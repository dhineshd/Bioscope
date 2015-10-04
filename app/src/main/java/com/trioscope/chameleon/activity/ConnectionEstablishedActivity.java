package com.trioscope.chameleon.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.media.MediaMetadataRetriever;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.record.MediaCodecRecorder;
import com.trioscope.chameleon.record.VideoRecorder;
import com.trioscope.chameleon.stream.NetworkStreamer;
import com.trioscope.chameleon.stream.PreviewStreamer;
import com.trioscope.chameleon.stream.ServerEventListener;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.stream.messages.SendRecordedVideoResponse;
import com.trioscope.chameleon.stream.messages.StartRecordingResponse;
import com.trioscope.chameleon.stream.messages.StreamMetadata;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.network.IpUtil;
import com.trioscope.chameleon.util.network.WifiUtil;
import com.trioscope.chameleon.util.security.SSLUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity
        extends EnableForegroundDispatchForNFCMessageActivity
        implements ServerEventListener {
    public static final String LOCAL_RECORDING_METADATA_KEY = "LOCAL_RECORDING_METADATA";
    public static final String REMOTE_RECORDING_METADATA_KEY = "REMOTE_RECORDING_METADATA";
    public static final String CONNECTION_INFO_AS_JSON_EXTRA = "CONNECTION_INFO_AS_JSON_EXTRA";
    public static final String PEER_INFO = "PEER_INFO";
    private ChameleonApplication chameleonApplication;
    private StreamFromPeerTask streamFromPeerTask;
    private ReceiveVideoFromPeerTask receiveVideoFromPeerTask;
    private SendVideoToPeerTask sendVideoToPeerTask;
    private Gson gson = new Gson();
    private boolean isRecording;
    private SSLSocketFactory sslSocketFactory;
    private ProgressBar progressBar;
    private ImageView imageViewProgressBarBackground;
    private TextView textViewFileTransfer;
    private SurfaceView previewDisplay;
    private long clockDifferenceMs;
    private TextView peerUserNameTextView;
    private TextView recordingTimerTextView;
    private long recordingStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private RelativeLayout endSessionLayout;
    private NetworkStreamer previewStreamer;
    private VideoRecorder recorder;
    private BroadcastReceiver wifiBroadcastReceiver;
    private ImageButton switchCamerasButton;
    private RecordingMetadata localRecordingMetadata;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        progressBar = (ProgressBar) findViewById(R.id.progressBar_file_transfer);
        imageViewProgressBarBackground = (ImageView) findViewById(R.id.imageview_progressbar_background);
        textViewFileTransfer = (TextView) findViewById(R.id.textview_file_transfer_status);

        peerUserNameTextView = (TextView) findViewById(R.id.textview_peer_user_name);

        recordingTimerTextView = (TextView) findViewById(R.id.textview_recording_timer);

        initializeRecordingTimer();

        chameleonApplication = (ChameleonApplication) getApplication();

        sslSocketFactory = SSLUtil.getInitializedSSLSocketFactory(this);

        // Prepare camera preview
        chameleonApplication.preparePreview();

        chameleonApplication.getPreviewDisplayer().addOnPreparedCallback(new Runnable() {
            @Override
            public void run() {
                log.info("Preview displayer is ready to display a preview - " +
                        "adding one to the ConnectionEstablished activity");
                addCameraPreviewSurface();
                chameleonApplication.startPreview();
            }
        });


        chameleonApplication.startConnectionServerIfNotRunning();

        // Start listening for server events
        chameleonApplication.getServerEventListenerManager().addListener(this);

        // Retrieve peer info to start streaming
        Intent intent = getIntent();
        log.info("Intent = {}", intent);
        final PeerInfo peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);

        previewStreamer = new PreviewStreamer(chameleonApplication.getCameraFrameBuffer());

        // Start streaming preview from peer
        streamFromPeerTask = new StreamFromPeerTask(peerInfo.getIpAddress(), peerInfo.getPort());
        streamFromPeerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // Create recorder
        recorder = new MediaCodecRecorder(chameleonApplication, chameleonApplication.getCameraFrameBuffer());

        log.info("PeerInfo = {}", peerInfo);

        peerUserNameTextView.setText("Connected to " + peerInfo.getUserName());

        switchCamerasButton = (ImageButton) findViewById(R.id.button_switch_cameras);
        switchCamerasButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.debug("Toggling between cameras");
                chameleonApplication.getPreviewDisplayer().toggleFrontFacingCamera();
            }
        });

        final ImageButton recordSessionButton = (ImageButton) findViewById(R.id.button_record_session);
        recordSessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (isRecording) {
                    recordSessionButton.setImageResource(R.drawable.start_recording_button_enabled);

                    // Director should send message to crew to stop recording
                    if (PeerInfo.Role.CREW_MEMBER.equals(peerInfo.getRole())) {
                        PeerMessage peerMsg = PeerMessage.builder()
                                .type(PeerMessage.Type.STOP_RECORDING)
                                .build();
                        new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }

                    stopRecording();

                    isRecording = false;

                    // Give the user the option to retake the video or continue to merge
                    recordSessionButton.setEnabled(false);
                    endSessionLayout.setVisibility(View.VISIBLE);

                } else {
                    recordSessionButton.setImageResource(R.drawable.stop_recording_button_enabled);

                    // Director should send message to crew to start recording
                    if (PeerInfo.Role.CREW_MEMBER.equals(peerInfo.getRole())) {
                        PeerMessage peerMsg = PeerMessage.builder()
                                .type(PeerMessage.Type.START_RECORDING)
                                .build();
                        new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    }

                    startRecording();

                    isRecording = true;
                }
            }
        });

        if (PeerInfo.Role.CREW_MEMBER.equals(peerInfo.getRole())) {
            // I am the director. So, should be able to start/stop recording.
            recordSessionButton.setEnabled(true);
            recordSessionButton.setVisibility(View.VISIBLE);
        } else {
            chameleonApplication.tearDownWifiHotspot();
        }

        wifiBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive : intent = {}, current SSID = {}", intent.getAction(),
                        WifiUtil.getCurrentSSID(context));
            }
        };

        // Buttons for ending/continuing session
        endSessionLayout = (RelativeLayout) findViewById(R.id.relativeLayout_end_session);


        Button continueButton = (Button) findViewById(R.id.button_continue_session);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSessionLayout.setVisibility(View.INVISIBLE);

                receiveVideoFromPeerTask = new ReceiveVideoFromPeerTask(peerInfo);

                receiveVideoFromPeerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        Button retakeButton = (Button) findViewById(R.id.button_retake_video);
        retakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSessionLayout.setVisibility(View.INVISIBLE);
                recordSessionButton.setEnabled(true);
            }
        });
    }

    private void startRecording() {
        log.debug("Start recording event received!!");

        // Hide button to switch cameras
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switchCamerasButton.setVisibility(View.INVISIBLE);
            }
        });

        // Start recorder
        recorder.startRecording();
        log.debug("Video recording started");
        recordingStartTime = System.currentTimeMillis();
        timerHandler.postDelayed(timerRunnable, 500);
    }

    private void stopRecording() {

        //Stop recorder
        log.debug("Stop recording event received!!");
        localRecordingMetadata = recorder.stopRecording();
        log.debug("Video recording stopped");
        timerHandler.removeCallbacks(timerRunnable);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                recordingTimerTextView.setVisibility(View.INVISIBLE);

                // Show button to switch cameras
                switchCamerasButton.setVisibility(View.VISIBLE);
            }
        });


    }

    private void initializeRecordingTimer() {
        //runs without a timer by reposting this handler at the end of the runnable
        timerHandler = new Handler();

        timerRunnable = new Runnable() {

            @Override
            public void run() {
                long millis = System.currentTimeMillis() - recordingStartTime;
                int seconds = (int) (millis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;

                recordingTimerTextView.setVisibility(View.VISIBLE);
                recordingTimerTextView.setText(String.format("%02d:%02d", minutes, seconds));

                timerHandler.postDelayed(this, 500);
            }
        };
    }

    private void addCameraPreviewSurface() {
        log.debug("Creating surfaceView on thread {}", Thread.currentThread());

        try {
            ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();
            RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_session_preview);
            previewDisplay = chameleonApplication.createPreviewDisplay();
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            layout.addView(previewDisplay, layoutParams);
        } catch (Exception e) {
            log.error("Failed to add camera preview surface", e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        log.info("onPause invoked!");
        if (isFinishing()) {
            cleanup();
        }

        // Unregister wifi receiver
        chameleonApplication.unregisterReceiverSafely(wifiBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register to listen for wifi events
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        wifiFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(wifiBroadcastReceiver, wifiFilter);
    }

    private void cleanup() {
        log.info("Cleanup invoked!");

        if (previewStreamer != null) {
            previewStreamer.stopStreaming();
            previewStreamer = null;
        }

        chameleonApplication.getServerEventListenerManager().removeListener(this);

        chameleonApplication.tearDownWifiHotspot();

        if (streamFromPeerTask != null) {
            streamFromPeerTask.cancel(true);
            streamFromPeerTask = null;
        }
        if (receiveVideoFromPeerTask != null) {
            receiveVideoFromPeerTask.cancel(true);
            receiveVideoFromPeerTask = null;
        }
        if (sendVideoToPeerTask != null) {
            sendVideoToPeerTask.cancel(true);
            sendVideoToPeerTask = null;
        }

        timerHandler.removeCallbacks(timerRunnable);
        chameleonApplication.stopPreview();

        chameleonApplication.stopConnectionServer();

        wifiBroadcastReceiver = null;
    }

    @Override
    public void onClientRequest(Socket clientSocket, PeerMessage messageFromClient) {

        log.info("Received message from client = {}", messageFromClient.getType());

        switch (messageFromClient.getType()) {
            case SEND_STREAM:
                startStreamingToPeer(clientSocket);
                break;
            case TERMINATE_SESSION:
                break;
            case SESSION_HEARTBEAT:
                break;
            case START_RECORDING:
                processStartRecordingMessage(clientSocket);
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

    public void startStreamingToPeer(Socket peerSocket) {
        if (previewStreamer != null) {
            try {
                log.info("Ready to send stream to peer! dest os = {}", peerSocket.getOutputStream());
                if (!previewStreamer.isStreaming()) {
                    log.info("Starting stream..");
                    previewStreamer.startStreaming(peerSocket.getOutputStream());
                } else {
                    log.info("Already streaming.. ignoring");
                }
            } catch (IOException e) {
                log.error("Failed to start streaming", e);
            }
        }
    }

    private void processStartRecordingMessage(final Socket clientSocket) {
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

            startRecording();

        } catch (IOException e) {
            log.error("Failed to send START_RECORDING_RESPONSE", e);
        }
    }

    private void sendRecordedVideo(final Socket clientSocket) {
        log.debug("Received message to send recorded video!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendVideoToPeerTask = new SendVideoToPeerTask(
                        clientSocket,
                        localRecordingMetadata);
                sendVideoToPeerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });
    }

    @AllArgsConstructor
    class SendMessageToPeerTask extends AsyncTask<Void, Void, Void> {
        private PeerMessage peerMsg;
        private InetAddress peerIp;
        private int port;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Wait till we can reach the remote host. May take time to refresh ARP cache
                if (!IpUtil.isIpReachable(peerIp)) {
                    log.warn("Peer = {} not reachable. Unable to send message = {}", peerIp, peerMsg);
                    return null;
                }

                SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                socket.setEnabledProtocols(new String[]{"TLSv1.2"});

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(socket.getOutputStream());
                String serializedMsgToSend = gson.toJson(peerMsg);
                log.debug("Sending msg = {}", serializedMsgToSend);
                long localCurrentTimeMsBeforeSendingRequest = System.currentTimeMillis();
                pw.println(serializedMsgToSend);
                pw.close();
                if (PeerMessage.Type.START_RECORDING.equals(peerMsg.getType())) {
                    String recvMsg = bufferedReader.readLine();
                    long localCurrentTimeMsAfterReceivingResponse = System.currentTimeMillis();
                    if (recvMsg != null) {
                        PeerMessage message = gson.fromJson(recvMsg, PeerMessage.class);
                        StartRecordingResponse response =
                                gson.fromJson(message.getContents(), StartRecordingResponse.class);
                        long networkLatencyMs = (localCurrentTimeMsAfterReceivingResponse - localCurrentTimeMsBeforeSendingRequest) / 2;
                        clockDifferenceMs = response.getCurrentTimeMillis() -
                                localCurrentTimeMsAfterReceivingResponse +
                                networkLatencyMs;
                        log.debug("Local current time before sending request = {}", localCurrentTimeMsBeforeSendingRequest);
                        log.debug("Remote current time = {}", response.getCurrentTimeMillis());
                        log.debug("Local current time after receiving response = {}", localCurrentTimeMsAfterReceivingResponse);
                        log.debug("Clock difference ms = {}", clockDifferenceMs);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to send message = " + peerMsg + " to peer", e);
            }
            return null;
        }

    }

    @RequiredArgsConstructor
    class ReceiveVideoFromPeerTask extends AsyncTask<Void, Integer, Void> {
        @NonNull
        private PeerInfo peerInfo;

        private Long remoteRecordingStartTimeMillis;
        private boolean remoteRecordingHorizontallyFlipped;
        private long remoteClockAheadOfLocalClockMillis = 0L;
        private File remoteVideoFile;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar("Receiving video..");
        }

        @Override
        protected Void doInBackground(Void... params) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {

                // Wait till we can reach the remote host. May take time to refresh ARP cache
                if (!IpUtil.isIpReachable(peerInfo.getIpAddress())) {
                    log.warn("Peer = {} not reachable! Unable to receive video", peerInfo.getIpAddress().getHostAddress());
                    return null;
                }

                SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerInfo.getIpAddress(), peerInfo.getPort());
                socket.setEnabledProtocols(new String[]{"TLSv1.2"});
                log.debug("SSL client enabled protocols {}", Arrays.toString(socket.getEnabledProtocols()));
                log.debug("SSL client enabled cipher suites {}", Arrays.toString(socket.getEnabledCipherSuites()));

                // Request recorded file from peer
                PeerMessage peerMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO)
                        .build();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw = new PrintWriter(socket.getOutputStream());
                String serializedMessageToSend = gson.toJson(peerMsg);
                log.debug("Sending msg = {}", serializedMessageToSend);
                long localCurrentTimeMsBeforeSendingRequest = System.currentTimeMillis();
                pw.println(serializedMessageToSend);
                pw.close();

                log.debug("Waiting for SEND_RECORDED_VIDEO_RESPONSE");
                // Receive recorded file size from peer
                long fileSizeBytes = -1;
                String recvMsg = bufferedReader.readLine();
                long localCurrentTimeMsAfterReceivingResponse = System.currentTimeMillis();
                if (recvMsg != null) {
                    log.debug("Message received = {}", recvMsg);
                    PeerMessage message = gson.fromJson(recvMsg, PeerMessage.class);
                    if (PeerMessage.Type.SEND_RECORDED_VIDEO_RESPONSE.equals(message.getType())) {
                        SendRecordedVideoResponse response =
                                gson.fromJson(message.getContents(), SendRecordedVideoResponse.class);
                        if (response != null) {
                            fileSizeBytes = response.getFileSizeBytes();
                            remoteRecordingStartTimeMillis = response.getRecordingStartTimeMillis();
                            remoteRecordingHorizontallyFlipped = response.isRecordingHorizontallyFlipped();
                            log.debug("Local current time before sending request = {}", localCurrentTimeMsBeforeSendingRequest);
                            log.debug("Remote current time = {}", response.getCurrentTimeMillis());
                            log.debug("Local current time after receiving response = {}", localCurrentTimeMsAfterReceivingResponse);
                            long networkCommunicationLatencyMs = (localCurrentTimeMsAfterReceivingResponse -
                                    localCurrentTimeMsBeforeSendingRequest) / 2;
                            log.debug("network communication latency = {} ms", networkCommunicationLatencyMs);
                            remoteClockAheadOfLocalClockMillis = response.getCurrentTimeMillis() -
                                    localCurrentTimeMsAfterReceivingResponse +
                                    networkCommunicationLatencyMs;
                        }
                    }
                }

                int totalBytesReceived = 0;

                remoteVideoFile = chameleonApplication.getOutputMediaFile(ChameleonApplication.MEDIA_TYPE_VIDEO);
                if (remoteVideoFile.exists()) {
                    remoteVideoFile.delete();
                }
                if (remoteVideoFile.createNewFile()) {
                    outputStream = new BufferedOutputStream(new FileOutputStream(remoteVideoFile));

                    final byte[] buffer = new byte[ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES];
                    inputStream = new BufferedInputStream(socket.getInputStream());
                    int bytesRead = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        log.debug("Receiving recorded file from peer.. bytes = {}", bytesRead);
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesReceived += bytesRead;
                        publishProgress((int) (100 * totalBytesReceived / fileSizeBytes));
                    }
                    log.debug("Successfully received recorded video!");
                }

                log.info("Done receiving video from peer!");
            } catch (IOException e) {
                log.error("Failed to receive recorded video..", e);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (IOException e) {
                    log.error("Failed to close stream when receiving recorded video..", e);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            log.debug("Updating progress bar.. {}", values[0]);
            progressBar.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            hideProgressBar();

            // Adjust recording start time for remote recording to account for
            // clock difference between two devices
            long clockAdjustmentMs = (remoteClockAheadOfLocalClockMillis + clockDifferenceMs) / 2;
            remoteRecordingStartTimeMillis -= clockAdjustmentMs;

            log.debug("Adjusted remote recording start time millis by {} ms", clockAdjustmentMs);
            log.debug("Local recording start time = {} ms", localRecordingMetadata.getStartTimeMillis());
            log.debug("Remote recording start time = {} ms", remoteRecordingStartTimeMillis);

            RecordingMetadata remoteRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(remoteVideoFile.getAbsolutePath())
                    .startTimeMillis(remoteRecordingStartTimeMillis)
                    .horizontallyFlipped(remoteRecordingHorizontallyFlipped)
                    .videographer(peerInfo.getUserName())
                    .build();

            Intent intent = new Intent(getApplicationContext(), PreviewMergeActivity.class);
            intent.putExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY, gson.toJson(localRecordingMetadata));
            intent.putExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
            startActivity(intent);
        }

    }

    @RequiredArgsConstructor
    class SendVideoToPeerTask extends AsyncTask<Void, Integer, Void> {

        @NonNull
        private final Socket clientSocket;
        @NonNull
        private final RecordingMetadata recordingMetadata;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar("Sending video..");
        }

        @Override
        protected Void doInBackground(Void... params) {

            OutputStream outputStream = null;
            InputStream inputStream = null;
            File fileToSend = new File(recordingMetadata.getAbsoluteFilePath());
            Long fileSizeBytes = fileToSend.length();

            try {
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                SendRecordedVideoResponse response = SendRecordedVideoResponse.builder()
                        .fileSizeBytes(fileSizeBytes)
                        .recordingStartTimeMillis(recordingMetadata.getStartTimeMillis())
                        .recordingHorizontallyFlipped(recordingMetadata.isHorizontallyFlipped())
                        .currentTimeMillis(System.currentTimeMillis()).build();
                PeerMessage responseMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO_RESPONSE)
                        .contents(gson.toJson(response)).build();
                log.debug("Sending file size msg = {}", gson.toJson(responseMsg));
                pw.println(gson.toJson(responseMsg));
                pw.close();

                // Get recording time
                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(fileToSend.getAbsolutePath());
                log.debug("File recording time = {}", metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
                clientSocket.setSendBufferSize(ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES);
                clientSocket.setReceiveBufferSize(ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES);
                outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
                inputStream = new BufferedInputStream(new FileInputStream(fileToSend));
                byte[] buffer = new byte[ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES];
                int bytesRead = 0;
                int totalBytesSent = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    log.debug("Sending recorded file.. bytes = {}", bytesRead);
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesSent += bytesRead;
                    publishProgress((int) (100 * totalBytesSent / fileSizeBytes));
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

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            log.debug("Updating progress bar.. {}", values[0]);
            progressBar.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            hideProgressBar();

            Toast.makeText(getApplicationContext(), "Session completed!", Toast.LENGTH_LONG).show();

            //Re-use MainActivity instance if already present. If not, create new instance.
            Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
            openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(openMainActivity);
        }
    }

    private void showProgressBar(final String progressBarText) {
        int color = 0xffffa500;
        //progressBar.setProgressDrawable(getResources().getDrawable(R.drawable.circular_progress_bar));
        progressBar.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        //progressBar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        //progressBar.setIndeterminate(false);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        imageViewProgressBarBackground.setVisibility(View.VISIBLE);
        textViewFileTransfer.setText(progressBarText);
        textViewFileTransfer.setVisibility(View.VISIBLE);
        log.debug("Progress bar set to be visible = {}", progressBar.getVisibility());
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.INVISIBLE);
        imageViewProgressBarBackground.setVisibility(View.INVISIBLE);
        textViewFileTransfer.setVisibility(View.INVISIBLE);
    }

    @AllArgsConstructor
    class StreamFromPeerTask extends AsyncTask<Void, Void, Void> {
        private static final int STREAM_MESSAGE_INTERVAL_MSEC = 5000;
        private static final int MAX_ATTEMPTS_TO_STREAM = 3;

        private InetAddress peerIp;
        private int port;

        @Override
        protected Void doInBackground(Void... params) {
            while (!isCancelled()) {

                try {
                    log.info("Connect to remote host invoked Thread = {}", Thread.currentThread());

                    if (!IpUtil.isIpReachable(peerIp)) {
                        log.warn("Peer IP = {} not reachable. Unable to receive stream!", peerIp.getHostAddress());
                        continue;
                    }

                    SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                    socket.setEnabledProtocols(new String[]{"TLSv1.2"});

                    final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);

                    final byte[] buffer = new byte[ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE_BYTES];
                    InputStream inputStream = socket.getInputStream();
                    final Matrix matrix = new Matrix();

                    PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

                    // Send SEND_STREAM message periodically
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.SEND_STREAM)
                            .contents("abc")
                            .senderUserName(getUserName()) //Send this user's name
                            .build();
                    log.info("Sending msg = {}", gson.toJson(peerMsg));

                    pw.println(gson.toJson(peerMsg));

                    int attemptsLeft = MAX_ATTEMPTS_TO_STREAM;

                    while (!isCancelled() && attemptsLeft > 0) {

                        boolean streamImageReceived = false;
                        BufferedReader bufferedReader = new BufferedReader(
                                new InputStreamReader(socket.getInputStream()));
                        String recvMsg = bufferedReader.readLine();
                        if (recvMsg != null) {
                            StreamMetadata streamMetadata = gson.fromJson(recvMsg, StreamMetadata.class);
                            matrix.setScale(1, streamMetadata.isHorizontallyFlipped() ? -1 : 1);
                            matrix.postRotate(90);
                            final int bytesRead = inputStream.read(buffer);
                            if (bytesRead != -1) {
                                streamImageReceived = true;
                                log.debug("Received preview image from remote server bytes = " + bytesRead);
                                final WeakReference<Bitmap> bmpRef = new WeakReference<Bitmap>(
                                        BitmapFactory.decodeByteArray(buffer, 0, bytesRead));
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (imageView != null && bmpRef.get() != null) {
                                            // TODO : Rotate image without using bitmap
                                            Bitmap rotatedBitmap = Bitmap.createBitmap(
                                                    bmpRef.get(), 0, 0, bmpRef.get().getWidth(),
                                                    bmpRef.get().getHeight(), matrix, true);
                                            imageView.setImageBitmap(rotatedBitmap);
                                            imageView.setVisibility(View.VISIBLE);
                                        }
                                    }
                                });
                            }
                        }
                        if (!streamImageReceived) {
                            attemptsLeft--;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to stream from peer. Retrying again..", e);
                }
            }
            log.debug("Finishing StreamFromPeerTask..");
            return null;
        }
    }

    private String getUserName() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(
                this.chameleonApplication.getApplicationContext());
        return settings.getString(getString(R.string.pref_user_name_key), "");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_established, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
