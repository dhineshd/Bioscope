package com.trioscope.chameleon;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.trioscope.chameleon.opengl.DirectVideo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import lombok.Setter;

/**
 * Created by phand on 5/18/15.
 */
public class SurfaceTextureDisplay extends GLSurfaceView {
    private static final Logger LOG = LoggerFactory.getLogger(SurfaceTextureDisplay.class);

    private SurfaceTextureRenderer renderer;

    @Setter
    private SurfaceTexture toDisplay;

    @Setter
    private int textureId = -1;

    private DirectVideo directVideo;

    public SurfaceTextureDisplay(Context context) {
        super(context);
        init(context);
    }

    public SurfaceTextureDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);
    }

    public class SurfaceTextureRenderer implements Renderer {
        public void onDrawFrame(GL10 unused) {
            // note -- dont LOG here, this is frame loop
            // Redraw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            LOG.debug("Rendering frame");
            if(textureId != -1) {
                if(directVideo == null) {
                    LOG.info("Creating directVideo at start of drawFrame using texture {}", textureId);
                    directVideo = new DirectVideo(textureId);
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
