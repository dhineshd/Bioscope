package com.trioscope.chameleon.camera;

import android.view.SurfaceView;

import com.trioscope.chameleon.listener.CameraFrameBuffer;

/**
 * Created by phand on 7/25/15.
 */
public interface PreviewDisplayer {
    void startPreview();

    void stopPreview();

    void setCameraFrameBuffer(CameraFrameBuffer cfb);

    void addOnPreparedCallback(Runnable runnable);

    SurfaceView createPreviewDisplay();

    void toggleFrontFacingCamera();

    boolean isUsingFrontFacingCamera();
}
