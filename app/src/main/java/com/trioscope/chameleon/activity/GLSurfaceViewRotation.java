package com.trioscope.chameleon.activity;


import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.widget.RelativeLayout;

import com.trioscope.chameleon.R;
import com.trioscope.chameleon.opengl.BlueScreenRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Created by phand on 6/19/15.
 */
public class GLSurfaceViewRotation extends AppCompatActivity {
    private static final Logger LOG = LoggerFactory.getLogger(GLSurfaceViewRotation.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gl_surface_view_rotation);
        LOG.info("Activity is created");

        final Handler createHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                LOG.info("Handle message running on thread {}", Thread.currentThread());
                GLSurfaceView glSurfaceView = new GLSurfaceView(GLSurfaceViewRotation.this);
                glSurfaceView.setEGLContextFactory(new ContextFactory());
                glSurfaceView.setRenderer(new BlueScreenRenderer());

                RelativeLayout layout = (RelativeLayout) findViewById(R.id.gl_surface_view_rotation_layout);
                layout.addView(glSurfaceView);
            }
        };

        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                LOG.info("Timer task running on thread {}", Thread.currentThread());
                createHandler.sendMessage(createHandler.obtainMessage());
            }
        }, 5000);
    }


    private static class ContextFactory implements GLSurfaceView.EGLContextFactory {
        private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;

        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            LOG.info("creating OpenGL ES 2.0 context");
            checkEglError("Before eglCreateContext", egl);
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            EGLContext context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            checkEglError("After eglCreateContext", egl);
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    private static void checkEglError(String prompt, EGL10 egl) {
        int error;
        while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
            LOG.error("{}: EGL error: 0x{}", prompt, error);
        }
    }
}
