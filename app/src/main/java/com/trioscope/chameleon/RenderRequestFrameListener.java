package com.trioscope.chameleon;

import android.opengl.GLSurfaceView;

import com.trioscope.chameleon.service.FrameListener;

import lombok.RequiredArgsConstructor;

/**
 * Created by phand on 5/30/15.
 */
@RequiredArgsConstructor
public class RenderRequestFrameListener implements FrameListener {
    private final GLSurfaceView renderable;

    @Override
    public void frameAvailable() {
        renderable.requestRender();
    }
}
