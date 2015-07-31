package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.types.CameraInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private List<CameraFrameAvailableListener> listeners = new ArrayList<>();

    public void frameAvailable(CameraInfo cameraInfo, IntOrByteArray frameData) {
        for (CameraFrameAvailableListener listener : listeners)
            listener.onFrameAvailable(cameraInfo, frameData);
    }



    public void addListener(CameraFrameAvailableListener listener) {
        listeners.add(listener);
    }
}
