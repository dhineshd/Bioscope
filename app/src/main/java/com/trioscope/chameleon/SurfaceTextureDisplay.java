package com.trioscope.chameleon;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.trioscope.chameleon.opengl.DirectVideo;
import com.trioscope.chameleon.state.RotationState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import lombok.Data;
import lombok.Setter;

/**
 * Class used with interanal OpenGL Renderer to display a given surfaceTexture.
 * <p/>
 * GLSurfaceView already implements SurfaceHolder.Callback; declaring it explicitly.
 * <p/>
 * Created by phand on 5/18/15.
 */
public class SurfaceTextureDisplay extends GLSurfaceView implements SurfaceHolder.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(SurfaceTextureDisplay.class);

    private SurfaceTextureRenderer renderer;

    @Setter
    private SurfaceTexture toDisplay;

    @Setter
    private int textureId = -1;

    private DirectVideo directVideo;

    public SurfaceTextureDisplay(Context context) {
        super(context);
        init(context, null);
    }

    public SurfaceTextureDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, null);
    }

    public SurfaceTextureDisplay(Context context, Renderer renderer) {
        super(context);
        init(context, renderer);
    }

    private void init(Context context, Renderer renderer) {
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
        getHolder().addCallback(this);

        if (renderer != null)
            setRenderer(renderer);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        LOG.info("Surface Changed!");
        super.surfaceChanged(holder, format, w, h);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LOG.info("Surface Created!");
        super.surfaceCreated(holder);
    }


    @Data
    public class SurfaceTextureRenderer implements Renderer {
        private final RotationState rotationState;

        public void onDrawFrame(GL10 unused) {
            // note -- dont LOG here, this is frame loop
            // Redraw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            LOG.debug("Rendering frame");
            if (textureId != -1) {
                if (directVideo == null) {
                    LOG.info("Creating directVideo at start of drawFrame using texture {}", textureId);
                    directVideo = new DirectVideo(textureId);
                    directVideo.setRotationState(rotationState);
                }
                LOG.debug("Drawing direct video");
                directVideo.draw();
            }
        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            LOG.info("Surface is created");
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        }

        public void onSurfaceChanged(GL10 unused, int width, int height) {
            LOG.info("This should be on OPENGL thread {}", Thread.currentThread());
            GLES20.glViewport(0, 0, width, height);
        }
    }
}
