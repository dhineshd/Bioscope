package com.trioscope.chameleon.stream;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

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
    private ByteArrayOutputStream stream = new ByteArrayOutputStream(160 * 90 * 4); // 160 x 90
    @Setter
    private Context context;
    private OutputStream destOutputStream;

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();
        //log.info("Frame available for streaming w = {}, h = {}, array size =  {}", w, h, data.length);

        if (destOutputStream != null){
            stream.reset();
            Bitmap bmp = convertToBmpMethod(data, w, h);
            boolean compressSuccesful = bmp.compress(Bitmap.CompressFormat.PNG, 100, stream);
            bmp.recycle();
            try {
                byte[] byteArray = stream.toByteArray();
                destOutputStream.write(byteArray, 0, byteArray.length);
                log.info("Sending preview image to local server.. bytes = {}, compress success = {}", byteArray.length, compressSuccesful);
            } catch (IOException e) {
                log.error("Failed to send data to client", e);
                destOutputStream = null;
                //throw new RuntimeException(e);
            }
        }
    }
    @Override
    public void onClientConnectionRequest(Socket clientSocket) {
        log.info("onClientConnectionRequest invoked isStreamingStarted = {}", isStreamingStarted);
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

    private Bitmap convertToBmpMethod(final int[] data, final int width, final int height) {
        int screenshotSize = width * height;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(data, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }
}
