package com.trioscope.chameleon.listener.impl;

import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.IntOrByteArray;
import com.trioscope.chameleon.types.CameraInfo;

/**
 * Created by phand on 6/11/15.
 */
public class UpdateRateListener implements CameraFrameAvailableListener {
    private UpdateRateCalculator calculator = new UpdateRateCalculator();

    @Override
    public void onFrameAvailable(CameraInfo cameraInfo, IntOrByteArray data) {
        calculator.updateReceived();
    }
}
