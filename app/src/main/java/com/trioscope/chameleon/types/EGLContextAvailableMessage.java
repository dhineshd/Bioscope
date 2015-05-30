package com.trioscope.chameleon.types;

import android.graphics.SurfaceTexture;

import javax.microedition.khronos.egl.EGLContext;

import lombok.Data;

/**
 * Created by phand on 5/30/15.
 */
@Data
public class EGLContextAvailableMessage {
    private int glTextureId;
    private EGLContext eglContext;
    private SurfaceTexture surfaceTexture;
}
