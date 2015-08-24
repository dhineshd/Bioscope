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

    public static native byte[] convertI420ToNV12(byte[] input, int width, int height);

    public static native byte[] convertI420ToNV21(byte[] input, int width, int height);

    public static native byte[] i420ScaleAndRotateBy90(byte[] input, int currentWidth, int currentHeight, int newWidth, int newHeight);
}
