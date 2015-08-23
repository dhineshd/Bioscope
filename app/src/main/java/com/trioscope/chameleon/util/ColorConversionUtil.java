package com.trioscope.chameleon.util;

/**
 * Created by dhinesh.dharman on 8/22/15.
 */
public class ColorConversionUtil {

    static{
        System.loadLibrary("YUVDemo");
        System.loadLibrary("stlport_shared");
        System.loadLibrary("yuv_shared");
    }

    // Following methods implemented YUVDemo native C module

    public static native byte[] convertI420ToNV12(byte[] input, int width, int height);

    public static native byte[] convertI420ToNV21(byte[] input, int width, int height);

    public static native byte[] i420ScaleAndRotateBy90(byte[] input, int currentWidth, int currentHeight, int newWidth, int newHeight);

    public static native byte[] convertI420ToNV21AndScale(byte[] input,  int width, int height, int newWidth, int newHeight);

}
