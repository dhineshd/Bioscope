package com.trioscope.chameleon.service;

import android.hardware.Camera;

/**
 * Created by phand on 5/30/15.
 */
public interface FrameListener {
    void frameAvailable();
    void onFrameReceived(byte[] data, Camera.Parameters cameraParams);
}
