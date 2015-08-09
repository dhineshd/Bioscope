package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.types.CameraInfo;

/**
 * Created by phand on 6/11/15.
 */
public interface CameraFrameAvailableListener {
    void onFrameAvailable(CameraInfo cameraInfo, IntOrByteArray data, FrameInfo frameInfo);
}
