package com.trioscope.chameleon.service;

import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by phand on 5/28/15.
 */
public class CameraPreviewFrameListener implements SurfaceTexture.OnFrameAvailableListener {
    private static final Logger LOG = LoggerFactory.getLogger(CameraPreviewFrameListener.class);

    private List<GLSurfaceView> displaySurfaces = new ArrayList<>();
    private List<FrameListener> frameListeners = new ArrayList<>();

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        LOG.info("Camera frame is available, alerting {} surfaces and {} listeners", displaySurfaces.size(), frameListeners.size());

        for (GLSurfaceView surface : displaySurfaces) {
            surface.requestRender();
        }

        for (FrameListener listener : frameListeners)
            listener.frameAvailable();
    }

    public void addDisplaySurface(GLSurfaceView surface) {
        displaySurfaces.add(surface);
    }

    public void addFrameListener(FrameListener listener) {
        frameListeners.add(listener);
    }
}
