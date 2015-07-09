package com.trioscope.chameleon.stream;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.PeerInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
@Slf4j
public class VideoStreamFrameListener implements CameraFrameAvailableListener, ServerEventListener {
    @Setter
    private boolean isStreamingStarted;
    private ByteArrayOutputStream stream = new ByteArrayOutputStream(4096); // 160 x 90
    @Setter
    private Context context;
    @Setter
    private OutputStream destOutputStream;

    private long previousFrameSendTime = 0;

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();
        //log.info("Frame available for streaming w = {}, h = {}, array size =  {}", w, h, data.length);

        if (shouldStreamCurrentFrame()){
            stream.reset();
            Bitmap bmp = convertToBmpMethod(data, w, h);
            bmp.compress(Bitmap.CompressFormat.JPEG, 20, stream);
            bmp.recycle();
            byte[] byteArray = stream.toByteArray();

            if (destOutputStream != null){
                try {
                    destOutputStream.write(byteArray, 0, byteArray.length);
                    previousFrameSendTime = System.currentTimeMillis();
                    //log.info("Sending preview image to remote client.. bytes = {}", byteArray.length);
                } catch (IOException e) {
                    log.error("Failed to send data to client", e);
                    destOutputStream = null;
                    //throw new RuntimeException(e);
                }
            }
        }
    }

    private boolean shouldStreamCurrentFrame(){
        // Roughly 5 frames per second
        return ((System.currentTimeMillis() - previousFrameSendTime) >= 200);
    }


    private Bitmap convertToBmpMethod(final int[] data, final int width, final int height) {
        int screenshotSize = width * height;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(data, screenshotSize - width, -width, 0, 0, width, height);

        //final BitmapFactory.Options options = new BitmapFactory.Options();
        // Calculate inSampleSize
        //options.inSampleSize = 2;

        // Decode bitmap with inSampleSize set
        //options.inJustDecodeBounds = false;

        return bmp;
    }

    public static Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                         int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = 2;//calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @Override
    public void onClientConnectionRequest(Socket clientSocket) {
        log.info("onClientConnectionRequest invoked for client = {}, isStreamingStarted = {}",
                clientSocket.getInetAddress().getHostAddress(), isStreamingStarted);
        try {
            destOutputStream = clientSocket.getOutputStream();
            log.info("Destination output stream set in Thread = {}", Thread.currentThread());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (context != null && !isStreamingStarted){
            //TODO Don't start activity if we get second request
            Intent intent = new Intent(context, ConnectionEstablishedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT).build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, new Gson().toJson(peerInfo));
            context.startActivity(intent);
            isStreamingStarted = true;
        }
    }

    @Override
    public void onClientConnectionTerminated() {
        if (destOutputStream != null) {
            try {
                destOutputStream.close();
                destOutputStream = null;
                isStreamingStarted = false;
            } catch (IOException e) {
                log.warn("Failed to close client outputstream", e);
            }
        }
    }
}
