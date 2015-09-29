package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.types.CameraInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private volatile Set<CameraFrameAvailableListener> listeners = new HashSet<>();

    public void frameAvailable(CameraInfo cameraInfo, CameraFrameData frameData, FrameInfo frameInfo) {
        for (CameraFrameAvailableListener listener : listeners)
            listener.onFrameAvailable(cameraInfo, frameData, frameInfo);
    }

    public synchronized void addListener(CameraFrameAvailableListener listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(CameraFrameAvailableListener listener) {
        listeners.remove(listener);
    }
}
