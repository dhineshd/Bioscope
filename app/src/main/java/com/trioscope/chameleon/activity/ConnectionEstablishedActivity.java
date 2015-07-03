package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.PeerInfo;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionEstablishedActivity extends ActionBarActivity {
    public static final String PEER_INFO = "PEER_INFO";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_established);

        ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.connection_established_layout);
        //layout.addView(new SurfaceTextureDisplay(this));
//
//        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams
//                (RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
//        RelativeLayout relativeLayout = new RelativeLayout(getApplicationContext());
//        relativeLayout.addView(chameleonApplication.getPreviewDisplay());
//        layout.addView(relativeLayout);


        // Retrieve peer info to start streaming
        Gson gson = new Gson();
        Intent intent = getIntent();
        PeerInfo peerInfo = gson.fromJson(intent.getStringExtra(PEER_INFO), PeerInfo.class);
        //PeerInfo peerInfo =
        //        ((ChameleonApplication) getApplication()).getPeerInfo();
        InetAddress peerIp = peerInfo.getIpAddress();
        ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);

        chameleonApplication.getServerEventListener().setStreamingSessionStarted(true);

        new ConnectToServerTask(peerIp, peerInfo.getPort())
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    class ConnectToServerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread;

        public ConnectToServerTask(final InetAddress hostIp, final int port){
            mThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    connectToRemoteHost(hostIp, port);
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

    private void connectToRemoteHost(final InetAddress remoteHostIp, final int port){
        log.info("Connect to remote host invoked");
        try {
            Socket socket = new Socket(remoteHostIp, port);
            final ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_remote);
            final byte[] buffer = new byte[1024];
            InputStream inputStream = socket.getInputStream();
            while (true){
                // TODO More robust
                final int bytesRead = inputStream.read(buffer);
                if (bytesRead != -1){
                    log.info("Received preview image from remote server bytes = " + bytesRead);
                    final Bitmap bmp = BitmapFactory.decodeByteArray(buffer, 0, bytesRead);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (imageView != null) {
                                imageView.setImageBitmap(bmp);
                            }
                        }
                    });
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
