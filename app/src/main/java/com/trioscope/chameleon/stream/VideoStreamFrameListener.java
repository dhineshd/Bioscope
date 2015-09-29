package com.trioscope.chameleon.stream;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.stream.messages.StartRecordingResponse;
import com.trioscope.chameleon.stream.messages.StreamMetadata;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.SendVideoToPeerMetadata;
import com.trioscope.chameleon.types.Size;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.Socket;

import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 6/28/15.
 */
@Slf4j
public class VideoStreamFrameListener implements CameraFrameAvailableListener, ServerEventListener {
    private static final int STREAMING_FRAMES_PER_SEC = 15;
    private static final int STREAMING_COMPRESSION_QUALITY = 30; // 0 worst - 100 best
    private static final Size DEFAULT_STREAM_IMAGE_SIZE = new Size(480, 270); // 16 : 9

    @NonNull
    private volatile ChameleonApplication chameleonApplication;

    @Setter
    private volatile boolean isStreamingStarted;
    private volatile OutputStream destOutputStream;

    private final ByteArrayOutputStream stream =
            new ByteArrayOutputStream(ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE_BYTES);
    private final Gson gson = new Gson();

    private Size cameraFrameSize = ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE;
    private long previousFrameSendTimeMs = System.currentTimeMillis();
    private byte[] finalFrameData;
    // = new byte[DEFAULT_STREAM_IMAGE_SIZE.getWidth() * DEFAULT_STREAM_IMAGE_SIZE.getHeight() * 3/2];

    @Setter
    private Handler sendVideoToPeerHandler;

    public VideoStreamFrameListener(ChameleonApplication chameleonApplication) {
        this.chameleonApplication = chameleonApplication;
    }

    @Override
    //@Timed
    public void onFrameAvailable(final CameraInfo cameraInfos, final CameraFrameData data, FrameInfo frameInfo) {
        int cameraWidth = cameraInfos.getCameraResolution().getWidth();
        int cameraHeight = cameraInfos.getCameraResolution().getHeight();

        int targetWidth = DEFAULT_STREAM_IMAGE_SIZE.getWidth();
        int targetHeight = DEFAULT_STREAM_IMAGE_SIZE.getHeight();

        log.debug("Frame available to send across the stream on thread {}, " +
                        "frame timestamp = {}, currentTime = {}, uptime = {}",
                Thread.currentThread(), frameInfo.getTimestampNanos() / 1000000,
                System.currentTimeMillis(), SystemClock.uptimeMillis());
        if (destOutputStream != null) {

            if (shouldStreamCurrentFrame()) {
                log.debug("Decided to send current frame across stream");
                try {
                    stream.reset();
                    byte[] byteArray = null;

                    if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
                        if (data.getBytes() != null) {
                            byteArray = CameraFrameUtil.convertYUV420888ByteArrayToJPEGByteArray(
                                    data.getBytes(), stream, cameraWidth, cameraHeight, targetWidth, targetHeight,
                                    frameInfo.isHorizontallyFlipped(), STREAMING_COMPRESSION_QUALITY);
                        }
                    } else if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.NV21) {
                        byteArray = CameraFrameUtil.convertNV21ToJPEGByteArray(data.getBytes(),
                                stream, cameraWidth, cameraHeight, targetWidth, targetHeight,
                                STREAMING_COMPRESSION_QUALITY);
                    } else if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.RGBA_8888) {
                        new WeakReference<Bitmap>(CameraFrameUtil.convertToBmp(data.getInts(), cameraWidth, cameraHeight)).get()
                                .compress(Bitmap.CompressFormat.JPEG, STREAMING_COMPRESSION_QUALITY, stream);
                        byteArray = stream.toByteArray();
                    }
                    if (byteArray != null) {
                        log.debug("Stream image type = {}, size = {} bytes", cameraInfos.getEncoding(), byteArray.length);
                        StreamMetadata streamMetadata = StreamMetadata.builder()
                                .horizontallyFlipped(frameInfo.isHorizontallyFlipped())
                                .build();
                        PrintWriter pw = new PrintWriter(destOutputStream);
                        pw.println(gson.toJson(streamMetadata));
                        pw.close();
                        destOutputStream.write(byteArray, 0, byteArray.length);
                    }
                    previousFrameSendTimeMs = System.currentTimeMillis();
                } catch (Exception e) {
                    log.error("Failed to send data to client", e);
                }
            }
        }
    }

    private boolean shouldStreamCurrentFrame() {
        return ((System.currentTimeMillis() - previousFrameSendTimeMs) >=
                (1000 / STREAMING_FRAMES_PER_SEC));
    }

    @Override
    public void onClientRequest(Socket clientSocket, PeerMessage messageFromClient) {
        log.info("onClientRequest invoked for client = {}, isStreamingStarted = {}",
                clientSocket.getInetAddress().getHostAddress(), isStreamingStarted);

        switch (messageFromClient.getType()) {
            case START_SESSION:
                String peerUserName = messageFromClient.getSenderUserName();
                //startStreaming(clientSocket, peerUserName);
                break;
            case TERMINATE_SESSION:
                terminateSession();
                break;
            case SESSION_HEARTBEAT:
                updateConnectionHealth();
                break;
            case START_RECORDING:
                startRecording(clientSocket);
                break;
            case STOP_RECORDING:
                stopRecording();
                break;
            case SEND_RECORDED_VIDEO:
                sendRecordedVideo(clientSocket);
                break;
            default:
                log.debug("Unknown message received from client. Type = {}",
                        messageFromClient.getType());
        }
    }

    private void startStreaming(final Socket clientSocket, String peerUserName) {
        try {
            destOutputStream = clientSocket.getOutputStream();
            //chameleonApplication.setStreamingDestOutputStream(clientSocket.getOutputStream());

            log.debug("Destination output stream set in Thread = {}", Thread.currentThread());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Context context = chameleonApplication.getApplicationContext();
        if (context != null && !isStreamingStarted) {
            Intent intent = new Intent(context, ConnectionEstablishedActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT)
                    .role(PeerInfo.Role.CREW_MEMBER)
                    .userName(peerUserName)
                    .build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, gson.toJson(peerInfo));
            context.startActivity(intent);
            isStreamingStarted = true;
        }
    }

    public void terminateSession() {
        destOutputStream = null;
        log.info("Session terminated by peer!");
    }

    private void updateConnectionHealth() {
        //TODO : Need to update last seen time and declare connection as
        // dead when no heartbeat received for a while. When that happens,
        // show dialog and take user back to main activity
    }

    private void startRecording(final Socket clientSocket) {
        log.debug("Received message to start recording!");
        try {
            PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
            StartRecordingResponse response = StartRecordingResponse.builder()
                    .currentTimeMillis(System.currentTimeMillis()).build();
            PeerMessage responseMsg = PeerMessage.builder()
                    .type(PeerMessage.Type.START_RECORDING_RESPONSE)
                    .contents(gson.toJson(response)).build();
            log.debug("Sending file size msg = {}", gson.toJson(responseMsg));
            pw.println(gson.toJson(responseMsg));
            pw.close();
        } catch (IOException e) {
            log.error("Failed to send START_RECORDING_RESPONSE", e);
        }
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(chameleonApplication);
        manager.sendBroadcast(new Intent(ChameleonApplication.START_RECORDING_ACTION));
    }

    private void stopRecording() {
        log.debug("Received message to stop recording!");
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(chameleonApplication);
        manager.sendBroadcast(new Intent(ChameleonApplication.STOP_RECORDING_ACTION));
    }

    private void sendRecordedVideo(final Socket clientSocket) {
        log.debug("Received message to send recorded video!");
        File videoFile = chameleonApplication.getVideoFile();
        SendVideoToPeerMetadata metadata = SendVideoToPeerMetadata.builder()
                .clientSocket(clientSocket)
                .videoFile(videoFile)
                .recordingStartTimeMillis(chameleonApplication.getRecordingStartTimeMillis())
                .recordingHorizontallyFlipped(chameleonApplication.isRecordingHorizontallyFlipped())
                .build();
        Message msg = sendVideoToPeerHandler.obtainMessage(ChameleonApplication.SEND_VIDEO_TO_PEER_MESSAGE, metadata);
        sendVideoToPeerHandler.sendMessage(msg);
    }
}
