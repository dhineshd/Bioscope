package com.trioscope.chameleon.opengl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by phand on 6/19/15.
 */
public class BlueScreenRenderer implements GLSurfaceView.Renderer {
    private static final Logger LOG = LoggerFactory.getLogger(BlueScreenRenderer.class);

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        LOG.info("SurfaceCreated");
        GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        LOG.info("SurfaceChanged");
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }
}
