package com.trioscope.chameleon.util;

import android.graphics.ImageFormat;
import android.media.Image;

import com.trioscope.chameleon.aop.Timed;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 8/6/15.
 */
@Slf4j
public class ImageUtil {
    @Timed
    public static byte[] getDataFromImage(final Image image, byte[] tempBuffer) {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data;
        if (format == ImageFormat.JPEG) {
            data = new byte[image.getPlanes()[0].getBuffer().capacity()];
        } else if (format == ImageFormat.YUV_420_888) {
            data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        } else {
            throw new RuntimeException("Unsupported image format: " + format);
        }

        getDataFromImage(image, data, tempBuffer);
        return data;
    }

    @Timed
    public static void getDataFromImage(final Image image, final byte[] data, byte[] tempBuffer) {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride;
        // Read image data
        Image.Plane[] planes = image.getPlanes();
        // Check image validity
        if (format == ImageFormat.JPEG) {
            // JPEG doesn't have pixelstride and rowstride, treat it as 1D buffer.
            ByteBuffer buffer = planes[0].getBuffer();
            buffer.get(data);
        } else if (format == ImageFormat.YUV_420_888) {
            int offset = 0;
            int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;

            // y-plane

            // Special case: optimized read of all rows
            ByteBuffer buffer = planes[0].getBuffer();
            int length = width * height * bytesPerPixel;
            buffer.get(data, offset, length);
            offset += length;

            // u and v planes

            for (int i = 1; i < planes.length; i++) {
                buffer = planes[i].getBuffer();
                pixelStride = planes[i].getPixelStride();

                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.

                // Generic case: should work for any pixelStride but slower.
                // Use use intermediate buffer to avoid read byte-by-byte from
                // DirectByteBuffer, which is very bad for performance.
                // Also need avoid access out of bound by only reading the available
                // bytes in the bytebuffer.
                length = buffer.remaining();

                buffer.get(tempBuffer, 0, length);

                offset = copyBuffer(length, pixelStride, data, tempBuffer, offset);
            }
        } else {
            throw new RuntimeException("Unsupported image format: " + format);
        }
    }

    @Timed
    private static int copyBuffer(final int length, final int pixelStride, final byte[] data,
                                  final byte[] tempBuffer, int offset) {
        int len = length / pixelStride;
        for (int i = 0; i < len; i++) {
            tempBuffer[i] = tempBuffer[i * pixelStride];
        }
        System.arraycopy(tempBuffer, 0, data, offset, len);
        offset += len;
        return offset;
    }
}
