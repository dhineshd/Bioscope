package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;

import com.trioscope.chameleon.util.ColorConversionUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 9/28/15.
 */
@Slf4j
public class CameraFrameUtil {
    public static Bitmap convertToBmp(final int[] pixelsBuffer, final int width, final int height) {
        int screenshotSize = width * height;
        for (int i = 0; i < screenshotSize; ++i) {
            // The alpha and green channels' positions are preserved while the red and blue are swapped
            // since the received frame is in RGB but Bitmap expects BGR. May need to revisit the
            // efficiency of this approach and see if we can get the frame directly in BGR.
            // Refer: https://www.khronos.org/registry/gles/extensions/EXT/EXT_read_format_bgra.txt
            pixelsBuffer[i] =
                    ((pixelsBuffer[i] & 0xff00ff00))
                            | ((pixelsBuffer[i] & 0x000000ff) << 16)
                            | ((pixelsBuffer[i] & 0x00ff0000) >> 16);
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixelsBuffer, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }

    public static byte[] convertYUV420888ByteArrayToJPEGByteArray(
            final byte[] frameData,
            final ByteArrayOutputStream stream,
            final int frameWidth,
            final int frameHeight,
            final int targetWidth,
            final int targetHeight,
            final int quality) {
        byte[] nv21Bytes = ColorConversionUtil.scaleAndConvertI420ToNV21AndReturnByteArray(
                frameData, frameWidth, frameHeight, targetWidth, targetHeight, false);
        YuvImage yuvimage = new YuvImage(nv21Bytes, ImageFormat.NV21, targetWidth, targetHeight, null);
        yuvimage.compressToJpeg(new Rect(0, 0, targetWidth, targetHeight),
                quality, stream);
        return stream.toByteArray();
    }

    public static byte[] convertYUV420888ByteBufferToJPEGByteArray(
            final ByteBuffer frameData,
            final ByteBuffer outputBuffer,
            final ByteArrayOutputStream stream,
            final int frameWidth,
            final int frameHeight,
            final int targetWidth,
            final int targetHeight,
            final int quality) {
        ColorConversionUtil.scaleAndConvertI420ByteBufferToNV21ByteBuffer(
                frameData, outputBuffer, frameWidth, frameHeight, targetWidth, targetHeight);
        YuvImage yuvimage = new YuvImage(outputBuffer.array(), ImageFormat.NV21, targetWidth, targetHeight, null);
        yuvimage.compressToJpeg(new Rect(0, 0, targetWidth, targetHeight),
                quality, stream);
        return stream.toByteArray();
    }

    public static byte[] convertYUV420888ImageToJPEGByteArray(
            final Image frameData,
            final byte[] outputByteArray,
            final ByteArrayOutputStream stream,
            final int frameWidth,
            final int frameHeight,
            final int targetWidth,
            final int targetHeight,
            final int quality) {
        Image.Plane[] imagePlanes = frameData.getPlanes();
        ColorConversionUtil.scaleAndConvertI420ToNV21Method2(
                imagePlanes[0].getBuffer(), imagePlanes[1].getBuffer(), imagePlanes[2].getBuffer(),
                outputByteArray, frameWidth, frameHeight, targetWidth, targetHeight);
        YuvImage yuvimage = new YuvImage(
                outputByteArray,
                ImageFormat.NV21, targetWidth, targetHeight, null);
        yuvimage.compressToJpeg(new Rect(0, 0, targetWidth, targetHeight),
                quality, stream);
        return stream.toByteArray();
    }

    public static byte[] convertNV21ToJPEGByteArray(
            final byte[] frameData,
            final ByteArrayOutputStream stream,
            final int frameWidth,
            final int frameHeight,
            final int targetWidth,
            final int targetHeight,
            final int quality) {
        YuvImage yuvimage = new YuvImage(frameData, ImageFormat.NV21,
                frameWidth, frameHeight, null);
        yuvimage.compressToJpeg(new Rect(0, 0, targetWidth, targetHeight),
                quality, stream);
        return stream.toByteArray();
    }
}
