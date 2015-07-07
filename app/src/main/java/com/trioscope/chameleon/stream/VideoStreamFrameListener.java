package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;

import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
@Slf4j
public class VideoStreamFrameListener implements CameraFrameAvailableListener {
    @Setter
    @Getter
    private boolean isStreamingStarted;
    private ByteArrayOutputStream stream = new ByteArrayOutputStream(160 * 90 * 4); // 160 x 90
    @NonNull
    private ParcelFileDescriptor.AutoCloseOutputStream outputStream;

    public VideoStreamFrameListener(final ParcelFileDescriptor writeStreamFd) {
        outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeStreamFd);
    }

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();
        //log.info("Frame available for streaming w = {}, h = {}, array size =  {}", w, h, data.length);

        if (isStreamingStarted) {
            stream.reset();
            Bitmap bmp = convertToBmpMethod(data, w, h);
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
    }

    private Bitmap convertToBmpMethod(final int[] data, final int width, final int height) {
        int screenshotSize = width * height;
        int pixelsBuffer[] = new int[screenshotSize];

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(data, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }
}
