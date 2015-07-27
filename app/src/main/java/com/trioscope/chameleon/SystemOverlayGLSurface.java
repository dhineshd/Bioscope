package com.trioscope.chameleon;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;

import com.trioscope.chameleon.camera.impl.FBOPreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.opengl.DirectVideo;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 5/18/15.
 */
public class SystemOverlayGLSurface extends GLSurfaceView {
    private static final Logger LOG = LoggerFactory.getLogger(SystemOverlayGLSurface.class);
    private Handler eglContextHandler;

    private SurfaceTextureRenderer renderer;

    @Getter
    private SurfaceTexture surfaceTexture;

    @Getter
    private EGLContext eglContext;

    @Getter
    private int textureId;
    private int fboId;
    private DirectVideo directVideo;

    @Setter
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    @Setter
    private CameraInfo cameraInfo;

    public SystemOverlayGLSurface(Context context, Handler eglContextHandler) {
        super(context);
        this.eglContextHandler = eglContextHandler;
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

    public class SurfaceTextureRenderer implements GLSurfaceView.Renderer {
        public void onDrawFrame(GL10 unused) {
            // note -- dont log in here, this is frame loop
            // Redraw background color
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            surfaceTexture.updateTexImage();
            LOG.debug("Drawing surfaceView frame using textureId {}, directVideo {}", textureId, directVideo);

            if (textureId != -1) {
                if (directVideo == null) {
                    if (cameraInfo != null) {
                        fboId = createFBO();
                        LOG.info("Creating directVideo at start of drawFrame using texture {}", textureId);
                        directVideo = new DirectVideo(textureId, fboId);
                    } else {
                        LOG.info("Waiting to create DirectVideo since we dont have cameraInfo yet");
                    }
                } else {
                    LOG.debug("Drawing direct video");
                    directVideo.draw();
                    pullRenderIntoMemory();

                    // Rebind default frame buffer
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                }
            }
        }

        /**
         * Creates a user-defined FrameBufferObject (FBO) for rendering rather than using the default FrameBuffer
         *
         * @return the OpenGL id for the created FBO
         */
        private int createFBO() {
            // Create an FBO for rendering rather than rendering directly to the default FrameBuffer
            int[] ids = new int[1];
            GLES20.glGenFramebuffers(1, ids, 0);
            int fboId = ids[0];
            LOG.info("Creating frame buffer id {}", fboId);

            GLES20.glViewport(0, 0, cameraInfo.getCaptureResolution().getWidth(), cameraInfo.getCaptureResolution().getHeight());
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
            GLES20.glGenRenderbuffers(1, ids, 0);
            int colorBufferId = ids[0];
            LOG.info("Generated color buffer {}", colorBufferId);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, colorBufferId);
            //The storage format is RGBA8
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_RGB565, cameraInfo.getCaptureResolution().getWidth(), cameraInfo.getCaptureResolution().getHeight());
            LOG.info("Created render buffer storage");
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_RENDERBUFFER, colorBufferId);
            LOG.info("FrameBuffer color render buffer is set");
            //-------------------------
            GLES20.glGenRenderbuffers(1, ids, 0);
            int depthBufferId = ids[0];
            LOG.info("Generated depth buffer {}", depthBufferId);
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBufferId);
            LOG.info("Bound depth buffer as render buffer");
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, cameraInfo.getCaptureResolution().getWidth(), cameraInfo.getCaptureResolution().getHeight());
            LOG.info("Created storage for depth buffer");
            //-------------------------
            //Attach depth buffer to FBO
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBufferId);
            LOG.info("Done creating FBO with id {}", fboId);


            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            LOG.info("FrameBuffer Status is {} (complete={})", status, GLES20.GL_FRAMEBUFFER_COMPLETE);

            return fboId;
        }

        private void pullRenderIntoMemory() {
            if (cameraInfo == null) {
                LOG.info("Camera info not available - not pulling into memory");
            } else {
                LOG.debug("Pulling rendered FBO into main memory");
                int w = cameraInfo.getCaptureResolution().getWidth(), h = cameraInfo.getCaptureResolution().getHeight();
                int size = w * h;
                int b[] = new int[size];
                IntBuffer ib = IntBuffer.wrap(b);
                ib.position(0);

                //ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
                //buf.order(ByteOrder.nativeOrder());

                // Bind rendered FBO and read pixels
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);

                // TODO: Reading at high resolutions causes lots of memory usage and slows FPS down.
                GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);
                LOG.debug("IntBuffer: {}", b);
                cameraFrameBuffer.frameAvailable(cameraInfo, ib.array());
            }
        }

        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // Set the background frame color
            LOG.info("Surface created -- openGL thread is {}", Thread.currentThread());
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);

            createSurfaceTexture();

            // Alert the application that the EGLContext is created, and the surfaceTexture is ready for use
            EGLContextAvailableMessage msg = new EGLContextAvailableMessage();
            msg.setEglContext(eglContext);
            msg.setGlTextureId(textureId);
            msg.setSurfaceTexture(surfaceTexture);
            eglContextHandler.sendMessage(eglContextHandler.obtainMessage(FBOPreviewDisplayer.EGLContextAvailableHandler.EGL_CONTEXT_AVAILABLE, msg));
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
