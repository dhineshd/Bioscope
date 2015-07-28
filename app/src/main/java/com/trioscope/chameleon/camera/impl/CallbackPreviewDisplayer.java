package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;

import java.io.IOException;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/27/15.
 */
@Slf4j
public class CallbackPreviewDisplayer implements PreviewDisplayer {
    private final Context context;

    @Setter
    private CameraFrameBuffer cameraFrameBuffer;
    private Camera camera;
    private SurfaceHolder displaySurfaceHolder;
    private boolean shouldCallStartWhenAvailable = false;

    public CallbackPreviewDisplayer(Context context, Camera c) {
        this.context = context;
        this.camera = c;
    }


    public CallbackPreviewDisplayer(Context context, Camera c, SurfaceHolder displaySurfaceHolder) {
        this.context = context;
        this.camera = c;
        this.displaySurfaceHolder = displaySurfaceHolder;
    }

    @Override
    public void startPreview() {
        log.info("Starting preview with camera {} and displaySurfaceHolder {}", camera, displaySurfaceHolder);
        try {
            synchronized (this) {
                if (displaySurfaceHolder != null) {
                    camera.setPreviewDisplay(displaySurfaceHolder);
                    camera.setDisplayOrientation(90);
                    camera.startPreview();
                } else {
                    log.warn("Display surface holder not yet available, going to start preview later");
                    shouldCallStartWhenAvailable = true;
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        log.info("Starting preview");
        camera.startPreview();
    }

    @Override
    public void addOnPreparedCallback(Runnable runnable) {
        log.info("Running prepared callback");
        // No preparation needed - run it immediately
        try {
            runnable.run();
        } catch (Exception e) {
            log.error("Error received while calling callback", e);
            throw e;
        }
    }

    @Override
    public SurfaceView createPreviewDisplay() {
        log.info("Request to create preview display");
        SurfaceView surfaceView = new SurfaceView(context);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                log.info("Surface has been created in thread {}", Thread.currentThread());
                synchronized (CallbackPreviewDisplayer.this) {
                    displaySurfaceHolder = holder;
                    if (shouldCallStartWhenAvailable) {
                        log.info("Requested to start the preview before the surfaceview was available, starting preview now");
                        startPreview();
                    }
                }
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
}
