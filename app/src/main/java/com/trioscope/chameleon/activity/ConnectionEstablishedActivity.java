package com.trioscope.chameleon.activity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.stream.RecordingEventListener;
import com.trioscope.chameleon.stream.messages.HandshakeMessage;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.stream.messages.SendRecordedVideoResponse;
import com.trioscope.chameleon.stream.messages.StartRecordingResponse;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.types.SessionStatus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
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

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity extends EnableForegroundDispatchForNFCMessageActivity {
    public static final String PEER_INFO = "PEER_INFO";
    private static final int MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE = 20000; // 20 secs
    private ChameleonApplication chameleonApplication;
    private StreamFromPeerTask connectToServerTask;
    private Gson gson = new Gson();
    private boolean isRecording;
    private SSLSocketFactory sslSocketFactory;
    private BroadcastReceiver recordEventReceiver;
    private ProgressBar progressBar;
    private long networkCommunicationLatencyMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);

        progressBar = (ProgressBar) findViewById(R.id.progressBar_file_transfer);

        sslSocketFactory = getInitializedSSLSocketFactory();

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.createBackgroundRecorder(new RecordingEventListener() {
            @Override
            public void onStartRecording(final long recordingStartTimeMillis) {
                chameleonApplication.setRecordingStartTimeMillis(recordingStartTimeMillis);
            }

            @Override
            public void onStopRecording() {
                // Nothing for now
            }
        });

        recordEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                VideoRecorder videoRecorder = chameleonApplication.getVideoRecorder();
                if(ChameleonApplication.START_RECORDING_ACTION.equals(intent.getAction())) {
                    log.info("Start recording event received!!");
                    if (videoRecorder != null){
                        // initialize video camera
                        if (chameleonApplication.prepareVideoRecorder()) {
                            videoRecorder.startRecording();
                            isRecording = true;
                            log.info("isRecording is {}", isRecording);
                            Toast.makeText(getApplicationContext(), "Video recording started..", Toast.LENGTH_LONG).show();
                        }
                    }
                } else if(ChameleonApplication.STOP_RECORDING_ACTION.equals(intent.getAction())) {
                    log.info("Stop recording event received!!");
                    if (videoRecorder != null){
                        chameleonApplication.finishVideoRecording();
                        isRecording = false;
                        Toast.makeText(getApplicationContext(), "Video recording stopped..", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };

        // Display camera preview
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_session_preview);
        layout.addView(chameleonApplication.generatePreviewDisplay(
                chameleonApplication.getGlobalEglContextInfo()));

        // Retrieve peer info to start streaming
        Intent intent = getIntent();
        final PeerInfo peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);

        // Start streaming the preview
        chameleonApplication.setSessionStatus(SessionStatus.CONNECTED);
        chameleonApplication.getStreamListener().setStreamingStarted(true);

        log.info("PeerInfo = {}", peerInfo);
        connectToServerTask = new StreamFromPeerTask(peerInfo.getIpAddress(), peerInfo.getPort());
        connectToServerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        final Button recordSessionButton = (Button) findViewById(R.id.button_record_session);
        recordSessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(getApplicationContext());

                if (isRecording) {
                    // Sending message to peer to stop recording
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.STOP_RECORDING)
                            .build();
                    new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    // Stopping local video recording
                    manager.sendBroadcast(new Intent(ChameleonApplication.STOP_RECORDING_ACTION));
                    recordSessionButton.setText("Record");
                    isRecording = false;

                    // Give the user the option to retake the video or continue to merge
                    showRetakeOrMergeVideoDialog(peerInfo);

                } else {

                    // Sending message to peer to start remote recording
                    PeerMessage peerMsg = PeerMessage.builder()
                            .type(PeerMessage.Type.START_RECORDING)
                            .build();
                    new SendMessageToPeerTask(peerMsg, peerInfo.getIpAddress(), peerInfo.getPort())
                            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                    // Starting local video recording
                    manager.sendBroadcast(new Intent(ChameleonApplication.START_RECORDING_ACTION));

                    recordSessionButton.setText("Stop");
                    isRecording = true;
                }
            }
        });
        recordSessionButton.setVisibility(View.INVISIBLE);
        if (!PeerInfo.Role.DIRECTOR.equals(peerInfo.getRole())){
            // If peer is not director, then I am the director
            // So, should be able to start/stop recording.
            recordSessionButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        // Register to listen for recording events
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ChameleonApplication.START_RECORDING_ACTION);
        filter.addAction(ChameleonApplication.STOP_RECORDING_ACTION);
        log.info("Registering record event receiver");
        manager.registerReceiver(this.recordEventReceiver, filter);
        super.onResume();
    }

    private SSLSocketFactory getInitializedSSLSocketFactory(){
        SSLSocketFactory sslSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore trustStore = KeyStore.getInstance("BKS");
            InputStream trustStoreInputStream =  getApplicationContext().getResources().openRawResource(R.raw.chameleon_truststore);
            trustStore.load(trustStoreInputStream, "poiuyt".toCharArray());
            trustStoreInputStream.close();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, tmf.getTrustManagers(), null);
            sslSocketFactory = ctx.getSocketFactory();
        } catch (IOException  | 
                NoSuchAlgorithmException | 
                KeyStoreException | 
                KeyManagementException | 
                CertificateException e) {
            log.error("Failed to initialize SSL socket factory", e);
        }
        return sslSocketFactory;
    }

    private void showRetakeOrMergeVideoDialog(final PeerInfo peerInfo) {
        final File peerVideoFile = chameleonApplication.getOutputMediaFile(ChameleonApplication.MEDIA_TYPE_VIDEO);

        new AlertDialog.Builder(this)
                .setPositiveButton("Merge videos",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                new ReceiveVideoFromPeerTask(
                                        chameleonApplication.getVideoFile(),
                                        peerVideoFile,
                                        peerInfo.getIpAddress(),
                                        peerInfo.getPort())
                                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        }
                )
                .setNegativeButton("Retake videos",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                // do nothing.
                            }
                        }
                )
                .create().show();
    }

    class SendMessageToPeerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread;

        public SendMessageToPeerTask(
                final PeerMessage peerMsg,
                final InetAddress peerIp,
                final int port){

            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Wait till we can reach the remote host. May take time to refresh ARP cache
                        waitUntilIPBecomesReachable(peerIp);

                        SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                        socket.setEnabledProtocols(new String[]{"TLSv1.2"});

                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter pw  = new PrintWriter(socket.getOutputStream());
                        String serializedMsgToSend = gson.toJson(peerMsg);
                        log.info("Sending msg = {}", serializedMsgToSend);
                        long localCurrentTimeMsBeforeSendingRequest = System.currentTimeMillis();
                        pw.println(serializedMsgToSend);
                        pw.close();
                        if (PeerMessage.Type.START_RECORDING.equals(peerMsg.getType())){
                            String recvMsg = bufferedReader.readLine();
                            long localCurrentTimeMsAfterReceivingResponse = System.currentTimeMillis();
                            if (recvMsg != null) {
                                PeerMessage message = gson.fromJson(recvMsg, PeerMessage.class);
                                StartRecordingResponse response =
                                        gson.fromJson(message.getContents(), StartRecordingResponse.class);
                                log.info("Local current time before sending request = {}", localCurrentTimeMsBeforeSendingRequest);
                                log.info("Remote current time = {}", response.getCurrentTimeMillis());
                                log.info("Local current time after receiving response = {}", localCurrentTimeMsAfterReceivingResponse);
                                networkCommunicationLatencyMs = (localCurrentTimeMsAfterReceivingResponse -
                                        localCurrentTimeMsBeforeSendingRequest) / 2;
                                log.info("network communication latency = {} ms", networkCommunicationLatencyMs);
                            }
                        }

                    } catch (IOException e) {
                        log.error("Failed to send message = " + peerMsg + " to peer", e);
                    }
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            mThread.start();
            return null;
        }

        public void tearDown(){
            mThread.interrupt();
        }
    }

    @RequiredArgsConstructor
    class ReceiveVideoFromPeerTask extends AsyncTask<Void, Integer, Void>{
        @NonNull
        private File localVideoFile;
        @NonNull
        private File remoteVideoFile;
        @NonNull
        private InetAddress peerIp;
        @NonNull
        private Integer port;
        private Long remoteRecordingStartTimeMillis;
        private long remoteClockAheadOfLocalClockMillis = 0L;

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {

                // Wait till we can reach the remote host. May take time to refresh ARP cache
                waitUntilIPBecomesReachable(peerIp);

                SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                socket.setEnabledProtocols(new String[]{"TLSv1.2"});
                log.info("SSL client enabled protocols {}", Arrays.toString(socket.getEnabledProtocols()));
                log.info("SSL client enabled cipher suites {}", Arrays.toString(socket.getEnabledCipherSuites()));

                // Request recorded file from peer
                PeerMessage peerMsg = PeerMessage.builder()
                        .type(PeerMessage.Type.SEND_RECORDED_VIDEO)
                        .build();

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter pw  = new PrintWriter(socket.getOutputStream());
                String serializedMessageToSend = gson.toJson(peerMsg);
                log.info("Sending msg = {}", serializedMessageToSend);
                long localCurrentTimeMsBeforeSendingRequest = System.currentTimeMillis();
                pw.println(serializedMessageToSend);
                pw.close();

                // Receive recorded file size from peer
                long fileSizeBytes = -1;
                String recvMsg = bufferedReader.readLine();
                long localCurrentTimeMsAfterReceivingResponse = System.currentTimeMillis();
                if (recvMsg != null){
                    log.info("Message received = {}", recvMsg);
                    PeerMessage message = gson.fromJson(recvMsg, PeerMessage.class);
                    if (PeerMessage.Type.SEND_RECORDED_VIDEO_RESPONSE.equals(message.getType())){
                        SendRecordedVideoResponse response =
                                gson.fromJson(message.getContents(), SendRecordedVideoResponse.class);
                        if (response != null){
                            fileSizeBytes = response.getFileSizeBytes();
                            remoteRecordingStartTimeMillis = response.getRecordingStartTimeMillis();
                            log.info("Local current time before sending request = {}", localCurrentTimeMsBeforeSendingRequest);
                            log.info("Remote current time = {}", response.getCurrentTimeMillis());
                            log.info("Local current time after receiving response = {}", localCurrentTimeMsAfterReceivingResponse);
//                            long networkCommunicationLatencyMs = (localCurrentTimeMsAfterReceivingResponse -
//                                    localCurrentTimeMsBeforeSendingRequest) / 2;
                            // log.info("network communication latency = {} ms", networkCommunicationLatencyMs);
                            remoteClockAheadOfLocalClockMillis = response.getCurrentTimeMillis() -
                                    localCurrentTimeMsAfterReceivingResponse +
                                    networkCommunicationLatencyMs;
                        }
                    }
                }

                int totalBytesReceived = 0;

                // TODO Generate filename based on sessionId
                if (remoteVideoFile.exists()){
                    remoteVideoFile.delete();
                }
                if (remoteVideoFile.createNewFile()){
                    outputStream = new BufferedOutputStream(new FileOutputStream(remoteVideoFile));

                    final byte[] buffer = new byte[65536];
                    inputStream = new BufferedInputStream(socket.getInputStream());
                    int bytesRead = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1){
                        log.info("Receiving recorded file from peer.. bytes = {}", bytesRead);
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesReceived += bytesRead;
                        publishProgress((int) (100 * totalBytesReceived / fileSizeBytes));
                    }
                    log.info("Successfully received recorded video!");
                }
            } catch (IOException e) {
                log.error("Failed to receive recorded video..", e);
            } finally {
                try {
                    if (inputStream != null){
                        inputStream.close();
                    }
                    if (outputStream != null){
                        outputStream.close();
                    }
                } catch (IOException e){
                    log.error("Failed to close stream when receiving recorded video..", e);
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressBar.setVisibility(View.INVISIBLE);

            // Adjust recording start time for remote recording to account for
            // clock difference between two devices
            remoteRecordingStartTimeMillis -= remoteClockAheadOfLocalClockMillis;

            log.info("Adjusting remote recording start time millis by {} ms", remoteClockAheadOfLocalClockMillis);
            log.info("Local recording start time = {} ms", chameleonApplication.getRecordingStartTimeMillis());
            log.info("Remote recording start time = {} ms", remoteRecordingStartTimeMillis);
            RecordingMetadata localRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(localVideoFile.getAbsolutePath())
                    .startTimeMillis(chameleonApplication.getRecordingStartTimeMillis())
                    .build();
            RecordingMetadata remoteRecordingMetadata = RecordingMetadata.builder()
                    .absoluteFilePath(remoteVideoFile.getAbsolutePath())
                    .startTimeMillis(remoteRecordingStartTimeMillis)
                    .build();

            MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
            metadataRetriever.setDataSource(localVideoFile.getAbsolutePath());
            log.info("Local recording create time metadata = {}",
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            metadataRetriever.setDataSource(remoteVideoFile.getAbsolutePath());
            log.info("Remote recording create time metadata = {}",
                    metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE));
            log.info("Local filename = {}", localVideoFile.getName());
            log.info("Remote filename = {}", remoteVideoFile.getName());

            Intent intent = new Intent(getApplicationContext(), MergeVideosActivity.class);
            intent.putExtra(MergeVideosActivity.LOCAL_RECORDING_METADATA_KEY, gson.toJson(localRecordingMetadata));
            intent.putExtra(MergeVideosActivity.REMOTE_RECORDING_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
            startActivity(intent);
        }

    }

    class StreamFromPeerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread;

        public StreamFromPeerTask(final InetAddress hostIp, final int port){
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    receiveStreamFromPeer(hostIp, port);
                }
            });
        }

        @Override
        protected Void doInBackground(Void... params) {
            mThread.start();
            return null;
        }

        public void tearDown(){
            mThread.interrupt();
        }

    }

    private void receiveStreamFromPeer(final InetAddress remoteHostIp, final int port){
        log.info("Connect to remote host invoked Thread = {}", Thread.currentThread());
        try {
            waitUntilIPBecomesReachable(remoteHostIp);
            
            SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(remoteHostIp, port);
            socket.setEnabledProtocols(new String[]{"TLSv1.2"});

            final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);

            PrintWriter pw  = new PrintWriter(socket.getOutputStream());
            HandshakeMessage handshakeMessage = HandshakeMessage.builder().info("blah").build();
            PeerMessage peerMsg = PeerMessage.builder()
                    .type(PeerMessage.Type.START_SESSION)
                    .contents("abc")
                    .build();
            log.info("Sending msg = {}", gson.toJson(peerMsg));
            pw.println(gson.toJson(peerMsg));
            pw.close();
            final byte[] buffer = new byte[ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE];
            InputStream inputStream = socket.getInputStream();
            while (!Thread.currentThread().isInterrupted()){
                // TODO More robust
                final int bytesRead = inputStream.read(buffer);
                if (bytesRead != -1){
                    //log.info("Received preview image from remote server bytes = " + bytesRead);
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
            }
        } catch (IOException e) {
            log.warn("Connection to remote server closed", e);
        }
    }
    
    private void waitUntilIPBecomesReachable(final InetAddress ipAddress) throws IOException {
        // Wait till we can reach the remote host. May take time to refresh ARP cache
        long startTime = System.currentTimeMillis();
        while (!ipAddress.isReachable(1000)
                && (System.currentTimeMillis() - startTime
                > MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE));
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


    @Override
    protected void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        cleanup();

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity= new Intent(getApplicationContext(), MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        super.onBackPressed();
    }

    private void cleanup(){
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        log.info("Unregistering record event receiver");
        manager.unregisterReceiver(this.recordEventReceiver);

        if (connectToServerTask != null){
            connectToServerTask.tearDown();
        }
    }
}
