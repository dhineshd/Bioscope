package com.trioscope.chameleon.stream;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;

import lombok.Getter;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
public class VideoStreamFrameListener implements CameraFrameAvailableListener {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);
    private final static int PREVIEW_IMAGE_AVAILABLE = 1;
    private ImageView imageView;
    private Context context;
    private Handler localUiHandler;
    private ParcelFileDescriptor writeParcelFd;
    @Getter
    private StreamThreadHandler handler = new StreamThreadHandler();
    public VideoStreamFrameListener(){
        //Canvas canvas = new Canvas(bmp);
        localUiHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case PREVIEW_IMAGE_AVAILABLE:
                        if (imageView != null){
                            imageView.setImageBitmap((Bitmap) msg.obj);
                        }
                        break;
                    default:
                        super.handleMessage(msg);
                }

            }
        };
    }

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        LOG.info("Frame available for streaming");
        int w = 50, h = 50;
        //if (Math.random() < 0.1) {
        // localUiHandler.sendMessage(localUiHandler.obtainMessage(PREVIEW_IMAGE_AVAILABLE, convertToBmpMethod2(data, w, h)));
        //}
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

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixelsBuffer, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }

    private Bitmap convertToBmpMethod2(final int[] data, final int width, final int height){
        int screenshotSize = width * height;

        IntBuffer pixelsBuffer = IntBuffer.wrap(data);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(pixelsBuffer);
        return  bmp;
    }
    public class StreamThreadHandler extends Handler {
        public static final int IMAGEVIEW_AVAILABLE = 1;
        public static final int FRAME_DESTINATION_AVAILABLE = 2;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case IMAGEVIEW_AVAILABLE:
                    LOG.info("ImageView available, parameters {}", msg.obj);
                    imageView = (ImageView) msg.obj;
                    break;
                case FRAME_DESTINATION_AVAILABLE:
                    LOG.info("Frame destination available, parameters {}", msg.obj);
                    writeParcelFd = (ParcelFileDescriptor) msg.obj;
                    break;
                default:
                    super.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
