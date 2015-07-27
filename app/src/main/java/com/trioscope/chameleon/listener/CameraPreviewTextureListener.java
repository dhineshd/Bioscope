package com.trioscope.chameleon.listener;

import android.graphics.SurfaceTexture;

import com.trioscope.chameleon.service.FrameListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phand on 5/28/15.
 */
public class CameraPreviewTextureListener implements SurfaceTexture.OnFrameAvailableListener {
    private static final Logger LOG = LoggerFactory.getLogger(CameraPreviewTextureListener.class);

    private List<FrameListener> frameListeners = new ArrayList<>();

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        LOG.debug("Camera frame is available, alerting {} listeners, thread {}", frameListeners.size(), Thread.currentThread());

        for (FrameListener listener : frameListeners)
            listener.frameAvailable();
    }


    public void addFrameListener(FrameListener listener) {
        frameListeners.add(listener);
    }
}
