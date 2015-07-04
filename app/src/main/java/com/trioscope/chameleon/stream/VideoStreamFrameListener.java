package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
@Slf4j
public class VideoStreamFrameListener implements CameraFrameAvailableListener {
    private ByteArrayOutputStream stream = new ByteArrayOutputStream(160 * 90 * 4); // 160 x 90
    @NonNull
    private ParcelFileDescriptor.AutoCloseOutputStream outputStream;

    public VideoStreamFrameListener(final ParcelFileDescriptor writeStreamFd){
        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeStreamFd);
    }

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();
        //log.info("Frame available for streaming w = {}, h = {}, array size =  {}", w, h, data.length);
        Bitmap bmp = convertToBmpMethod4(data, w, h);
        stream.reset();
        boolean compressSuccesful = bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
        bmp.recycle();
        try {
            byte[] byteArray = stream.toByteArray();
            outputStream.write(byteArray, 0, byteArray.length);
            //log.info("Sending preview image to local server.. bytes = {}, compress success = {}", byteArray.length, compressSuccesful);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Bitmap convertToBmpMethod1(final int[] data, final int width, final int height){
        int screenshotSize = width * height;
        int pixelsBuffer[] = new int[screenshotSize];

        IntBuffer intBuffer = IntBuffer.wrap(data);
        intBuffer.get(pixelsBuffer);

        for (int i = 0; i < screenshotSize; ++i) {
            // The alpha and green channels' positions are preserved while the red and blue are swapped
            pixelsBuffer[i] = ((pixelsBuffer[i] & 0xff00ff00)) | ((pixelsBuffer[i] & 0x000000ff) << 16) | ((pixelsBuffer[i] & 0x00ff0000) >> 16);
        }

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bmp.setPixels(pixelsBuffer, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }

    private Bitmap convertToBmpMethod2(final int[] data, final int width, final int height){
        IntBuffer pixelsBuffer = IntBuffer.wrap(data);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        bmp.copyPixelsFromBuffer(pixelsBuffer);
        return  bmp;
    }

    private Bitmap convertToBmpMethod3(final int[] data, final int width, final int height){
        int offset1, offset2;
        int bitmapSource[] = new int[width * height];
        for (int i = 0; i < height; i++) {
            offset1 = i * width;
            offset2 = (height - i - 1) * width;
            for (int j = 0; j < width; j++) {
                int texturePixel = data[offset1 + j];
                int blue = (texturePixel >> 16) & 0xff;
                int red = (texturePixel << 16) & 0x00ff0000;
                int pixel = (texturePixel & 0xff00ff00) | red | blue;
                bitmapSource[offset2 + j] = pixel;
            }
        }
        return Bitmap.createBitmap(bitmapSource, width, height, Bitmap.Config.RGB_565);
    }

    private Bitmap convertToBmpMethod4(final int[] data, final int width, final int height){
        final Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.RGB_565);
        final int screenshotSize = width * height;
        bitmap.setPixels(data, screenshotSize - width, -width,
                0, 0, width, height);

        short sBuffer[] = new short[screenshotSize];
        ShortBuffer sb = ShortBuffer.wrap(sBuffer);
        bitmap.copyPixelsToBuffer(sb);

        // Making created bitmap (from OpenGL points) compatible with
        // Android
        // bitmap
        for (int i = 0; i < screenshotSize; ++i) {
            short v = sBuffer[i];
            sBuffer[i] = (short) (((v & 0x1f) << 11) | (v & 0x7e0) | ((v & 0xf800) >> 11));
        }
        sb.rewind();
        bitmap.copyPixelsFromBuffer(sb);
        return bitmap;
    }

    private Bitmap convertToBmpMethod5(final int[] data, final int width, final int height){
        final Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.RGB_565);
        final int screenshotSize = width * height;
        bitmap.setPixels(data, screenshotSize - width, -width,
                0, 0, width, height);

        short sBuffer[] = new short[screenshotSize];
        ShortBuffer sb = ShortBuffer.wrap(sBuffer);
        bitmap.copyPixelsToBuffer(sb);

        // Making created bitmap (from OpenGL points) compatible with
        // Android
        // bitmap
//        for (int i = 0; i < screenshotSize; ++i) {
//            short v = sBuffer[i];
//            sBuffer[i] = (short) (((v & 0x1f) << 11) | (v & 0x7e0) | ((v & 0xf800) >> 11));
//        }
        sb.rewind();
        bitmap.copyPixelsFromBuffer(sb);
        return bitmap;
    }
}
