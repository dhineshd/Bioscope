package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;

import com.trioscope.chameleon.SurfaceTextureDisplay;
import com.trioscope.chameleon.SystemOverlayGLSurface;
import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.RenderRequestFrameListener;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/25/15.
 */
@Slf4j
public class FBOPreviewDisplayer implements PreviewDisplayer {
    private final Camera camera;
    private Context context;

    private RotationState rotationState;

    private EGLContextAvailableMessage globalEglContextInfo;
    private GLSurfaceView.EGLContextFactory eglContextFactory;

    // For background image recording
    private WindowManager windowManager;
    private SystemOverlayGLSurface surfaceView;
    private EGLContextAvailableHandler eglContextAvailHandler = new EGLContextAvailableHandler();
    private CameraPreviewTextureListener cameraPreviewFrameListener = new CameraPreviewTextureListener();

    private final Object eglCallbackLock = new Object();
    private List<Runnable> preparedCallbacks = new ArrayList<>();

    public FBOPreviewDisplayer(Context context, Camera camera, CameraInfo cameraInfo, RotationState rotationState) {
        this.context = context;
        this.camera = camera;
        this.rotationState = rotationState;

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SystemOverlayGLSurface(context, eglContextAvailHandler);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        log.info("Created system overlay SurfaceView {}", surfaceView);

        surfaceView.setCameraInfo(cameraInfo);
    }

    @Override
    public void startPreview() {
        try {
            log.info("Starting camera preview {}, {}", cameraPreviewFrameListener, globalEglContextInfo);
            cameraPreviewFrameListener.addFrameListener(new RenderRequestFrameListener(surfaceView));
            globalEglContextInfo.getSurfaceTexture().setOnFrameAvailableListener(cameraPreviewFrameListener);
            log.info("Setting preview texture");
            camera.setPreviewTexture(globalEglContextInfo.getSurfaceTexture());

            camera.startPreview();
            log.info("Started camera preview");
        } catch (IOException e) {
            log.error("Error starting camera preview", e);
        } catch (Exception e) {
            log.error("Error starting camera preview", e);
        }
    }

    @Override
    public void stopPreview() {
        camera.stopPreview();
    }

    @Override
    public void setCameraFrameBuffer(CameraFrameBuffer cfb) {
        surfaceView.setCameraFrameBuffer(cfb);
    }

    @Override
    public void addOnPreparedCallback(Runnable runnable) {
        synchronized (eglCallbackLock) {
            if (globalEglContextInfo == null) {
                preparedCallbacks.add(runnable);
            } else {
                runnable.run();
            }
        }
    }

    public class EGLContextAvailableHandler extends Handler {
        public static final int EGL_CONTEXT_AVAILABLE = 1;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EGL_CONTEXT_AVAILABLE) {
                synchronized (eglCallbackLock) {
                    log.info("EGL context is created and available");
                    globalEglContextInfo = (EGLContextAvailableMessage) msg.obj;

                    log.info("Now calling eglCallback since EGLcontext is available");
                    for (Runnable runnable : preparedCallbacks)
                        runnable.run();
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }

    @Override
    public SurfaceTextureDisplay createPreviewDisplay() {
        final EGLContextAvailableMessage contextMessage = globalEglContextInfo;
        SurfaceTextureDisplay previewDisplay = new SurfaceTextureDisplay(context);
        previewDisplay.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
            @Override
            public javax.microedition.khronos.egl.EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                log.info("Creating shared EGLContext");
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };

                EGLContext newContext = ((EGL10) EGLContext.getEGL()).eglCreateContext(
                        display, eglConfig, contextMessage.getEglContext(), attrib2_list);
                log.info("Created a shared EGL context: {}", newContext);
                return newContext;
            }

            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display, javax.microedition.khronos.egl.EGLContext context) {
                log.info("EGLContext is being destroyed");
                egl.eglDestroyContext(display, context);
            }
        });

        previewDisplay.setTextureId(contextMessage.getGlTextureId());
        previewDisplay.setToDisplay(contextMessage.getSurfaceTexture());
        //previewDisplay.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        previewDisplay.setRenderer(previewDisplay.new SurfaceTextureRenderer(rotationState));
        cameraPreviewFrameListener.addFrameListener(new RenderRequestFrameListener(previewDisplay));

        return previewDisplay;
    }

    @Override
    public void toggleFrontFacingCamera() {
        throw new UnsupportedOperationException("This preview displayer does not support front facing camera" );
    }
}
