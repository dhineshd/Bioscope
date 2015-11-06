package com.trioscope.chameleon.util;

import java.nio.ByteBuffer;

/**
 * Created by dhinesh.dharman on 8/22/15.
 */
public class ColorConversionUtil {

    static{
        System.loadLibrary("stlport_shared");
        System.loadLibrary("yuv_shared");
        System.loadLibrary("yuv_jni");
    }

    // Following methods are implemented in "yuv_jni" native module which is our JNI wrapper for
    // using "libyuv" (https://code.google.com/p/libyuv/) library functions. The source can
    // be found under the "jni" directory

    public static native void convertI420ToNV12(byte[] input, byte[] output, int width, int height);

    public static native byte[] convertI420ToNV12AndReturnByteArray(byte[] input, int width, int height);

    public static native void convertI420ByteBufferToNV12ByteBuffer(
            ByteBuffer input, ByteBuffer output, int width, int height);

    public static native void convertI420ToNV12Method2(
            ByteBuffer y_plane, ByteBuffer u_plane, ByteBuffer v_plane,
            byte[] output, int width, int height);

    public static native void scaleAndConvertI420ToNV21(
            byte[] input, byte[] output, int oldWidth, int oldHeight, int newWidth, int newHeight);

    public static native byte[] scaleAndConvertI420ToNV21AndReturnByteArray(
            byte[] input, int oldWidth, int oldHeight, int newWidth, int newHeight, boolean inversion);

    public static native void scaleAndConvertI420ByteBufferToNV21ByteBuffer(
            ByteBuffer input, ByteBuffer output, int oldWidth, int oldHeight, int newWidth, int newHeight);

    public static native void scaleAndConvertI420ToNV21Method2(
            ByteBuffer y_plane, ByteBuffer u_plane, ByteBuffer v_plane,
            byte[] output, int oldWidth, int oldHeight, int newWidth, int newHeight);

    public static native void rotateI420By90Degrees(byte[] input, byte[] output, int width, int height);

}
