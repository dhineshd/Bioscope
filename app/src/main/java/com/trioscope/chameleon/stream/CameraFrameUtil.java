package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

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

    public static byte[] convertYUV420888ByteBufferToJPEGByteArray(
            final ByteBuffer frameData,
            final ByteBuffer outputBuffer,
            final ByteBuffer scalingBuffer,
            final ByteBuffer rotationBuffer,
            final ByteArrayOutputStream stream,
            final int frameWidth,
            final int frameHeight,
            final int targetWidth,
            final int targetHeight,
            final int quality,
            final boolean isHorizontallyFlipped,
            final int orientationDegrees) {

        int width = targetWidth, height = targetHeight;

        // TODO : Rotation?
        if (frameWidth == targetWidth && frameHeight == targetHeight) {
            ColorConversionUtil.convertI420ByteBufferToNV21ByteBuffer(
                    frameData, outputBuffer, frameWidth, frameHeight,
                    isHorizontallyFlipped);
        } else {
            ColorConversionUtil.scaleAndConvertI420ByteBufferToNV21ByteBuffer(
                    frameData, outputBuffer, scalingBuffer, rotationBuffer,
                    frameWidth, frameHeight, targetWidth, targetHeight,
                    isHorizontallyFlipped, orientationDegrees);

            if (orientationDegrees == 90 || orientationDegrees == 270) {
                // Swapping height and width since rotation by 90 or 270 will result in transpose.
                width = targetHeight;
                height = targetWidth;
            }
        }

        YuvImage yuvimage = new YuvImage(outputBuffer.array(), ImageFormat.NV21, width, height, null);
        yuvimage.compressToJpeg(new Rect(0, 0, width, height),
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
