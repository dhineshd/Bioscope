package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.types.CameraInfo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private ConcurrentMap<CameraFrameAvailableListener, Boolean> listeners = new ConcurrentHashMap<>();

    public void frameAvailable(CameraInfo cameraInfo, CameraFrameData frameData, FrameInfo frameInfo) {
        for (CameraFrameAvailableListener listener : listeners.keySet()) {
            listener.onFrameAvailable(cameraInfo, frameData, frameInfo);
        }
    }

    public void addListener(CameraFrameAvailableListener listener) {
        listeners.putIfAbsent(listener, false);
    }

    public void removeListener(CameraFrameAvailableListener listener) {
        listeners.remove(listener);
    }
}
