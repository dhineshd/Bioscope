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

    public static native void convertI420ByteBufferToNV12ByteBuffer(
            ByteBuffer input, ByteBuffer output, int width, int height, boolean isHorizontallyFlipped);

    public static native void convertI420ByteBufferToNV21ByteBuffer(
            ByteBuffer input, ByteBuffer output, int width, int height, boolean isHorizontallyFlipped);

    public static native void transformI420ByteBuffer(
            ByteBuffer input, ByteBuffer output, int width, int height, boolean isHorizontallyFlipped);

    public static native void scaleAndConvertI420ByteBufferToNV21ByteBuffer(
            ByteBuffer input, ByteBuffer output, ByteBuffer scalingBuf, ByteBuffer rotationBuf,
            int oldWidth, int oldHeight, int newWidth, int newHeight,
            boolean isHorizontallyFlipped, int orientationDegrees);

}
