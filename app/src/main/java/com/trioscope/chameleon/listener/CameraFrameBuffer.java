package com.trioscope.chameleon.listener;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phand on 6/11/15.
 */
public class CameraFrameBuffer {
    private List<CameraFrameAvailableListener> listeners = new ArrayList<>();

    public void frameAvailable(int[] data) {
        for(CameraFrameAvailableListener listener : listeners)
            listener.onFrameAvailable(data);
    }

    public void addListener(CameraFrameAvailableListener listener) {
        listeners.add(listener);
    }
}
