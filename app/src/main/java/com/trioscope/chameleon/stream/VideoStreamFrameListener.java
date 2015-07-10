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
    private static final int TARGET_STREAMING_FRAME_RATE_PER_SEC = 5;

    @Setter
    private volatile boolean isStreamingStarted;
    @Setter
    private volatile OutputStream destOutputStream;
    @Setter
    private volatile Context context;

    private ByteArrayOutputStream stream = new ByteArrayOutputStream(4096 * 5);
    private Gson gson = new Gson();

    private long previousFrameSendTime = 0;

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();


        if (shouldStreamCurrentFrame()) {
            stream.reset();
            Bitmap bmp = convertToBmp(data, w, h);
            bmp.compress(Bitmap.CompressFormat.JPEG, 20, stream);
            bmp.recycle();
            byte[] byteArray = stream.toByteArray();

            if (destOutputStream != null) {
                try {
                    destOutputStream.write(byteArray, 0, byteArray.length);
                    previousFrameSendTime = System.currentTimeMillis();
                    //log.info("Sending preview image to remote client.. bytes = {}", byteArray.length);
                } catch (IOException e) {
                    log.error("Failed to send data to client", e);
                    destOutputStream = null;
                }
            }
        }
    }

    private boolean shouldStreamCurrentFrame() {
        return ((System.currentTimeMillis() - previousFrameSendTime) >=
                (1000 / TARGET_STREAMING_FRAME_RATE_PER_SEC));
    }

    private Bitmap convertToBmp(final int[] pixelsBuffer, final int width, final int height) {
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
            Intent intent = new Intent(context, ConnectionEstablishedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT).build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, gson.toJson(peerInfo));
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
