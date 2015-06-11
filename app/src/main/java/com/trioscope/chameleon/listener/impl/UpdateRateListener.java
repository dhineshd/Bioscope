package com.trioscope.chameleon.listener.impl;

import com.trioscope.chameleon.listener.CameraFrameAvailableListener;

/**
 * Created by phand on 6/11/15.
 */
public class UpdateRateListener implements CameraFrameAvailableListener {
    private UpdateRateCalculator calculator = new UpdateRateCalculator();

    @Override
    public void onFrameAvailable(int[] data) {
        calculator.updateReceived();
    }
}
