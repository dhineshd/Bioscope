package com.trioscope.chameleon.util;

import android.graphics.ImageFormat;
import android.media.Image;

import com.trioscope.chameleon.aop.Timed;

import java.nio.ByteBuffer;

/**
 * Created by phand on 8/6/15.
 */
public class ImageUtil {
    @Timed
    public static byte[] getDataFromImage(Image image) {
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

        getDataFromImage(image, data);
        return data;
    }

    public static void getDataFromImage(Image image, byte[] data) {
        int format = image.getFormat();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride, pixelStride;
        // Read image data
        Image.Plane[] planes = image.getPlanes();
        // Check image validity
        if (format == ImageFormat.JPEG) {
            // JPEG doesn't have pixelstride and rowstride, treat it as 1D buffer.
            ByteBuffer buffer = planes[0].getBuffer();
            data = new byte[buffer.capacity()];
            buffer.get(data);
        } else if (format == ImageFormat.YUV_420_888) {
            int offset = 0;
            byte[] rowData = new byte[planes[0].getRowStride()];
            for (int i = 0; i < planes.length; i++) {
                ByteBuffer buffer = planes[i].getBuffer();
                rowStride = planes[i].getRowStride();
                pixelStride = planes[i].getPixelStride();
                // For multi-planar yuv images, assuming yuv420 with 2x2 chroma subsampling.
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    int bytesPerPixel = ImageFormat.getBitsPerPixel(format) / 8;
                    if (pixelStride == bytesPerPixel) {
                        // Special case: optimized read of the entire row
                        int length = w * bytesPerPixel;
                        buffer.get(data, offset, length);
                        // Advance buffer the remainder of the row stride
                        buffer.position(buffer.position() + rowStride - length);
                        offset += length;
                    } else {
                        // Generic case: should work for any pixelStride but slower.
                        // Use use intermediate buffer to avoid read byte-by-byte from
                        // DirectByteBuffer, which is very bad for performance.
                        // Also need avoid access out of bound by only reading the available
                        // bytes in the bytebuffer.
                        int readSize = rowStride;
                        if (buffer.remaining() < readSize) {
                            readSize = buffer.remaining();
                        }
                        buffer.get(rowData, 0, readSize);
                        for (int col = 0; col < w; col++) {
                            data[offset++] = rowData[col * pixelStride];
                        }
                    }
                }
            }
        } else {
            throw new RuntimeException("Unsupported image format: " + format);
        }
    }
}
