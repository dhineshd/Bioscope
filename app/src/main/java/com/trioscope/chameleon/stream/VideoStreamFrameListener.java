package com.trioscope.chameleon.stream;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.stream.messages.SendRecordedVideoResponse;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.PeerInfo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.Socket;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
@Slf4j
@RequiredArgsConstructor
public class VideoStreamFrameListener implements CameraFrameAvailableListener, ServerEventListener {
    private static final int STREAMING_FRAMES_PER_SEC = 20;
    private static final int STREAMING_COMPRESSION_QUALITY = 20; // 1 - 100

    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    @Setter
    private volatile boolean isStreamingStarted;
    private volatile OutputStream destOutputStream;
    @Setter
    private volatile RecordingEventListener recordingEventListener;

    private ByteArrayOutputStream stream =
            new ByteArrayOutputStream(ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE);
    private Gson gson = new Gson();

    private long previousFrameSendTimeMs = 0;

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final int[] data) {
        long frameProcessingStartTime = System.currentTimeMillis();
        int w = cameraInfos.getCaptureResolution().getWidth();
        int h = cameraInfos.getCaptureResolution().getHeight();

        if (destOutputStream != null) {

            if (shouldStreamCurrentFrame()) {
                stream.reset();
                new WeakReference<Bitmap>(convertToBmp(data, w, h)).get()
                        .compress(Bitmap.CompressFormat.JPEG, STREAMING_COMPRESSION_QUALITY, stream);
                byte[] byteArray = stream.toByteArray();

                try {
                    destOutputStream.write(byteArray, 0, byteArray.length);
                    previousFrameSendTimeMs = System.currentTimeMillis();
                    //log.info("Sending image to remote client.. bytes = {}, process latency = {}",
                    //        byteArray.length, System.currentTimeMillis() - frameProcessingStartTime);
                } catch (IOException e) {
                    log.error("Failed to send data to client", e);
                    destOutputStream = null;
                }
            }
        }
    }

    private boolean shouldStreamCurrentFrame() {
        return ((System.currentTimeMillis() - previousFrameSendTimeMs) >=
                (1000 / STREAMING_FRAMES_PER_SEC));
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
    public void onClientRequest(Socket clientSocket, PeerMessage messageFromClient) {
        log.info("onClientRequest invoked for client = {}, isStreamingStarted = {}",
                clientSocket.getInetAddress().getHostAddress(), isStreamingStarted);
        if (PeerMessage.Type.CONNECTION_HANDSHAKE.equals(messageFromClient.getType())){
            startStreaming(clientSocket);
        } else if (PeerMessage.Type.START_RECORDING.equals(messageFromClient.getType())){
            startRecording();
        } else if (PeerMessage.Type.STOP_RECORDING.equals(messageFromClient.getType())){
            stopRecording();
        } else if (PeerMessage.Type.SEND_RECORDED_VIDEO_REQUEST.equals(messageFromClient.getType())){
            requestRecordedVideo(clientSocket);
        }
    }

    private void startStreaming(final Socket clientSocket){
        try {
            destOutputStream = clientSocket.getOutputStream();
            log.info("Destination output stream set in Thread = {}", Thread.currentThread());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = chameleonApplication.getApplicationContext();
        if (context != null && !isStreamingStarted){
            Intent intent = new Intent(context, ConnectionEstablishedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT)
                    .role(PeerInfo.Role.CREW_MEMBER)
                    .build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, gson.toJson(peerInfo));
            context.startActivity(intent);
            isStreamingStarted = true;
        }
    }

    private void startRecording() {
        log.info("Received message to start recording!");
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(chameleonApplication);
        manager.sendBroadcast(new Intent(ChameleonApplication.START_RECORDING_ACTION));
    }

    private void stopRecording() {
        log.info("Received message to stop recording!");
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(chameleonApplication);
        manager.sendBroadcast(new Intent(ChameleonApplication.STOP_RECORDING_ACTION));
    }

    private void requestRecordedVideo(final Socket clientSocket) {
        log.info("Received message to send recorded video!");
        File videoFile = chameleonApplication.getVideoFile();
        if (videoFile != null){
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                PrintWriter pw  = new PrintWriter(clientSocket.getOutputStream());
                SendRecordedVideoResponse response = SendRecordedVideoResponse.builder()
                        .fileSizeBytes(videoFile.length()).build();
                PeerMessage responseMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO_RESPONSE)
                        .contents(gson.toJson(response)).build();
                log.info("Sending file size msg = {}", gson.toJson(responseMsg));
                pw.println(gson.toJson(responseMsg));
                pw.close();

                clientSocket.setSendBufferSize(65536);
                clientSocket.setReceiveBufferSize(65536);
                outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
                inputStream = new BufferedInputStream(new FileInputStream(videoFile));
                byte[] buffer = new byte[65536];
                int bytesRead = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1){
                    log.info("Sending recorded file.. bytes = {}", bytesRead);
                    outputStream.write(buffer, 0, bytesRead);
                }
                log.info("Successfully sent recorded file!");
            } catch (IOException e) {
                log.error("Failed to send recorded video file", e);
            } finally {
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("Failed to close streams when sending recorded video", e);
                }
            }
        }
    }
}
