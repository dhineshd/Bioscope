package com.trioscope.chameleon.activity;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.stream.messages.HandshakeMessage;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.SessionStatus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity extends ActionBarActivity {
    public static final String PEER_INFO = "PEER_INFO";
    private static final int MAX_WAIT_TIME_MSEC_FOR_IP_TO_BE_REACHABLE = 10000; // 10 secs
    private ChameleonApplication chameleonApplication;
    private StreamFromPeerTask connectToServerTask;
    private Gson gson = new Gson();
    private boolean isRecording;
    private SSLSocketFactory sslSocketFactory;
    private BroadcastReceiver recordEventReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);
        
        sslSocketFactory = getInitializedSSLSocketFactory();

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.createBackgroundRecorder();

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
                    // Sending message to peer to start recording
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

    private void showRetakeOrMergeVideoDialog(final PeerInfo peerInfo){
        new AlertDialog.Builder(this)
                .setPositiveButton("Merge videos",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {

                                new ReceiveVideoFromPeerTask(peerInfo.getIpAddress(), peerInfo.getPort())
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

                        PrintWriter pw  = new PrintWriter(socket.getOutputStream());
                        log.info("Sending msg = {}", gson.toJson(peerMsg));
                        pw.println(gson.toJson(peerMsg));
                        pw.close();

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

    class ReceiveVideoFromPeerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread;

        public ReceiveVideoFromPeerTask(
                final InetAddress peerIp,
                final int port){

            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    InputStream inputStream = null;
                    OutputStream outputStream = null;
                    try {

                        // Wait till we can reach the remote host. May take time to refresh ARP cache
                        waitUntilIPBecomesReachable(peerIp);

                        SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(peerIp, port);
                        socket.setEnabledProtocols(new String[]{"TLSv1.2"});
                        log.info("SSL client enabled protocols {}", Arrays.toString(socket.getEnabledProtocols()));
                        log.info("SSL client enabled cipher suites {}", Arrays.toString(socket.getEnabledCipherSuites()));

                        PeerMessage peerMsg = PeerMessage.builder()
                                .type(PeerMessage.Type.REQUEST_RECORDED_VIDEO)
                                .build();

                        PrintWriter pw  = new PrintWriter(socket.getOutputStream());
                        log.info("Sending msg = {}", gson.toJson(peerMsg));
                        pw.println(gson.toJson(peerMsg));
                        pw.close();

                        // TODO Generate filename based on sessionId
                        File peerVideoFile = chameleonApplication.getOutputMediaFile(ChameleonApplication.MEDIA_TYPE_VIDEO);
                        if (peerVideoFile.exists()){
                            peerVideoFile.delete();
                        }
                        if (peerVideoFile.createNewFile()){
                            outputStream = new BufferedOutputStream(new FileOutputStream(peerVideoFile));

                            final byte[] buffer = new byte[65536];
                            inputStream = new BufferedInputStream(socket.getInputStream());
                            int bytesRead = 0;
                            while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) != -1){
                                log.info("Receiving recorded file from peer..");
                                outputStream.write(buffer, 0, bytesRead);
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

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(),
                                    "Video transfer completed!", Toast.LENGTH_LONG);
                        }
                    });
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
                    .type(PeerMessage.Type.CONNECTION_HANDSHAKE)
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
                    final WeakReference<Bitmap> bmpRef = new WeakReference<Bitmap>(BitmapFactory.decodeByteArray(buffer, 0, bytesRead));
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
