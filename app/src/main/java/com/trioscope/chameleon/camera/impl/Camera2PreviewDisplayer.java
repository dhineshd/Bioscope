package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;
import com.trioscope.chameleon.types.ThreadWithHandler;
import com.trioscope.chameleon.util.ImageUtil;

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
    private static final int MAX_NUM_IMAGES = 2;
    private final Context context;
    private final CameraDevice cameraDevice;
    private final CameraManager cameraManager;
    private final CameraInfo cameraInfo;
    private ImageReader imageReader;
    private SimpleImageListener simpleImageListener;
    private CameraCaptureSession captureSession;
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
        //builder.captureResolution(getSupportedSizes(encoding.getImageFormat()).get(0));
        //builder.cameraResolution(getSupportedSizes(encoding.getImageFormat()).get(0));

        List<Size> supportedSizes = getSupportedSizes(encoding.getImageFormat());
        builder.cameraResolution(ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE);
        builder.captureResolution(ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE);
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
                if (format == ImageFormat.YUV_420_888) {
                    encodings.add(CameraInfo.ImageEncoding.YUV_420_888);
                } else if (format == ImageFormat.NV21) {
                    encodings.add(CameraInfo.ImageEncoding.NV21);
                } else if (format == ImageFormat.YV12) {
                    encodings.add(CameraInfo.ImageEncoding.YV12);
                } else {
                    log.info("Unknown image format {}", format);
                }
            }
        } catch (CameraAccessException e) {
            log.error("Unable to retrieve supported encodings", e);
        }
        log.info("Supported image encoding formats = {}", encodings);

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
                final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
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
                        captureSession = session;
                        try {
                            // Auto focus should be continuous for camera preview.
                            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

                            // Finally, we start displaying the camera preview.
                            CaptureRequest previewRequest = requestBuilder.build();

                            requestSentAt = System.currentTimeMillis();
                            log.info("Repeating request sent at {}", requestSentAt);
                            captureSession.setRepeatingRequest(previewRequest,
                                    new CameraCaptureSession.CaptureCallback() {
                                        long captureStartTime = System.currentTimeMillis();

                                        @Override
                                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                                            captureStartTime = System.currentTimeMillis();
                                        }

                                    }, null);
                        } catch (CameraAccessException e) {
                            log.warn("Failure during capture session", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {

                    }
                }, null);
            } catch (Exception e) {
                log.error("Failed to create capture session", e);
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
            imageReader.setOnImageAvailableListener(simpleImageListener, new ThreadWithHandler().getHandler());
            log.info("Prepared image listener {}", simpleImageListener);
        } catch (Exception e) {
            log.error("Unable to create ImageReader with parameters", e);
        }
    }

    @Override
    public void stopPreview() {
        log.info("Stopping camera2 preview");
        if (cameraDevice != null) {
            // Closing camera will abort capture session
            cameraDevice.close();
        }
    }

    @Override
    public void addOnPreparedCallback(Runnable runnable) {
        runnable.run();
    }

    @Override
    public SurfaceView createPreviewDisplay() {
        SurfaceView surfaceView = new SurfaceView(context);
        surfaceView.getHolder().setFixedSize(
                ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE.getWidth(),
                ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE.getHeight());
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
                Camera2PreviewDisplayer.this.previewSurface = null;
            }
        });

        return surfaceView;
    }

    private class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        byte[] buffer;
        CameraFrameData frameData;
        FrameInfo frameInfo;

        @Override
        public void onImageAvailable(ImageReader reader) {
            //TODO : Are we dropping images by not using acquireNextImage?
            Image image = reader.acquireLatestImage();
            if (image == null) {
                log.warn("Null image from acquire latest image -- skipping");
                return;
            }

            //buffer = ImageUtil.getDataFromImage(image);
            //frameData = new CameraFrameData(image);
            //frameInfo = new FrameInfo();

            if (buffer == null) {
                buffer = ImageUtil.getDataFromImage(image);
                frameData = new CameraFrameData(buffer);
                frameInfo = new FrameInfo();
            } else {
                // Reuse buffer
                ImageUtil.getDataFromImage(image, buffer);
            }
            frameInfo.setTimestampNanos(image.getTimestamp());
            cameraFrameBuffer.frameAvailable(cameraInfo, frameData, frameInfo);

            image.close();
        }
    }
}
