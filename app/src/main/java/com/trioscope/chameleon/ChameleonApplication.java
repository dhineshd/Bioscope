package com.trioscope.chameleon;

import android.app.Application;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;

import com.trioscope.chameleon.activity.MainActivity;
import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.factory.CameraInfoFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 6/4/15.
 */
public class ChameleonApplication extends Application {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);

    private VideoRecorder videoRecorder;

    @Getter
    private RotationState rotationState = new RotationState();

    @Getter
    @Setter
    private EGLContextAvailableMessage globalEglContextInfo;
    private MainActivity eglCallback;
    private final Object eglCallbackLock = new Object();

    // For background image recording
    private WindowManager windowManager;
    private SystemOverlayGLSurface surfaceView;
    private Camera camera;
    @Getter
    private CameraInfo cameraInfo;
    @Getter
    private CameraPreviewTextureListener cameraPreviewFrameListener = new CameraPreviewTextureListener();
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;
    private EGLContextAvailableHandler eglContextAvailHandler;


    @Override
    public void onCreate() {
        super.onCreate();
        LOG.info("Starting application");

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        eglContextAvailHandler = new EGLContextAvailableHandler();
        surfaceView = new SystemOverlayGLSurface(this, eglContextAvailHandler);
        surfaceView.setCameraFrameBuffer(cameraFrameBuffer);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        LOG.info("Created system overlay SurfaceView {}", surfaceView);

        // Add FPS listener to CameraBuffer
        cameraFrameBuffer.addListener(new UpdateRateListener());
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        LOG.info("Terminating application");
    }

    public void setEglContextCallback(MainActivity mainActivity) {
        synchronized (eglCallbackLock) {
            LOG.info("Adding EGLContextCallback for when EGLContext is available");
            if (globalEglContextInfo != null) {
                LOG.info("EGLContext immediately available, calling now");
                mainActivity.eglContextAvailable(globalEglContextInfo);
                startPreview();
            } else {
                LOG.info("EGLContext not immediately available, going to call later");
                eglCallback = mainActivity;
            }
        }
    }

    private void startPreview() {
        if (!previewStarted) {
            LOG.info("Grabbing camera and starting preview");
            camera = Camera.open();

            Camera.Parameters params = camera.getParameters();

            cameraInfo = CameraInfoFactory.createCameraInfo(params);
            LOG.info("CameraInfo for opened camera is {}", cameraInfo);

            try {
                cameraPreviewFrameListener.addFrameListener(new RenderRequestFrameListener(surfaceView));
                globalEglContextInfo.getSurfaceTexture().setOnFrameAvailableListener(cameraPreviewFrameListener);
                camera.setPreviewTexture(globalEglContextInfo.getSurfaceTexture());
                camera.startPreview();
            } catch (IOException e) {
                LOG.error("Error starting camera preview", e);
            }
            previewStarted = true;
        } else {
            LOG.info("Preview already started");
        }
    }

    public synchronized void updateOrientation() {
        LOG.info("Updating current device orientation");

        int orientation = getResources().getConfiguration().orientation;

        boolean isLandscape = getResources().getConfiguration().ORIENTATION_LANDSCAPE == orientation;
        LOG.info("Device is in {} mode", isLandscape ? "landscape" : "portrait");
        rotationState.setLandscape(isLandscape);
    }

    public class EGLContextAvailableHandler extends Handler {
        public static final int EGL_CONTEXT_AVAILABLE = 1;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EGL_CONTEXT_AVAILABLE) {
                synchronized (eglCallbackLock) {
                    LOG.info("EGL context is created and available");
                    globalEglContextInfo = (EGLContextAvailableMessage) msg.obj;
                    startPreview();

                    if (eglCallback != null) {
                        LOG.info("Now calling eglCallback since EGLcontext is available");
                        eglCallback.eglContextAvailable(globalEglContextInfo);
                    }
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }
}
