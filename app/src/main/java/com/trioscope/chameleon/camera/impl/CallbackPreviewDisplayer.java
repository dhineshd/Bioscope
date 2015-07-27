package com.trioscope.chameleon.camera.impl;

import android.hardware.Camera;
import android.view.SurfaceHolder;

import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;

import java.io.IOException;

import lombok.Setter;

/**
 * Created by phand on 7/27/15.
 */
public class CallbackPreviewDisplayer implements PreviewDisplayer {
    @Setter
    private CameraFrameBuffer cameraFrameBuffer;
    private Camera camera;
    private SurfaceHolder displaySurfaceHolder;

    public CallbackPreviewDisplayer(Camera c, SurfaceHolder displaySurfaceHolder) {
        this.camera = c;
        this.displaySurfaceHolder = displaySurfaceHolder;
    }

    @Override
    public void startPreview() {
        try {
            camera.setPreviewDisplay(displaySurfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addPreparedCallback(Runnable runnable) {
        // No preparation needed - run it immediately
        runnable.run();
    }
}
