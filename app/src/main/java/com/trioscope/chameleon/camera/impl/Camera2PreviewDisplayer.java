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
import com.trioscope.chameleon.camera.CameraParams;
import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.listener.impl.UpdateRateCalculator;
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
    private static final int MAX_NUM_IMAGES = 2;
    private final Context context;
    private final CameraManager cameraManager;
    private CameraInfo cameraInfo;
    private CameraDevice cameraDevice;
    private ImageReader imageReader;
    private SimpleImageListener simpleImageListener;
    private CameraCaptureSession captureSession;
    private Surface previewSurface;
    private Size frameSize;
    private int curLensFacing = -1;
    private int currentOrientationDegrees;
    @Setter
    private CameraParams cameraParams;


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
        updateCameraInfo();
    }

    private void updateCameraInfo() {
        try {
            CameraCharacteristics cc = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            CameraInfo.CameraInfoBuilder builder = CameraInfo.builder();

            log.debug("Creating cameraInfo");
            // Supposed to be universally supported by Camera2
            CameraInfo.ImageEncoding encoding = CameraInfo.ImageEncoding.YUV_420_888;
            //CameraInfo.ImageEncoding encoding = CameraInfo.ImageEncoding.YV12;

            Set<CameraInfo.ImageEncoding> supportedEncodings = getSupportedEncodings();

            List<Size> supportedSizes = getSupportedSizes(encoding.getImageFormat());

            frameSize = ChameleonApplication.getDeviceSpecificCameraFrameSize(cc);

            if (!supportedSizes.contains(frameSize)) {
                // Find supported size with desired aspect ratio
                for (Size suppportedSize : supportedSizes) {
                    int factor = greatestCommonFactor(suppportedSize.getWidth(), suppportedSize.getHeight());
                    int widthRatio = suppportedSize.getWidth() / factor;
                    int heightRatio = suppportedSize.getHeight() / factor;
                    if (widthRatio == ChameleonApplication.DEFAULT_ASPECT_RATIO.getWidth()
                            && heightRatio == ChameleonApplication.DEFAULT_ASPECT_RATIO.getHeight()) {
                        frameSize = suppportedSize;
                        break;
                    }
                }

                log.info("Default frameSize is unsupported, using {} instead", frameSize);
            }
            builder.cameraResolution(frameSize);
            builder.captureResolution(frameSize);
            builder.encoding(encoding);
            cameraInfo = builder.build();

            log.info("Using cameraInfo {}", cameraInfo);
            curLensFacing = cc.get(CameraCharacteristics.LENS_FACING);
            currentOrientationDegrees = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
            log.debug("Camera is facing {}", curLensFacing);
            log.info("Camera orientation degrees = {}", cc.get(CameraCharacteristics.SENSOR_ORIENTATION));
            log.info("Camera auto exposure available modes = {}", cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES));
            log.info("Camera auto white-balance available modes = {}", cc.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES));
            log.info("CONTROL_AE_COMPENSATION_RANGE = {}", cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE));
            log.info("CONTROL_AE_COMPENSATION_STEP = {}", cc.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP));
        } catch (CameraAccessException e) {
            log.error("Unable to access camerainformation", e);
        }
    }

    public int greatestCommonFactor(int width, int height) {
        return (height == 0) ? width : greatestCommonFactor(height, width % height);
    }

    @Override
    public void startPreview(final CameraParams cameraParams) {
        this.cameraParams = cameraParams;
        log.debug("Starting camera preview");
        if (previewSurface != null)
            startPreviewHelper(cameraParams);
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
        log.info("Supported image encoding formats = {}", encodings);

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
                startPreview(cameraParams);
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

    private void startPreviewHelper(final CameraParams cameraParams) {
        try {
            updateCameraInfo();
            final CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            log.debug("Timestamp source for camera2: {}", characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            log.debug("SensorOrientation: {}", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));

            for (Range<Integer> fpsRange : characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)) {
                log.info("Available target FPS range: {}", fpsRange.toString());
            }
            log.info("Min frame duration = {}", map.getOutputMinFrameDuration(ImageFormat.YUV_420_888,
                    new android.util.Size(frameSize.getWidth(), frameSize.getHeight())));
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
                            int autoFocusMode = CaptureRequest.CONTROL_AF_MODE_AUTO;
                            if (cameraParams != null) {
                                autoFocusMode = cameraParams.getAutoFocusMode();
                                log.info("Auto focus mode = {}", autoFocusMode);
                            }
                            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, autoFocusMode);

                            requestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                                    CaptureRequest.CONTROL_AWB_MODE_AUTO);

                            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON);

                            // Limit frame rate range to guarantee reliable app performance
                            // Note : (30, 30) is guaranteed to be supported on all devices
                            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                    Range.create(30, 30));

                            // Enable video stabilization
                            requestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);

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
                                            Long sensorTimestampNs = result.get(CaptureResult.SENSOR_TIMESTAMP);
                                            Long sensorFrameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION);
                                            log.debug("Capture completed time {} ns, capture delay = {} ms, frame duration = {} ns",
                                                    sensorTimestampNs, System.currentTimeMillis() - captureStartTime,
                                                    sensorFrameDurationNs);
                                        }

                                        @Override
                                        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                            log.error("Error on capture request {}, failure is {}", request, failure.getReason());
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
            log.info("Prepared image listener {}", simpleImageListener);
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
                    startPreviewHelper(cameraParams);
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

    UpdateRateCalculator rateCalc = new UpdateRateCalculator();

    private class SimpleImageListener implements ImageReader.OnImageAvailableListener {
        byte[] buffer;
        CameraFrameData frameData;
        FrameInfo frameInfo;
        byte[] tempBuffer;
        private boolean shouldActOnFrames = true; // A debugging helper

        @Override
        public void onImageAvailable(final ImageReader reader) {
            //TODO : Are we dropping images by not using acquireNextImage?
            Image image = reader.acquireLatestImage();

            if (!shouldActOnFrames) {
                rateCalc.updateReceived();
                image.close();
                return;
            }

            if (image == null) {
                log.debug("Null image from acquire latest image -- skipping");
                return;
            }

            if (buffer == null) {
                tempBuffer = new byte[image.getHeight() * image.getWidth() / 2];
                buffer = ImageUtil.getDataFromImage(image, tempBuffer);
                frameData = new CameraFrameData(buffer);
                frameInfo = new FrameInfo();
            } else {
                // Reuse buffer
                ImageUtil.getDataFromImage(image, buffer, tempBuffer);
            }

            frameInfo.setTimestampNanos(image.getTimestamp());

            frameInfo.setOrientationDegrees(currentOrientationDegrees);

            // Front camera produces mirror image of original frame
            frameInfo.setHorizontallyFlipped(isUsingFrontFacingCamera());

            image.close();

            cameraFrameBuffer.frameAvailable(cameraInfo, frameData, frameInfo);
        }

    }
}
