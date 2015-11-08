package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import com.trioscope.chameleon.metrics.MetricNames;
import com.trioscope.chameleon.record.MediaCodecRecorder;
import com.trioscope.chameleon.record.VideoRecorder;
import com.trioscope.chameleon.stream.NetworkStreamer;
import com.trioscope.chameleon.stream.PreviewStreamer;
import com.trioscope.chameleon.stream.ServerEventListener;
import com.trioscope.chameleon.types.PeerMessage;
import com.trioscope.chameleon.types.SendRecordedVideoResponse;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.network.IpUtil;
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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    public static final String PEER_CERTIFICATE_KEY = "PEER_CERTIFICATE";
    private static final long MAX_HEARTBEAT_MESSAGE_INTERVAL_MS = 10000;
    private static final long HEARTBEAT_MESSAGE_CHECK_INTERVAL_MS = 5000;
    private static final long HEARTBEAT_MESSAGE_CHECK_INITIAL_DELAY_MS = 15000;

    boolean doubleBackToExitPressedOnce = false;
    private ChameleonApplication chameleonApplication;
    private StreamFromPeerTask streamFromPeerTask;
    private ReceiveVideoFromPeerTask receiveVideoFromPeerTask;
    private SendVideoToPeerTask sendVideoToPeerTask;
    private Gson gson = new Gson();
    private boolean isRecording;
    private SSLSocketFactory sslSocketFactory;
    private ProgressBar progressBar;
    private ProgressBar progressBarCrewNotification;
    private ImageView imageViewProgressBarBackground;
    private TextView textViewFileTransfer;
    private SurfaceView previewDisplay;
    private TextView peerUserNameTextView;
    private TextView recordingTimerTextView;
    private long recordingStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private RelativeLayout endSessionLayout;
    private RelativeLayout sessionActionsLayout;
    private NetworkStreamer previewStreamer;
    private VideoRecorder recorder;
    private ImageButton switchCamerasButton;
    private RecordingMetadata localRecordingMetadata;
    private PeerInfo peerInfo;
    private volatile long latestPeerHeartbeatMessageTimeMs;
    private Handler heartbeatCheckHandler;
    private Runnable heartbeatCheckRunnable;
    private List<Long> clockDifferenceMeasurementsMillis = new ArrayList<>();
    private TextView textViewCrewNotification;

    private Executor asyncTaskThreadPool = Executors.newFixedThreadPool(4);

    //Saves the starttime of when there is only streaming & no recording, this is used for metric-ing purpose.
    private long preRecordingStartTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Retrieve peer info to start streaming
        Intent intent = getIntent();
        log.info("Intent = {}", intent);
        peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);

        progressBar = (ProgressBar) findViewById(R.id.progressBar_file_transfer);
        imageViewProgressBarBackground = (ImageView) findViewById(R.id.imageview_progressbar_background);
        textViewFileTransfer = (TextView) findViewById(R.id.textview_file_transfer_status);

        peerUserNameTextView = (TextView) findViewById(R.id.textview_peer_user_name);

        recordingTimerTextView = (TextView) findViewById(R.id.textview_recording_timer);

        initializeRecordingTimer();

        initializeHeartbeatCheckTimer();

        preRecordingStartTime = System.currentTimeMillis();

        chameleonApplication = (ChameleonApplication) getApplication();

        X509Certificate trustedCertificate = SSLUtil.deserializeByteArrayToCertificate(
                intent.getByteArrayExtra(PEER_CERTIFICATE_KEY));

        sslSocketFactory = SSLUtil.createSSLSocketFactory(trustedCertificate);

        // Crew should restart server so that new certificate can be generated
        if (!isDirector(peerInfo)) {
            new AsyncTask<Void, Void, X509Certificate>() {

                @Override
                protected X509Certificate doInBackground(Void... params) {
                    return chameleonApplication.stopAndStartConnectionServer();
                }

                @Override
                protected void onPostExecute(X509Certificate certificate) {
                    super.onPostExecute(certificate);

                    // Crew member needs to send its certificate to peer so it can be used
                    // as trusted certificate to enable director to connect to crew
                    sendPeerMessage(PeerMessage.Type.START_SESSION,
                            gson.toJson(SSLUtil.serializeCertificateToByteArray(certificate)));
                }
            }.executeOnExecutor(asyncTaskThreadPool);
        }


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

        // Start listening for server events
        chameleonApplication.getServerEventListenerManager().addListener(this);

        previewStreamer = new PreviewStreamer(chameleonApplication.getCameraFrameBuffer());

        // Start streaming preview from peer
        streamFromPeerTask = new StreamFromPeerTask(peerInfo.getIpAddress(), peerInfo.getPort());
        streamFromPeerTask.executeOnExecutor(asyncTaskThreadPool);

        // Create recorder
        recorder = new MediaCodecRecorder(chameleonApplication, chameleonApplication.getCameraFrameBuffer());

        log.info("PeerInfo = {}", peerInfo);

        if(isDirector(peerInfo)) {
            peerUserNameTextView.setText("Connected to " + peerInfo.getUserName());
        } else {
            peerUserNameTextView.setText("Directed by " + peerInfo.getUserName());
        }

        switchCamerasButton = (ImageButton) findViewById(R.id.button_switch_cameras);
        switchCamerasButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                log.debug("Toggling between cameras");
                chameleonApplication.getPreviewDisplayer().toggleFrontFacingCamera();
            }
        });

        final ImageButton recordButton = (ImageButton) findViewById(R.id.button_record_session);
        recordButton.setOnClickListener(new View.OnClickListener() {

            private long startRecordingTime;

            @Override
            public void onClick(View view) {

                if (isRecording) {
                    recordButton.setImageResource(R.drawable.start_recording_button_enabled);

                    // Director should send message to crew to stop recording
                    if (isDirector(peerInfo)) {
                        sendPeerMessage(PeerMessage.Type.STOP_RECORDING, true);

                        long endRecordingTime = System.currentTimeMillis();

                        ChameleonApplication.getMetrics().sendTime(
                                MetricNames.Category.VIDEO.getName(),
                                MetricNames.Label.RECORDING_DURATION.getName(),
                                (endRecordingTime - startRecordingTime));

                        log.info("Duration of recording is {} ms", (endRecordingTime - startRecordingTime));
                    }

                    stopRecording();

                    isRecording = false;

                    // Give the user the option to retake the video or continue to merge
                    sessionActionsLayout.setVisibility(View.INVISIBLE);

                    endSessionLayout.setVisibility(View.VISIBLE);

                } else {
                    recordButton.setImageResource(R.drawable.stop_recording_button_enabled);

                    //store start time for metrics
                    startRecordingTime = System.currentTimeMillis();

                    // Director should send message to crew to start recording
                    if (isDirector(peerInfo)) {
                        sendPeerMessage(PeerMessage.Type.START_RECORDING, true);

                        //emit metrics
                        ChameleonApplication.getMetrics().sendTime(
                                MetricNames.Category.VIDEO.getName(),
                                MetricNames.Label.STREAM_ONLY_DURATION.getName(),
                                (startRecordingTime - preRecordingStartTime));

                        log.info("Duration of stream only session is {} ms", (startRecordingTime - preRecordingStartTime));
                    }

                    startRecording();

                    isRecording = true;


                }
            }
        });

        if (isDirector(peerInfo)) {
            // I am the director. So, should be able to start/stop recording.
            recordButton.setEnabled(true);
            recordButton.setVisibility(View.VISIBLE);
        } else {
            chameleonApplication.tearDownWifiHotspot();
        }

        sessionActionsLayout = (RelativeLayout) findViewById(R.id.relativeLayout_session_actions);

        // Buttons for ending/continuing session
        endSessionLayout = (RelativeLayout) findViewById(R.id.relativeLayout_end_session);


        Button continueButton = (Button) findViewById(R.id.button_continue_session);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSessionLayout.setVisibility(View.INVISIBLE);

                receiveVideoFromPeerTask = new ReceiveVideoFromPeerTask(peerInfo);
                receiveVideoFromPeerTask.executeOnExecutor(asyncTaskThreadPool);
            }
        });

        Button retakeButton = (Button) findViewById(R.id.button_retake_video);
        retakeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endSessionLayout.setVisibility(View.INVISIBLE);
                sessionActionsLayout.setVisibility(View.VISIBLE);

                // File will be deleted during temp directory cleanup
                localRecordingMetadata = null;

                sendPeerMessage(PeerMessage.Type.RETAKE_SESSION, false);

                preRecordingStartTime = System.currentTimeMillis();
            }
        });

        ImageButton disconnectButton = (ImageButton) findViewById(R.id.button_disconnect);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendPeerMessage(PeerMessage.Type.TERMINATE_SESSION, false);
                terminateSession("Terminating session..");
            }
        });

        progressBarCrewNotification = (ProgressBar) findViewById(R.id.progressbar_crew_notification);

        textViewCrewNotification = (TextView) findViewById(R.id.textview_crew_notification);
    }

    private void sendPeerMessage(
            final PeerMessage.Type msgType,
            final Socket peerSocket) {
        PeerMessage msg = PeerMessage.builder()
                .type(msgType).senderUserName(getUserName())
                .sendTimeMillis(System.currentTimeMillis())
                .build();
        sendPeerMessage(msg, peerSocket, false);
    }

    private void sendPeerMessage(
            final PeerMessage.Type msgType,
            final boolean shouldWaitForResponse) {
        PeerMessage msg = PeerMessage.builder()
                .type(msgType)
                .senderUserName(getUserName())
                .sendTimeMillis(System.currentTimeMillis())
                .build();
        sendPeerMessage(msg, null, shouldWaitForResponse);
    }

    private void sendPeerMessage(
            final PeerMessage.Type msgType,
            final String msgContents) {
        PeerMessage msg = PeerMessage.builder()
                .type(msgType)
                .contents(msgContents)
                .senderUserName(getUserName())
                .sendTimeMillis(System.currentTimeMillis())
                .build();
        sendPeerMessage(msg, null, false);
    }

    private void sendPeerMessage(
            final PeerMessage msg,
            final Socket peerSocket,
            final boolean shouldWaitForResponse) {
        new SendMessageToPeerTask(msg, peerInfo, peerSocket, shouldWaitForResponse)
                .executeOnExecutor(asyncTaskThreadPool);
    }

    private static boolean isDirector(final PeerInfo peerInfo) {
        return PeerInfo.Role.CREW_MEMBER.equals(peerInfo.getRole());
    }

    private void startRecording() {
        log.debug("Start recording event received!!");

        // Hide button to switch cameras
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switchCamerasButton.setVisibility(View.INVISIBLE);

                //Do this for crew
                if(!isDirector(peerInfo)) {
                    showCrewNotificationProgressBar("Lights,\nCamera,\nAction!");

                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            hideCrewNotificationProgressBar();
                        }
                    }, 1200);// This value needs to be the same as indeterminateDuration defined in xml for this progress bar
                }
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

                if (!isDirector(peerInfo)) {

                    showCrewNotificationProgressBar("Cut!\nWaiting for\n" + peerInfo.getUserName() + "...");
                }
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

                // delay needs to be less than a second to ensure timer has second-precision.
                timerHandler.postDelayed(this, 500);
            }
        };
    }

    private void initializeHeartbeatCheckTimer() {
        //runs without a timer by reposting this handler at the end of the runnable
        heartbeatCheckHandler = new Handler();

        heartbeatCheckRunnable = new Runnable() {

            @Override
            public void run() {
                // Check if we have received heartbeat message from peer recently.
                if (System.currentTimeMillis() - latestPeerHeartbeatMessageTimeMs
                        > MAX_HEARTBEAT_MESSAGE_INTERVAL_MS) {
                    terminateSession(peerInfo.getUserName() + " unreachable. Ending session..");
                    heartbeatCheckHandler.removeCallbacks(this);
                } else {
                    heartbeatCheckHandler.postDelayed(this, HEARTBEAT_MESSAGE_CHECK_INTERVAL_MS);
                }
            }
        };

        latestPeerHeartbeatMessageTimeMs = System.currentTimeMillis();

        // Start check after some initial delay to give peer time to begin session
        heartbeatCheckHandler.postDelayed(heartbeatCheckRunnable,
                HEARTBEAT_MESSAGE_CHECK_INITIAL_DELAY_MS);
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
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        log.info("Memory level = {}", level);
    }

    @Override
    protected void onPause() {
        super.onPause();
        log.info("onPause invoked!");
        if (isFinishing()) {
            cleanup();
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                doubleBackToExitPressedOnce = false;
            }
        }, 3000);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        log.info("User is leaving! Finishing activity");
        //finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

        if (heartbeatCheckHandler != null) {
            heartbeatCheckHandler.removeCallbacks(heartbeatCheckRunnable);
            heartbeatCheckHandler = null;
        }

        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler = null;
        }

        chameleonApplication.stopPreview();

        chameleonApplication.stopConnectionServer();
    }

    @Override
    public void onClientRequest(Socket clientSocket, PeerMessage messageFromClient) {

        log.info("Received message from client = {}", messageFromClient.getType());
        switch (messageFromClient.getType()) {
            case SEND_STREAM:
                startStreamingToPeer(clientSocket);
                break;
            case TERMINATE_SESSION:
                terminateSession(peerInfo.getUserName() + " terminated session.");
                break;
            case START_RECORDING:
                processStartRecordingMessage(clientSocket);
                break;
            case STOP_RECORDING:
                processStopRecordingMessage(clientSocket);
                break;
            case SEND_RECORDED_VIDEO:
                sendRecordedVideo(clientSocket);
                break;
            case RETAKE_SESSION:
                processRetakeSessionMessage();
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

    private void terminateSession(final String toastMsg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),
                        toastMsg,
                        Toast.LENGTH_LONG).show();

                // Finish activity after some delay
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openMainActivity();
                    }
                }, 2000);
            }
        });
    }

    private void processStartRecordingMessage(final Socket clientSocket) {
        log.debug("Received message to start recording!");
        startRecording();
        sendPeerMessage(PeerMessage.Type.START_RECORDING_RESPONSE, clientSocket);
    }

    private void processStopRecordingMessage(final Socket clientSocket) {
        log.debug("Received message to start recording!");
        stopRecording();
        sendPeerMessage(PeerMessage.Type.STOP_RECORDING_RESPONSE, clientSocket);
    }

    private void sendRecordedVideo(final Socket clientSocket) {
        log.debug("Received message to send recorded video!");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                //Hide crewNotificationProgressBar prior to sending video
                hideCrewNotificationProgressBar();

                sendVideoToPeerTask = new SendVideoToPeerTask(
                        clientSocket,
                        localRecordingMetadata);
                sendVideoToPeerTask.executeOnExecutor(asyncTaskThreadPool);
            }
        });
    }

    private void processRetakeSessionMessage() {
        log.debug("Received message to Retake recording!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (!isDirector(peerInfo)) {

                    showCrewNotificationProgressBar("Retake!\nConnecting\nto\n"+ peerInfo.getUserName());

                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            hideCrewNotificationProgressBar();
                        }
                    }, 1200);
                }
            }
        });

    }

    @AllArgsConstructor
    @RequiredArgsConstructor
    class SendMessageToPeerTask extends AsyncTask<Void, Void, Void> {
        @NonNull
        private PeerMessage peerMsg;
        @NonNull
        private PeerInfo peerInfo;
        private Socket peerSocket;
        private boolean shouldWaitForResponse;

        @Override
        protected Void doInBackground(Void... params) {
            try {
                // Wait till we can reach the remote host. May take time to refresh ARP cache
                if (!IpUtil.isIpReachable(peerInfo.getIpAddress())) {
                    log.warn("Peer = {} not reachable. Unable to send message = {}", peerInfo.getIpAddress(), peerMsg);
                    return null;
                }

                if (peerSocket == null) {
                    peerSocket = sslSocketFactory.createSocket(peerInfo.getIpAddress(), peerInfo.getPort());
                }

                PrintWriter pw = new PrintWriter(peerSocket.getOutputStream());
                String serializedMsgToSend = gson.toJson(peerMsg);
                log.debug("Sending msg = {}", serializedMsgToSend);
                long localCurrentTimeMsBeforeSendingRequest = System.currentTimeMillis();
                pw.println(serializedMsgToSend);
                pw.close();

                // Compute clock difference from send and recv time of this message
                if (shouldWaitForResponse) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
                    String recvMsg = bufferedReader.readLine();
                    long localCurrentTimeMsAfterReceivingResponse = System.currentTimeMillis();
                    if (recvMsg != null) {
                        PeerMessage message = gson.fromJson(recvMsg, PeerMessage.class);
                        if (message != null && message.getSendTimeMillis() != -1) {
                            long networkLatencyMs = (localCurrentTimeMsAfterReceivingResponse -
                                    localCurrentTimeMsBeforeSendingRequest) / 2;
                            long clockDifferenceMs = message.getSendTimeMillis() -
                                    localCurrentTimeMsAfterReceivingResponse +
                                    networkLatencyMs;
                            clockDifferenceMeasurementsMillis.add(clockDifferenceMs);
                            log.debug("Local current time before sending request = {}",
                                    localCurrentTimeMsBeforeSendingRequest);
                            log.debug("Remote current time = {}", message.getSendTimeMillis());
                            log.debug("Local current time after receiving response = {}",
                                    localCurrentTimeMsAfterReceivingResponse);
                            log.debug("Clock difference ms = {}", clockDifferenceMs);
                        }
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
        private File remoteVideoFile;

        private long startTime;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            startTime = System.currentTimeMillis();
            showProgressBar("Receiving\nvideo");
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

                // Request recorded file from peer
                PeerMessage peerMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO)
                        .senderUserName(getUserName())
                        .sendTimeMillis(System.currentTimeMillis())
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
                            log.debug("Local current time before sending request = {}", localCurrentTimeMsBeforeSendingRequest);
                            log.debug("Remote current time = {}", message.getSendTimeMillis());
                            log.debug("Local current time after receiving response = {}", localCurrentTimeMsAfterReceivingResponse);
                            long networkCommunicationLatencyMs = (localCurrentTimeMsAfterReceivingResponse -
                                    localCurrentTimeMsBeforeSendingRequest) / 2;
                            log.debug("network communication latency = {} ms", networkCommunicationLatencyMs);
                            long clockDifferenceMs = message.getSendTimeMillis() -
                                    localCurrentTimeMsAfterReceivingResponse +
                                    networkCommunicationLatencyMs;
                            clockDifferenceMeasurementsMillis.add(clockDifferenceMs);
                        }
                    }
                }

                long totalBytesReceived = 0;

                remoteVideoFile = chameleonApplication.createVideoFile(true);
                if (remoteVideoFile.createNewFile()) {
                    outputStream = new BufferedOutputStream(new FileOutputStream(remoteVideoFile));

                    final byte[] buffer = new byte[ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES];
                    inputStream = new BufferedInputStream(socket.getInputStream());
                    int bytesRead = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesReceived += bytesRead;
                        int fileTransferPercentage = (int) (100 * totalBytesReceived / fileSizeBytes);
                        log.info("File transfer = {}%", fileTransferPercentage);
                        publishProgress(fileTransferPercentage);
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


            long endTime = System.currentTimeMillis();

            ChameleonApplication.getMetrics().sendTime(
                    MetricNames.Category.VIDEO.getName(),
                    MetricNames.Label.TIME_TO_TRANSFER_FILE.getName(),
                    (endTime - startTime));

            log.info("Time to transfer file is {} ms", (endTime - startTime));

            // Compute difference between two clock using multiple measurements
            log.debug("Number of clock difference measurements = {}", clockDifferenceMeasurementsMillis.size());
            long clockDifferenceMsSum = 0;
            for (long clockDifferenceMeasurementMs : clockDifferenceMeasurementsMillis) {
                clockDifferenceMsSum += clockDifferenceMeasurementMs;
            }
            long clockDifferenceMs = clockDifferenceMsSum / clockDifferenceMeasurementsMillis.size();

            // Adjust recording start time for remote recording to account for
            // clock difference between two devices
            remoteRecordingStartTimeMillis -= clockDifferenceMs;

            log.debug("Adjusted remote recording start time millis by {} ms", clockDifferenceMs);
            log.debug("Local recording start time = {} ms", localRecordingMetadata.getStartTimeMillis());
            log.debug("Remote recording start time = {} ms", remoteRecordingStartTimeMillis);

            RecordingMetadata remoteRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(remoteVideoFile.getAbsolutePath())
                    .startTimeMillis(remoteRecordingStartTimeMillis)
                    .videographer(peerInfo.getUserName())
                    .build();

            Intent intent = new Intent(getApplicationContext(), PreviewMergeActivity.class);
            intent.putExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY, gson.toJson(localRecordingMetadata));
            intent.putExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
            startActivity(intent);
            finish();
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
            showProgressBar("Sending\nvideo");
        }

        @Override
        protected Void doInBackground(Void... params) {

            OutputStream outputStream = null;
            InputStream inputStream = null;
            File fileToSend = new File(recordingMetadata.getAbsoluteFilePath());
            long fileSizeBytes = fileToSend.length();

            try {
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                SendRecordedVideoResponse response = SendRecordedVideoResponse.builder()
                        .version(SendRecordedVideoResponse.CURRENT_VERSION)
                        .fileSizeBytes(fileSizeBytes)
                        .recordingStartTimeMillis(recordingMetadata.getStartTimeMillis())
                        .build();
                PeerMessage responseMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO_RESPONSE)
                        .senderUserName(getUserName())
                        .contents(gson.toJson(response))
                        .sendTimeMillis(System.currentTimeMillis())
                        .build();
                log.debug("Sending file size msg = {}", gson.toJson(responseMsg));
                pw.println(gson.toJson(responseMsg));
                pw.close();

                clientSocket.setSendBufferSize(ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES);
                clientSocket.setReceiveBufferSize(ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES);
                outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
                inputStream = new BufferedInputStream(new FileInputStream(fileToSend));
                byte[] buffer = new byte[ChameleonApplication.SEND_RECEIVE_BUFFER_SIZE_BYTES];
                int bytesRead = 0;
                long totalBytesSent = 0;
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

            // Delete video since we have already sent it
            File videoFile = new File(recordingMetadata.getAbsoluteFilePath());
            videoFile.delete();

            openMainActivity();


        }
    }

    private void openMainActivity(){
        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        finish();
    }

    private void showProgressBar(final String progressBarText) {
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

    private void showCrewNotificationProgressBar(final String progressBarText) {
        textViewCrewNotification.setText(progressBarText);
        textViewCrewNotification.setVisibility(View.VISIBLE);
        imageViewProgressBarBackground.setVisibility(View.VISIBLE);
        progressBarCrewNotification.setVisibility(View.VISIBLE);
    }

    private void hideCrewNotificationProgressBar() {
        progressBarCrewNotification.setVisibility(View.INVISIBLE);
        imageViewProgressBarBackground.setVisibility(View.INVISIBLE);
        textViewCrewNotification.setVisibility(View.INVISIBLE);
    }

    @AllArgsConstructor
    class StreamFromPeerTask extends AsyncTask<Void, Void, Void> {
        private static final int STREAM_MESSAGE_INTERVAL_MSEC = 5000;
        private static final int MAX_ATTEMPTS_TO_STREAM = 3;

        private InetAddress peerIp;
        private int port;

        @Override
        protected Void doInBackground(Void... params) {
            final byte[] buffer = new byte[ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE_BYTES];

            while (!isCancelled()) {

                try {
                    log.info("Connect to remote host invoked Thread = {}", Thread.currentThread());

                    if (!IpUtil.isIpReachable(peerIp)) {
                        log.warn("Peer IP = {} not reachable. Unable to receive stream!", peerIp.getHostAddress());
                        continue;
                    }

                    SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);

                    final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);

                    InputStream inputStream = socket.getInputStream();
                    final Matrix matrix = new Matrix();

                    PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);

                    // Send SEND_STREAM message
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
                        final int bytesRead = inputStream.read(buffer);
                        if (bytesRead != -1) {
                            streamImageReceived = true;
                            latestPeerHeartbeatMessageTimeMs = System.currentTimeMillis();
                            log.debug("Received preview image from remote server bytes = " + bytesRead);
                            final WeakReference<Bitmap> bmpRef = new WeakReference<Bitmap>(
                                    BitmapFactory.decodeByteArray(buffer, 0, bytesRead));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (imageView != null && bmpRef.get() != null) {
                                        imageView.setImageBitmap(bmpRef.get());
                                    }
                                }
                            });
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
