package com.trioscope.chameleon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;
import android.widget.TextView;

import com.trioscope.chameleon.stream.VideoStreamFrameListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * Activity to receive connection info via Intent.
 *
 */
public class ReceiveConnectionInfoActivity extends ActionBarActivity {
    public static final String CONNECTION_INFO_BLOB_KEY = "CONNECTION_INFO";
    private static final Logger LOG = LoggerFactory.getLogger(ReceiveConnectionInfoActivity.class);
    private MediaPlayer mMediaPlayer;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private ParcelFileDescriptor[] parcelFds;
    private ConnectionServer connectionServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_connection_info);

        try {
            parcelFds = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            LOG.error("Failed to create pipe");
        }

        // Start connection server
        connectionServer = new ConnectionServer(5080, parcelFds[1]);
        connectionServer.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        Intent intent = getIntent();

        String connectionInfoBlob = intent.getStringExtra(CONNECTION_INFO_BLOB_KEY);
        // TODO Deserialize blob and retrieve connection info
        // TODO Validate blob

        String networkSSID = "JohnyCL";
        String networkPassword = "6144778485";
        final String remoteIp = "127.0.0.1";
        final Integer port = 5080;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LOG.info("onReceive intent = " + intent.getAction());

                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())){
                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                            networkInfo.isConnected() && getLocalIpAddressForWifi() != null) {
                        TextView textView = (TextView) findViewById(R.id.textView_connection_status);
                        textView.setText(R.string.connected_message);
                        try {
                            InetAddress hostIp = InetAddress.getByName(remoteIp);
                            new ConnectToServerTask(hostIp, port.intValue())
                                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                        // Done with checking Wifi state
                        unregisterReceiver(this);
                    }
                }
            }
        };
        registerReceiver(broadcastReceiver, filter);

        connectToWifiNetwork(networkSSID, networkPassword);

        ImageView imageView = (ImageView) findViewById(R.id.imageView_stream);
        LOG.info("imageView = " + imageView);

        VideoStreamFrameListener.StreamThreadHandler streamThreadHandler =
                ((ChameleonApplication) getApplication()).getStreamListener().getHandler();
        streamThreadHandler.sendMessage(streamThreadHandler.obtainMessage(
                VideoStreamFrameListener.StreamThreadHandler.IMAGEVIEW_AVAILABLE, imageView));

        streamThreadHandler.sendMessage(streamThreadHandler.obtainMessage(
                VideoStreamFrameListener.StreamThreadHandler.FRAME_DESTINATION_AVAILABLE, parcelFds[0]));
    }



    class ConnectionServer extends AsyncTask<Void, Void, Void> {
        private Thread mThread = null;

        public ConnectionServer(final int port, final ParcelFileDescriptor readFd) {

            mThread = new Thread(new Runnable(){

                @Override
                public void run() {
                    try {
                        ServerSocket serverSocket = new ServerSocket(port);
                        while (!Thread.currentThread().isInterrupted()) {
                            LOG.info("ServerSocket Created, awaiting connection");
                            Socket clientSocket = serverSocket.accept();
                            LOG.info("Received new client request");
                            BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            String messageFromClient = br.readLine();
                            LOG.info("Received message from client : " + messageFromClient);
                            InputStreamReader inputStreamReader = new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(readFd));
                            char[] buffer = new char[16384];
//                            OutputStreamWriter ow = new OutputStreamWriter(clientSocket.getOutputStream());
//                            while(inputStreamReader.read(buffer) != -1){
//                                LOG.info("Sending bytes = " + buffer.length);
//                                ow.write(buffer);
//                            }
//                            ow.close();
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        public void tearDown() {
            mThread.interrupt();
        }


        @Override
        protected Void doInBackground(Void... params) {
            mThread.start();
            return null;
        }
    }

    class ConnectToServerTask extends AsyncTask<Void, Void, Void>{
        private Thread mThread = null;

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


    private void connectToWifiNetwork(final String networkSSID, final String networkPassword){
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + networkSSID + "\"";
        conf.preSharedKey = "\""+ networkPassword +"\"";

        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.addNetwork(conf);
        int netId = wifiManager.addNetwork(conf);
        wifiManager.setWifiEnabled(true);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
    }

    private void connectToRemoteHost(final InetAddress remoteHostIp, final int port){
        try {
            Socket socket = new Socket(remoteHostIp, port);
            PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
            String messageToServer = "My IP : " + getLocalIpAddressForWifi() + "\n";
            pw.write(messageToServer);
            pw.close();
            LOG.info("Sent message to server : " + messageToServer);
            char[] buffer = new char[16384];
//            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//            while(true){
//                // TODO More robust
//                while(br.read(buffer) != -1){
//                    LOG.info("Received frame bytes = " + buffer.length);
//                }
//            }
            //connectToRemoteServer(socket);
            //socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void connectToRemoteServer(Socket socket){

        LOG.info("Connect to server invoked");

        if (mMediaPlayer != null){
            LOG.info("Already playing.. new connection ignored");
            return;
        }

        surfaceView = (SurfaceView) findViewById(R.id.stream_surfaceView);
        surfaceHolder = surfaceView.getHolder();

        mMediaPlayer = new MediaPlayer();

        mMediaPlayer.setDisplay(surfaceHolder);
        try {
            //String dataSource = "https://archive.org/download/ksnn_compilation_master_the_internet/ksnn_compilation_master_the_internet_512kb.mp4";
            String dataSource = "http://127.0.0.1:8888//storage/sdcard0/abc.mp4";
            LOG.info("MediaPlayer data source = " + dataSource);
            mMediaPlayer.setDataSource(dataSource);
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    LOG.info("MediaPlayer onPrepared");
                    mMediaPlayer.start();
                }
            });

            mMediaPlayer.prepare();

            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    LOG.error("MediaPlayer onError what = " + what + " extra = " + extra);
                    return false;
                }
            });

        } catch (IOException e){
            releaseMediaPlayer();
            throw new RuntimeException(e);
        }

    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null){
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private String getLocalIpAddressForWifi(){
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            LOG.info("Unable to get host address.");
            ipAddressString = null;
        }
        return ipAddressString;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_receive_connection_info, menu);
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
        releaseMediaPlayer();
        if (connectionServer != null) {
            connectionServer.tearDown();
        }
        super.onDestroy();
    }

}
