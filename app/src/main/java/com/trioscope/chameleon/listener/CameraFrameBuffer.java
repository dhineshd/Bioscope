package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.types.CameraInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private volatile List<CameraFrameAvailableListener> listeners = new ArrayList<>();

    public void frameAvailable(CameraInfo cameraInfo, CameraFrameData frameData, FrameInfo frameInfo) {
        for (CameraFrameAvailableListener listener : listeners)
            listener.onFrameAvailable(cameraInfo, frameData, frameInfo);
    }

    public void addListener(CameraFrameAvailableListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CameraFrameAvailableListener listener) {
        listeners.remove(listener);
    }
}
