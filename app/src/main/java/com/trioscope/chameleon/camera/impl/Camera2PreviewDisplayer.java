package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.IntOrByteArray;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 8/3/15.
 */
@Slf4j
public class Camera2PreviewDisplayer implements PreviewDisplayer {
    private static final int MAX_NUM_IMAGES = 5;
    private final Context context;
    private final CameraDevice cameraDevice;
    private final CameraManager cameraManager;
    private final CameraInfo cameraInfo;
    private ImageReader imageReader;
    private SimpleImageListener simpleImageListener;
    private CameraCaptureSession captureSessions;
    private Surface previewSurface;


    @Setter
    private CameraFrameBuffer cameraFrameBuffer;

    //TODO : Remove
    private boolean shouldStartPreviewHelper = false;
    private long requestSentAt;
    private Long firstRequestReceived = null;

    public Camera2PreviewDisplayer(Context context, CameraDevice cameraDevice, CameraManager cameraManager) {
        this.context = context;
        this.cameraDevice = cameraDevice;
        this.cameraManager = cameraManager;

        Set<CameraInfo.ImageEncoding> supportedEncodings = getSupportedEncodings();
        CameraInfo.CameraInfoBuilder builder = CameraInfo.builder();

        log.info("Creating cameraInfo");
        CameraInfo.ImageEncoding encoding;
        /* ImageReader doesnt support NV21 currently.
          if (supportedEncodings.contains(CameraInfo.ImageEncoding.NV21))
            encoding = CameraInfo.ImageEncoding.NV21;
            */
        encoding = CameraInfo.ImageEncoding.YUV_420_888; // Supposed to be universally supported by Camera2

        builder.captureResolution(getSupportedSizes(encoding.getImageFormat()).get(0));
        builder.cameraResolution(getSupportedSizes(encoding.getImageFormat()).get(0));
        builder.encoding(encoding);
        cameraInfo = builder.build();

        log.info("Using cameraInfo {}", cameraInfo);
    }

    @Override
    public void startPreview() {
        if (previewSurface != null)
            startPreviewHelper();
        else
            shouldStartPreviewHelper = true;
    }

    private Set<CameraInfo.ImageEncoding> getSupportedEncodings() {
        Set<CameraInfo.ImageEncoding> encodings = new HashSet<>();

        CameraCharacteristics characteristics = null;
        try {
            characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            for (int format : map.getOutputFormats()) {
                if (format == ImageFormat.YUV_420_888)
                    encodings.add(CameraInfo.ImageEncoding.YUV_420_888);
                else if (format == ImageFormat.NV21)
                    encodings.add(CameraInfo.ImageEncoding.NV21);
                else
                    log.info("Unknown image format {}", format);
            }
        } catch (CameraAccessException e) {
            log.error("Unable to retrieve supported encodings", e);
        }

        return encodings;
    }

    private List<Size> getSupportedSizes(int format) {
        List<Size> sizes = new ArrayList<>();
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            log.info("Supported sizes: {}", map.getOutputSizes(format));
            log.info("Supported sizes for MediaRecorder: {}", map.getOutputSizes(MediaRecorder.class));
            log.info("Supported sizes for SurfaceHolder: {}", map.getOutputSizes(SurfaceHolder.class));

            for (android.util.Size sz : map.getOutputSizes(format)) {
                sizes.add(new Size(sz));
            }
        } catch (CameraAccessException e) {
            log.error("Unable to get camera sizes", e);
        }

        log.info("Getting sizes for format {} = {}", format, sizes);
        return sizes;
    }

    private void startPreviewHelper() {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            log.info("Timestamp source: {}", characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            log.info("SensorOrientation: {}", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
            log.info("Preview Surface: {}", previewSurface);
            prepareImageReader(cameraInfo.getCaptureResolution(), cameraInfo.getEncoding().getImageFormat());

            log.info("Creating CaptureRequest.Builder using cameraDevice {} and imageReader {}", cameraDevice, imageReader);
            try {
                final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                requestBuilder.addTarget(imageReader.getSurface());
                requestBuilder.addTarget(previewSurface);
                log.info("Creating capture session");
                cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface(), previewSurface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        log.info("CameraCaptureSession is configured");
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return;
                        }

                        // When the session is ready, we start displaying the preview.
                        captureSessions = session;
                        try {
                            // Auto focus should be continuous for camera preview.
                            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            // Flash is automatically enabled when necessary.
                            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                            // Finally, we start displaying the camera preview.
                            CaptureRequest previewRequest = requestBuilder.build();

                            requestSentAt = System.currentTimeMillis();
                            log.info("Repeating request sent at {}", requestSentAt);
                            captureSessions.setRepeatingRequest(previewRequest,
                                    new CameraCaptureSession.CaptureCallback() {
                                        @Override
                                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                            super.onCaptureCompleted(session, request, result);
                                            if (firstRequestReceived == null) {
                                                firstRequestReceived = System.currentTimeMillis();
                                                log.info("Latency between calls is {}", firstRequestReceived - requestSentAt);
                                            }
                                            log.debug("Capture completed - {}", result.get(CaptureResult.SENSOR_TIMESTAMP));
                                        }

                                    }, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {

                    }
                }, null);
            } catch (Exception e) {
                log.error("Error ", e);
                throw e;
            }

        } catch (CameraAccessException e) {
            log.error("Unable to access camera characterstics ", e);
        }
    }

    private void prepareImageReader(Size sz, int format) {
        int width = sz.getWidth();
        int height = sz.getHeight();
        try {
            imageReader = ImageReader.newInstance(width, height, format, MAX_NUM_IMAGES);
            simpleImageListener = new SimpleImageListener();
            imageReader.setOnImageAvailableListener(simpleImageListener, null);
            log.info("Prepared image listener {}", simpleImageListener);
        } catch (Exception e) {
            log.error("Unable to create ImageReader with parameters", e);
        }
    }

    @Override
    public void stopPreview() {
    }

    @Override
    public void addOnPreparedCallback(Runnable runnable) {
        runnable.run();
    }

    @Override
    public SurfaceView createPreviewDisplay() {
        SurfaceView surfaceView = new SurfaceView(context);
        surfaceView.getHolder().setFixedSize(1920, 1080);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                log.info("Surface holder has created the surface");
                Camera2PreviewDisplayer.this.previewSurface = holder.getSurface();

                if (shouldStartPreviewHelper)
                    startPreviewHelper();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        return surfaceView;
    }

    private class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        byte[] buffer;
        IntOrByteArray intOrByteArray;

        @Override
        public void onImageAvailable(ImageReader reader) {
            //TODO : Are we dropping images by not using acquireNextImage?
            Image image = reader.acquireLatestImage();
            /*if (buffer == null) {
                buffer = ImageUtil.getDataFromImage(image);
                intOrByteArray = new IntOrByteArray(buffer);
            } else {
                // Reuse buffer
                ImageUtil.getDataFromImage(image, buffer);
            }
            cameraFrameBuffer.frameAvailable(cameraInfo, intOrByteArray);
*/
            image.close();
        }
    }
}
