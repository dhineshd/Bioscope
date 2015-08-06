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
import com.trioscope.chameleon.types.Size;

import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 8/3/15.
 */
@RequiredArgsConstructor
@Slf4j
public class Camera2PreviewDisplayer implements PreviewDisplayer {
    private static final int MAX_NUM_IMAGES = 5;
    private final Context context;
    private final CameraDevice cameraDevice;
    private final CameraManager cameraManager;
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

    @Override
    public void startPreview() {
        if (previewSurface != null)
            startPreviewHelper();
        else
            shouldStartPreviewHelper = true;
    }

    private void startPreviewHelper() {
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());

            log.info("Characteristics info keys {}", characteristics.getKeys());
            for (CameraCharacteristics.Key key : characteristics.getKeys()) {
                log.info(key.getName());
            }

            log.info("Timestamp source: {}", characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            log.info("SensorOrientation: {}", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
            log.info("Output formats: {}", map.getOutputFormats());
            log.info("Formats JPEG: {}", ImageFormat.JPEG);
            log.info("Formats RAW_SENSOR: {}", ImageFormat.RAW_SENSOR);
            log.info("Formats NV21: {}", ImageFormat.NV21);
            log.info("Formats YUV_420_888: {}", ImageFormat.YUV_420_888);
            log.info("Supported sizes: {}", map.getOutputSizes(ImageFormat.YUV_420_888));
            log.info("Supported sizes for MediaRecorder: {}", map.getOutputSizes(MediaRecorder.class));
            log.info("Supported sizes for SurfaceHolder: {}", map.getOutputSizes(SurfaceHolder.class));
            log.info("Preview Surface: {}", previewSurface);
            prepareImageReader(new Size(map.getOutputSizes(ImageFormat.YUV_420_888)[0]), ImageFormat.YUV_420_888);

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
        imageReader = ImageReader.newInstance(width, height, format, MAX_NUM_IMAGES);
        simpleImageListener = new SimpleImageListener();
        imageReader.setOnImageAvailableListener(simpleImageListener, null);
        log.info("Prepared image listener {}", simpleImageListener);
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
        @Override
        public void onImageAvailable(ImageReader reader) {

            log.debug("Image is available. {}", System.currentTimeMillis());
            Image image = reader.acquireLatestImage();
            if (image != null)
                log.debug("Timestamp was {} - {}", image.getTimestamp(), System.currentTimeMillis());
            image.close();
        }
    }
}
