package com.trioscope.chameleon.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import com.trioscope.chameleon.state.RotationState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import lombok.Setter;

/**
 * Created by phand on 5/28/15.
 */
public class DirectVideo {
    private static final Logger LOG = LoggerFactory.getLogger(DirectVideo.class);

    private final String vertexShaderCode =
            "attribute vec4 position;" +
                    "attribute vec2 inputTextureCoordinate;" +
                    "varying vec2 textureCoordinate;" +
                    "void main()" +
                    "{" +
                    "gl_Position = position;" +
                    "textureCoordinate = inputTextureCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require \n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;" +
                    "uniform samplerExternalOES s_texture;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );" +
                    "}";
    private int fboId;

    private FloatBuffer vertexBuffer, textureVerticesBuffer, textureVerticesRotatedBuffer;
    private ShortBuffer drawListBuffer;
    private final int mProgram;
    private int mPositionHandle;
    private int mColorHandle;
    private int mTextureCoordHandle;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in clockwise order:
            1.0f, 1.0f,
            1.0f, -1.0f,
            -1.0f, -1.0f,
            -1.0f, 1.0f
    };

    private short drawOrder[] = {0, 1, 2, 0, 2, 3}; // order to draw vertices

    static float textureVertices[] = { // in counterclockwise order:
            0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f,};


    static float textureVerticesRotated[] = { // in counterclockwise order:
            1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f,};

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private int textureId;

    @Setter
    private RotationState rotationState;

    public DirectVideo(int _texture) {
        this(_texture, 0); // Default FrameBuffer is 0
    }

    public DirectVideo(int textureId, int fboId) {
        this.textureId = textureId;
        this.fboId = fboId;

        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(textureVertices);
        textureVerticesBuffer.position(0);


        bb2 = ByteBuffer.allocateDirect(textureVerticesRotated.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesRotatedBuffer = bb2.asFloatBuffer();
        textureVerticesRotatedBuffer.put(textureVerticesRotated);
        textureVerticesRotatedBuffer.position(0);

        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);

        LOG.info("Created DirectVideo programs in OpenGL context using textureId {} and fboId {}", textureId, fboId);
    }

    private static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        return shader;
    }

    public void draw() {
        LOG.debug("Binding to fboId {} and drawing {}", fboId, Thread.currentThread());
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
        drawColor();
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void drawColor() {
        GLES20.glUseProgram(mProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);

        if (rotationState == null || rotationState.isPortrait())
            GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, textureVerticesBuffer);
        else {
            LOG.info("Rotation state is landscape!");
            GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, textureVerticesRotatedBuffer);
        }
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "s_texture");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);
    }
}
