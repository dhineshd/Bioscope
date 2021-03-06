package com.trioscope.chameleon.listener;

import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.types.CameraInfo;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private volatile Set<CameraFrameAvailableListener> listeners =
            Collections.newSetFromMap(new ConcurrentHashMap<CameraFrameAvailableListener, Boolean>());

    public void frameAvailable(final CameraInfo cameraInfo, final CameraFrameData frameData, final FrameInfo frameInfo) {
        for (CameraFrameAvailableListener listener : listeners) {
            listener.onFrameAvailable(cameraInfo, frameData, frameInfo);
        }
    }

    public void addListener(CameraFrameAvailableListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CameraFrameAvailableListener listener) {
        listeners.remove(listener);
    }
}
