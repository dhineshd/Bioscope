package com.trioscope.chameleon.camera.impl;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.IntOrByteArray;
import com.trioscope.chameleon.types.CameraInfo;

import java.io.IOException;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/27/15.
 */
@Slf4j
public class SurfaceViewPreviewDisplayer implements PreviewDisplayer, Camera.PreviewCallback {
    private final Context context;

    @Setter
    private CameraFrameBuffer cameraFrameBuffer;
    private Camera camera;
    private CameraInfo cameraInfo;
    private SurfaceHolder displaySurfaceHolder;
    private boolean shouldCallStartWhenAvailable = false;

    public SurfaceViewPreviewDisplayer(Context context, Camera c, CameraInfo cameraInfo) {
        this.context = context;
        this.camera = c;
        this.cameraInfo = cameraInfo;
    }

    @Override
    public void startPreview() {
        log.info("Starting preview with camera {} and displaySurfaceHolder {}", camera, displaySurfaceHolder);
        try {
            synchronized (this) {
                if (displaySurfaceHolder != null) {
                    camera.setPreviewDisplay(displaySurfaceHolder);
                    camera.setPreviewCallbackWithBuffer(this);
                    int bufferSize = getBufferSize(camera);
                    byte[] buffer = new byte[bufferSize];
                    camera.addCallbackBuffer(buffer);
                    camera.setDisplayOrientation(90);
                    log.info("Starting preview");
                    camera.startPreview();
                    log.info("Preview started");
                } else {
                    log.warn("Display surface holder not yet available, going to start preview later");
                    shouldCallStartWhenAvailable = true;
                }
            }
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (Exception e) {
            log.info("Failed to start preview", e);
            throw e;
        }
    }

    public void stopPreview() {
        synchronized (this) {
            shouldCallStartWhenAvailable = false;
            displaySurfaceHolder = null;
            camera.stopPreview();
        }
    }

    private int getBufferSize(Camera camera) {
        int bufferSize = camera.getParameters().getPreviewSize().height * camera.getParameters().getPreviewSize().width;
        double bitsPerPixel = ImageFormat.getBitsPerPixel(camera.getParameters().getPreviewFormat());
        bufferSize *= bitsPerPixel / 8;

        log.info("Buffersize determined to be {} ({} bits per pixel)", bufferSize, bitsPerPixel);
        return bufferSize;
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
                synchronized (SurfaceViewPreviewDisplayer.this) {
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

        log.info("Creates surfaceView {}", surfaceView);
        return surfaceView;
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        cameraFrameBuffer.frameAvailable(cameraInfo, new IntOrByteArray(data));
        camera.addCallbackBuffer(data);
    }
}
