package com.trioscope.chameleon.util;

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

    public static native void scaleAndConvertI420ToNV21(byte[] input, byte[] output, int oldWidth, int oldHeight, int newWidth, int newHeight);
}
