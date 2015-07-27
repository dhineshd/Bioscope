package com.trioscope.chameleon.camera;

import com.trioscope.chameleon.listener.CameraFrameBuffer;

/**
 * Created by phand on 7/25/15.
 */
public interface PreviewDisplayer {
    void startPreview();

    void setCameraFrameBuffer(CameraFrameBuffer cfb);

    void addPreparedCallback(Runnable runnable);
}
