package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.util.Range;
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
import java.util.Collection;
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
    private static final int MAX_NUM_IMAGES = 3;
    private final Context context;
    private final CameraManager cameraManager;
    private CameraInfo cameraInfo;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private SimpleImageListener simpleImageListener;
    private CameraCaptureSession captureSession;
    private Surface previewSurface;
    private Size frameSize = ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE;
    private int curLensFacing = -1;


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
    }

    private void updateCameraInfo() {

        CameraInfo.CameraInfoBuilder builder = CameraInfo.builder();

        log.debug("Creating cameraInfo");
        // Supposed to be universally supported by Camera2
        CameraInfo.ImageEncoding encoding = CameraInfo.ImageEncoding.YUV_420_888;

        List<Size> supportedSizes = getSupportedSizes(encoding.getImageFormat());

        frameSize = ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE;

        if (!supportedSizes.contains(ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE)) {
            // Find supported size with desired aspect ratio
            for (Size suppportedSize : supportedSizes) {
                int factor = greatestCommonFactor(suppportedSize.getWidth(), suppportedSize.getHeight());
                int widthRatio = suppportedSize.getWidth() / factor;
                int heightRatio = suppportedSize.getHeight() / factor;
                if (widthRatio == ChameleonApplication.DEFAULT_ASPECT_WIDTH_RATIO
                        && heightRatio == ChameleonApplication.DEFAULT_ASPECT_HEIGHT_RATIO) {
                    frameSize = suppportedSize;
                    break;
                }
            }
        }
        builder.cameraResolution(frameSize);
        builder.captureResolution(frameSize);
        builder.encoding(encoding);
        cameraInfo = builder.build();

        log.info("Using cameraInfo {}", cameraInfo);

        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            curLensFacing = cc.get(CameraCharacteristics.LENS_FACING);
            log.debug("Camera is facing {}", curLensFacing);
        } catch (CameraAccessException e) {
            log.error("Unable to access camerainformation", e);
        }
    }

    public int greatestCommonFactor(int width, int height) {
        return (height == 0) ? width : greatestCommonFactor(height, width % height);
    }

    @Override
    public void startPreview() {
        log.debug("Starting camera preview");
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
                    log.debug("Unknown image format {}", format);
                }
            }
        } catch (CameraAccessException e) {
            log.error("Unable to retrieve supported encodings", e);
        }
        log.debug("Supported image encoding formats = {}", encodings);

        return encodings;
    }

    Collection<Integer> ALLOWABLE_CAMERA_LENS = Arrays.asList(CameraCharacteristics.LENS_FACING_BACK, CameraCharacteristics.LENS_FACING_FRONT);

    /*
     * Toggle between front and rear facing cameras. This method automatically stops and starts the camera preview.
     */
    public void toggleFrontFacingCamera() {
        try {
            log.debug("Iterating through cameraIds searching for suitable camera");
            String suitableCameraId = null;
            int suitableDirection = -1;
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                int facingDirection = characteristics.get(CameraCharacteristics.LENS_FACING);

                if (ALLOWABLE_CAMERA_LENS.contains(facingDirection) && facingDirection != curLensFacing) {
                    log.debug("Found suitable camera {} - facing {}", cameraId, facingDirection);
                    suitableCameraId = cameraId;
                    suitableDirection = facingDirection;
                }
            }

            if (suitableCameraId != null) {
                stopPreview();
                curLensFacing = suitableDirection;
                cameraDevice = null;
                cameraManager.openCamera(suitableCameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice cd) {
                        log.debug("Successfully changed cameraDevice");
                        synchronized (Camera2PreviewDisplayer.this) {
                            cameraDevice = cd;
                            Camera2PreviewDisplayer.this.notifyAll();
                        }
                    }

                    @Override
                    public void onDisconnected(CameraDevice cameraDevice) {
                        log.warn("CameraDevice disconnected (unexpectedly?) {}", cameraDevice);
                    }

                    @Override
                    public void onError(CameraDevice cameraDevice, int i) {
                        log.error("Error with cameraDevice, errorCode={}", i);
                    }
                }, new ThreadWithHandler().getHandler());

                // TODO: We should really be waiting on a different thread for the camera device to become ready
                // Wait for the camera device to become available before we start hte preview again
                synchronized (this) {
                    while (cameraDevice == null) {
                        try {
                            log.debug("Waiting for cameraDevice to become available.");
                            this.wait();
                        } catch (InterruptedException e) {
                            log.error("Waiting for camera to open was interrupted");
                        }
                    }
                }

                log.debug("Restarting cameraPreview");
                startPreview();
            }
        } catch (CameraAccessException e) {
            log.error("Unable to access camera information", e);
        }
    }

    @Override
    public boolean isUsingFrontFacingCamera() {
        return curLensFacing == CameraCharacteristics.LENS_FACING_FRONT;
    }

    private List<Size> getSupportedSizes(int format) {
        List<Size> sizes = new ArrayList<>();
        try {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            log.debug("Supported sizes: {}", map.getOutputSizes(format));
            log.debug("Supported sizes for MediaRecorder: {}", map.getOutputSizes(MediaRecorder.class));
            log.debug("Supported sizes for SurfaceHolder: {}", map.getOutputSizes(SurfaceHolder.class));

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
            updateCameraInfo();
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            log.debug("Timestamp source for camera2: {}", characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            log.debug("SensorOrientation: {}", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
            log.debug("Preview Surface: {}", previewSurface);
            prepareImageReader(cameraInfo.getCaptureResolution(), cameraInfo.getEncoding().getImageFormat());

            log.debug("Creating CaptureRequest.Builder using cameraDevice {} and imageReader {}", cameraDevice, imageReader);
            try {
                final CaptureRequest.Builder requestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                requestBuilder.addTarget(imageReader.getSurface());
                requestBuilder.addTarget(previewSurface);
                log.debug("Creating capture session");
                cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface(), previewSurface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        log.debug("CameraCaptureSession is configured");
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return;
                        }

                        // When the session is ready, we start displaying the preview.
                        captureSession = session;
                        try {
                            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_OFF);
                            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                    Range.create(20, 20));


                            // Finally, we start displaying the camera preview.
                            CaptureRequest previewRequest = requestBuilder.build();

                            requestSentAt = System.currentTimeMillis();
                            log.debug("Repeating request sent at {}", requestSentAt);

                            ThreadWithHandler handler = new ThreadWithHandler();
                            handler.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                                @Override
                                public void uncaughtException(Thread thread, Throwable throwable) {
                                    log.error("Uncaught exception in the handling of the capture request {}", thread, throwable);
                                }
                            });

                            captureSession.setRepeatingRequest(previewRequest,
                                    new CameraCaptureSession.CaptureCallback() {
                                        long captureStartTime = System.currentTimeMillis();

                                        @Override
                                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                                            captureStartTime = System.currentTimeMillis();
                                            log.debug("Capture successfully started");
                                        }


                                        @Override
                                        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                            super.onCaptureCompleted(session, request, result);
                                            if (firstRequestReceived == null) {
                                                for (CaptureResult.Key key : result.getKeys()) {
                                                    log.debug("Capture exposure key = {}, value = {}", key.getName(), result.get(key));
                                                }
                                                firstRequestReceived = System.currentTimeMillis();
                                                log.debug("Latency between calls is {} ms", firstRequestReceived - requestSentAt);
                                            }
                                            log.debug("Capture completed - {}, capture delay = {} ms, frame duration = {} ms",
                                                    result.get(CaptureResult.SENSOR_TIMESTAMP),
                                                    System.currentTimeMillis() - captureStartTime,
                                                    result.get(CaptureResult.SENSOR_FRAME_DURATION) / 1000000);
                                        }

                                        @Override
                                        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                            log.error("Error on capture request {}, failure is {}", request, failure);
                                        }

                                        @Override
                                        public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                                            log.warn("Capture aborted");
                                        }

                                        @Override
                                        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                            log.debug("Capture progressed");
                                        }

                                        @Override
                                        public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                                            log.debug("Capture sequence completed");
                                        }
                                    }, handler.getHandler());
                            log.debug("Call to capture completed");
                        } catch (Exception e) {
                            log.error("Exception caught", e);
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        log.debug("Configuration failed for session {}", session);
                    }
                }, null);
            } catch (Exception e) {
                log.error("Failed to create capture session", e);
            }

        } catch (CameraAccessException e) {
            log.error("Unable to access camera characteristics ", e);
        }
    }

    private void prepareImageReader(Size sz, int format) {
        int width = sz.getWidth();
        int height = sz.getHeight();
        try {
            imageReader = ImageReader.newInstance(width, height, format, MAX_NUM_IMAGES);
            simpleImageListener = new SimpleImageListener();
            imageReader.setOnImageAvailableListener(simpleImageListener, new ThreadWithHandler().getHandler());
            log.debug("Prepared image listener {}", simpleImageListener);
        } catch (Exception e) {
            log.error("Unable to create ImageReader with parameters", e);
        }
    }

    @Override
    public void stopPreview() {
        log.debug("Stopping camera2 preview");
        if (cameraDevice != null) {
            // Closing camera will abort capture session. So, no cleanup necessary.
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    @Override
    public void addOnPreparedCallback(Runnable runnable) {
        runnable.run();
    }

    @Override
    public SurfaceView createPreviewDisplay() {
        log.debug("Creating PreviewDisplay for open camera");
        SurfaceView surfaceView = new SurfaceView(context);
        surfaceView.getHolder().setFixedSize(frameSize.getWidth(), frameSize.getHeight());
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                log.debug("Surface holder has created the surface");
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

            // Front camera produces upside-down and mirror image of original frame
            frameInfo.setOrientationDegrees(isUsingFrontFacingCamera()? 270 : 90);
            frameInfo.setVerticallyFlipped(isUsingFrontFacingCamera());
            frameInfo.setHorizontallyFlipped(isUsingFrontFacingCamera());

            cameraFrameBuffer.frameAvailable(cameraInfo, frameData, frameInfo);

            image.close();
        }
    }
}
