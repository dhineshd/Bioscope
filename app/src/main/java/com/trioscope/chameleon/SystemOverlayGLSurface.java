package com.trioscope.chameleon;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import lombok.Getter;

/**
 * Created by phand on 5/18/15.
 */
public class SystemOverlayGLSurface extends GLSurfaceView {
    private static final Logger LOG = LoggerFactory.getLogger(SystemOverlayGLSurface.class);

    private SurfaceTextureRenderer renderer;
    private SurfaceTexture surfaceTexture;

    @Getter
    private EGLContext eglContext;

    @Getter
    private int textureId;

    public SystemOverlayGLSurface(Context context) {
        super(context);
        init(context);
    }

    public SystemOverlayGLSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        renderer = new SurfaceTextureRenderer();

        // Set the Renderer for drawing on the GLSurfaceView, and only update when we have a frame available from the camera
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public SurfaceTexture getSurfaceTexture() {
        return surfaceTexture;
    }

    public class SurfaceTextureRenderer implements GLSurfaceView.Renderer {
        public void onDrawFrame(GL10 unused) {
            // note -- dont log in here, this is frame loop
            // Redraw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            surfaceTexture.updateTexImage();
            LOG.trace("Drawing surfaceView frame");
        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // Set the background frame color
            LOG.info("Surface created -- openGL thread is {}", Thread.currentThread());
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

            createSurfaceTexture();
        }

        private void createSurfaceTexture() {
            // Not sure if this is the best way to communicate the surfaceTexture across threads, but it works for now.
            int[] textureIds = new int[1];
            // generate one texture pointer and bind it as an external texture.
            GLES20.glGenTextures(1, textureIds, 0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIds[0]);
            // No mip-mapping with camera source.
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
            // Clamp to edge is only option.
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

            textureId = textureIds[0];
            surfaceTexture = new SurfaceTexture(textureId);
            LOG.info("Created surface texture successfully, surface texture id is {} (0 not valid)", textureId);
            LOG.info("Setting the surface texture of the context");

            eglContext = ((EGL10) EGLContext.getEGL()).eglGetCurrentContext();
            LOG.info("EGLContext is available for use {}", eglContext);
        }

        public void onSurfaceChanged(GL10 unused, int width, int height) {
            LOG.info("This should be on OPENGL thread {}", Thread.currentThread());
            GLES20.glViewport(0, 0, width, height);
        }
    }
}
