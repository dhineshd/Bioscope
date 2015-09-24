package com.trioscope.chameleon.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
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
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.stream.messages.SendRecordedVideoResponse;
import com.trioscope.chameleon.stream.messages.StartRecordingResponse;
import com.trioscope.chameleon.stream.messages.StreamMetadata;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.types.SendVideoToPeerMetadata;
import com.trioscope.chameleon.types.SessionStatus;

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
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity extends EnableForegroundDispatchForNFCMessageActivity {
    public static final String LOCAL_RECORDING_METADATA_KEY = "LOCAL_RECORDING_METADATA";
    public static final String REMOTE_RECORDING_METADATA_KEY = "REMOTE_RECORDING_METADATA";
    public static final String CONNECTION_INFO_AS_JSON_EXTRA = "CONNECTION_INFO_AS_JSON_EXTRA";
    public static final String PEER_INFO = "PEER_INFO";
    private static final int MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE = 10000; // 10 secs
    private ChameleonApplication chameleonApplication;
    private StreamFromPeerTask streamFromPeerTask;
    private ReceiveVideoFromPeerTask receiveVideoFromPeerTask;
    private SendVideoToPeerTask sendVideoToPeerTask;
    private Gson gson = new Gson();
    private boolean isRecording;
    private SSLSocketFactory sslSocketFactory;
    private BroadcastReceiver recordEventReceiver;
    private ProgressBar progressBar;
    private SurfaceView previewDisplay;
    private long clockDifferenceMs;
    private TextView peerUserNameTextView;
    private TextView recordingTimerTextView;
    private long recordingStartTime;
    private Handler timerHandler;
    private Runnable timerRunnable;
    private RelativeLayout endSessionLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        progressBar = (ProgressBar) findViewById(R.id.progressBar_file_transfer);

        peerUserNameTextView = (TextView) findViewById(R.id.textview_peer_user_name);

        recordingTimerTextView = (TextView) findViewById(R.id.textview_recording_timer);

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
                recordingTimerTextView.setText(String.format("%d:%02d", minutes, seconds));

                timerHandler.postDelayed(this, 500);
            }
        };

        Handler sendVideoToPeerHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {

                if (msg.what == ChameleonApplication.SEND_VIDEO_TO_PEER_MESSAGE) {

                    SendVideoToPeerMetadata metadata = (SendVideoToPeerMetadata) msg.obj;
                    sendVideoToPeerTask = new SendVideoToPeerTask(
                            metadata.getClientSocket(),
                            metadata.getVideoFile(),
                            metadata.getRecordingStartTimeMillis(),
                            metadata.isRecordingHorizontallyFlipped());
                    sendVideoToPeerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    super.handleMessage(msg);
                }

            }
        };

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.getStreamListener().setSendVideoToPeerHandler(sendVideoToPeerHandler);

        sslSocketFactory = getInitializedSSLSocketFactory();

        recordEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ChameleonApplication.START_RECORDING_ACTION.equals(intent.getAction())) {
                    log.debug("Start recording event received!!");
                    // Start recording using MediaCodec method
                    chameleonApplication.getRecordingFrameListener().onStartRecording(System.currentTimeMillis());
                    log.debug("Video recording started");
                    recordingStartTime = System.currentTimeMillis();
                    timerHandler.postDelayed(timerRunnable, 500);
                } else if (ChameleonApplication.STOP_RECORDING_ACTION.equals(intent.getAction())) {
                    log.debug("Stop recording event received!!");
                    // Stop recording using MediaCodec method
                    chameleonApplication.getRecordingFrameListener().onStopRecording();
                    log.debug("Video recording stopped");
                    timerHandler.removeCallbacks(timerRunnable);
                    recordingTimerTextView.setVisibility(View.INVISIBLE);
                }
            }
        };

        // Prepare camera preview
        chameleonApplication.preparePreview();

        chameleonApplication.getPreviewDisplayer().addOnPreparedCallback(new Runnable() {
            @Override
            public void run() {
                log.debug("Preview displayer is ready to display a preview - adding one to the ConnectionEstablished activity");
                addCameraPreviewSurface();
                chameleonApplication.startPreview();
            }
        });

        // Retrieve peer info to start streaming
        Intent intent = getIntent();
        log.debug("Intent = {}", intent);
        final PeerInfo peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);

        // Start camera frame listener for streaming
        chameleonApplication.getCameraFrameBuffer().addListener(chameleonApplication.getStreamListener());

        // Start streaming the preview
        chameleonApplication.setSessionStatus(SessionStatus.CONNECTED);
        chameleonApplication.getStreamListener().setStreamingStarted(true);

        // Start camera frame listener for recording
        chameleonApplication.getCameraFrameBuffer().addListener(chameleonApplication.getRecordingFrameListener());

        log.debug("PeerInfo = {}", peerInfo);

        peerUserNameTextView.setText("Connected to " + peerInfo.getUserName());

        streamFromPeerTask = new StreamFromPeerTask(peerInfo.getIpAddress(), peerInfo.getPort());
        streamFromPeerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        final ImageView switchCamerasButton = (ImageView) findViewById(R.id.button_switch_cameras);
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

                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());

                if (isRecording) {
                    recordSessionButton.setImageResource(R.drawable.start_recording_button_enabled);

                    // Hide button to switch cameras
                    switchCamerasButton.setVisibility(View.VISIBLE);

                    // Sending message to peer to stop recording
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.STOP_RECORDING)
                            .build();
                    new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    // Stopping local video recording
                    manager.sendBroadcast(new Intent(ChameleonApplication.STOP_RECORDING_ACTION));
                    isRecording = false;

                    // Give the user the option to retake the video or continue to merge
                    recordSessionButton.setEnabled(false);
                    endSessionLayout.setVisibility(View.VISIBLE);

                } else {

                    recordSessionButton.setImageResource(R.drawable.stop_recording_button_enabled);

                    // Hide button to switch cameras
                    switchCamerasButton.setVisibility(View.INVISIBLE);

                    // Sending message to peer to start remote recording
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.START_RECORDING)
                            .build();
                    new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    // Starting local video recording
                    manager.sendBroadcast(new Intent(ChameleonApplication.START_RECORDING_ACTION));

                    isRecording = true;
                }
            }
        });
        recordSessionButton.setEnabled(false);
        if (!PeerInfo.Role.DIRECTOR.equals(peerInfo.getRole())) {
            // If peer is not director, then I am the director
            // So, should be able to start/stop recording.
            recordSessionButton.setEnabled(true);
        }

        // Buttons for ending/continuing session
        endSessionLayout = (RelativeLayout) findViewById(R.id.relativeLayout_end_session);

        Typeface appFontTypeface = Typeface.createFromAsset(getAssets(),
                ChameleonApplication.APP_FONT_LOCATION);

        Button continueButton = (Button) findViewById(R.id.button_continue_session);
        continueButton.setTypeface(appFontTypeface);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final File peerVideoFile = chameleonApplication.getOutputMediaFile("PeerVideo.mp4");

                endSessionLayout.setVisibility(View.INVISIBLE);

                receiveVideoFromPeerTask = new ReceiveVideoFromPeerTask(
                        chameleonApplication.getVideoFile(),
                        peerVideoFile,
                        peerInfo.getIpAddress(),
                        peerInfo.getPort());

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

    private void addCameraPreviewSurface() {
        log.debug("Creating surfaceView on thread {}", Thread.currentThread());

        try {
            ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();
            RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_session_preview);
            previewDisplay = chameleonApplication.createPreviewDisplay();
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            layout.addView(previewDisplay, layoutParams);
        } catch (Exception e) {
            log.error("Failed to add camera preview surface", e);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        log.debug("onPause invoked!");
        cleanup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register to listen for recording events
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ChameleonApplication.START_RECORDING_ACTION);
        filter.addAction(ChameleonApplication.STOP_RECORDING_ACTION);
        log.debug("Registering record event receiver");
        manager.registerReceiver(this.recordEventReceiver, filter);
    }


    @Override
    public void onBackPressed() {

        cleanup();

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        super.onBackPressed();
    }

    private void cleanup() {
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        log.debug("Unregistering record event receiver");
        manager.unregisterReceiver(this.recordEventReceiver);

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

        chameleonApplication.getStreamListener().setStreamingStarted(false);
        timerHandler.removeCallbacks(timerRunnable);
        chameleonApplication.stopPreview();

        // Stop camera frame listener for recording
        chameleonApplication.getCameraFrameBuffer().removeListener(chameleonApplication.getRecordingFrameListener());

        // Stop camera frame listener for streaming
        chameleonApplication.getCameraFrameBuffer().removeListener(chameleonApplication.getStreamListener());

        chameleonApplication.stopConnectionServer();
    }

    private SSLSocketFactory getInitializedSSLSocketFactory() {
        SSLSocketFactory sslSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            InputStream trustStoreInputStream = getApplicationContext().getResources().openRawResource(R.raw.chameleon_truststore);
            trustStore.load(trustStoreInputStream, "poiuyt".toCharArray());
            trustStoreInputStream.close();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            sslSocketFactory = ctx.getSocketFactory();
        } catch (IOException |
                NoSuchAlgorithmException |
                KeyStoreException |
                KeyManagementException |
                CertificateException e) {
            log.error("Failed to initialize SSL socket factory", e);
        }
        return sslSocketFactory;
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
                if (!isIpReachable(peerIp)) {
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
        private File localVideoFile;
        @NonNull
        private File remoteVideoFile;
        @NonNull
        private InetAddress peerIp;
        @NonNull
        private Integer port;
        private Long remoteRecordingStartTimeMillis;
        private boolean remoteRecordingHorizontallyFlipped;
        private long remoteClockAheadOfLocalClockMillis = 0L;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            log.debug("Progress bar set to be visible = {}", progressBar.getVisibility());
            progressBar.setProgress(0);
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {

                // Wait till we can reach the remote host. May take time to refresh ARP cache
                if (!isIpReachable(peerIp)) {
                    log.warn("Peer = {} not reachable! Unable to receive video", peerIp.getHostAddress());
                    return null;
                }

                SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
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

                // TODO Generate filename based on sessionId
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

            chameleonApplication.tearDownWifiHotspot();

            chameleonApplication.stopConnectionServer();

            // Stop receiving stream
            if (streamFromPeerTask != null) {
                streamFromPeerTask.cancel(true);
                streamFromPeerTask = null;
            }

            // Stop sending stream
            chameleonApplication.getStreamListener().terminateSession();

            // Sending message to terminate session
            PeerMessage terminateSessionMsg = PeerMessage.builder()
                    .type(PeerMessage.Type.TERMINATE_SESSION).build();
            new SendMessageToPeerTask(terminateSessionMsg, peerIp, port)
                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

            progressBar.setVisibility(View.INVISIBLE);

            // Adjust recording start time for remote recording to account for
            // clock difference between two devices
            long clockAdjustmentMs = (remoteClockAheadOfLocalClockMillis + clockDifferenceMs) / 2;
            remoteRecordingStartTimeMillis -= clockAdjustmentMs;

            log.debug("Adjusted remote recording start time millis by {} ms", clockAdjustmentMs);
            log.debug("Local recording start time = {} ms", chameleonApplication.getRecordingStartTimeMillis());
            log.debug("Remote recording start time = {} ms", remoteRecordingStartTimeMillis);
            RecordingMetadata localRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(localVideoFile.getAbsolutePath())
                    .startTimeMillis(chameleonApplication.getRecordingStartTimeMillis())
                    .horizontallyFlipped(chameleonApplication.isRecordingHorizontallyFlipped())
                    .build();
            RecordingMetadata remoteRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(remoteVideoFile.getAbsolutePath())
                    .startTimeMillis(remoteRecordingStartTimeMillis)
                    .horizontallyFlipped(remoteRecordingHorizontallyFlipped)
                    .build();

            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(localVideoFile.getAbsolutePath());
            log.debug("Local recording create time metadata = {}",
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            metadataRetriever.setDataSource(remoteVideoFile.getAbsolutePath());
            log.debug("Remote recording create time metadata = {}",
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            log.debug("Local filename = {}", localVideoFile.getName());
            log.debug("Remote filename = {}", remoteVideoFile.getName());

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
        private final File fileToSend;
        @NonNull
        private final Long recordingStartTimeMillis;
        @NonNull
        private final boolean recordingHorizontallyFlipped;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            log.debug("Progress bar set to be visible = {}", progressBar.getVisibility());
            progressBar.setProgress(0);
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {

            OutputStream outputStream = null;
            InputStream inputStream = null;
            Long fileSizeBytes = fileToSend.length();

            try {
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream());
                SendRecordedVideoResponse response = SendRecordedVideoResponse.builder()
                        .fileSizeBytes(fileSizeBytes)
                        .recordingStartTimeMillis(recordingStartTimeMillis)
                        .recordingHorizontallyFlipped(recordingHorizontallyFlipped)
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
                clientSocket.setSendBufferSize(65536);
                clientSocket.setReceiveBufferSize(65536);
                outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
                inputStream = new BufferedInputStream(new FileInputStream(fileToSend));
                byte[] buffer = new byte[65536];
                int bytesRead = 0;
                int totalBytesSent = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    log.debug("Sending recorded file.. bytes = {}", bytesRead);
                    outputStream.write(buffer, 0, bytesRead);

                    totalBytesSent += bytesRead;

                    publishProgress((int) (100 * totalBytesSent / fileSizeBytes));
                }
                log.debug("Successfully sent recorded file!");
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

            chameleonApplication.stopConnectionServer();

            // Stop receiving stream
            if (streamFromPeerTask != null) {
                streamFromPeerTask.cancel(true);
                streamFromPeerTask = null;
            }

            // Stop sending stream
            chameleonApplication.getStreamListener().terminateSession();

            progressBar.setVisibility(View.INVISIBLE);

            Toast.makeText(getApplicationContext(), "Session completed!", Toast.LENGTH_LONG).show();

            //Re-use MainActivity instance if already present. If not, create new instance.
            Intent openMainActivity = new Intent(getApplicationContext(), MainActivity.class);
            openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(openMainActivity);
        }
    }

    @AllArgsConstructor
    class StreamFromPeerTask extends AsyncTask<Void, Void, Void> {
        private InetAddress peerIp;
        private int port;

        @Override
        protected Void doInBackground(Void... params) {
            int attemptsLeft = 3;
            while (attemptsLeft-- > 0) {
                try {
                    log.debug("Connect to remote host invoked Thread = {}", Thread.currentThread());

                    if (!isIpReachable(peerIp)) {
                        log.warn("Peer IP = {} not reachable. Unable to receive stream!", peerIp.getHostAddress());
                        continue;
                    }

                    SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                    socket.setEnabledProtocols(new String[]{"TLSv1.2"});

                    final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);

                    PrintWriter pw = new PrintWriter(socket.getOutputStream());
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.START_SESSION)
                            .contents("abc")
                            .senderUserName(getUserName()) //Send this user's name
                            .build();
                    log.debug("Sending msg = {}", gson.toJson(peerMsg));
                    pw.println(gson.toJson(peerMsg));
                    pw.close();
                    final byte[] buffer = new byte[ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE_BYTES];
                    InputStream inputStream = socket.getInputStream();
                    final Matrix matrix = new Matrix();
                    while (!isCancelled()) {
                        // TODO More robust
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String recvMsg = bufferedReader.readLine();
                        if (recvMsg != null) {
                            StreamMetadata streamMetadata = gson.fromJson(recvMsg, StreamMetadata.class);
                            matrix.setScale(1, streamMetadata.isHorizontallyFlipped() ? -1 : 1);
                            matrix.postRotate(90);
                            final int bytesRead = inputStream.read(buffer);
                            if (bytesRead != -1) {
                                //log.debug("Received preview image from remote server bytes = " + bytesRead);
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
                    }
                } catch (Exception e) {
                    log.warn("Failed to stream from peer attemptsLeft = " + attemptsLeft, e);
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

    private boolean isIpReachable(final InetAddress ipAddress) throws IOException {
        // Wait till we can reach the remote host. May take time to refresh ARP cache
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime
                < MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE)) {
            if (ipAddress.isReachable(1000)) {
                log.debug("IP = {} is reachable!", ipAddress.getHostAddress());
                return true;
            }
        }
        return false;
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
