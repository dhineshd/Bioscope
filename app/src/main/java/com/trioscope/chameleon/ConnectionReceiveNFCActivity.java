package com.trioscope.chameleon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.stream.VideoStreamFrameListener;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConnectionReceiveNFCActivity extends ActionBarActivity {

    private Gson mGson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_receive_nfc);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_receive_nfc, menu);
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
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present

        final WiFiNetworkConnectionInfo connectionInfo =
                mGson.fromJson(new String(msg.getRecords()[0].getPayload()), WiFiNetworkConnectionInfo.class);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive intent = " + intent.getAction());

                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())){
                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                            networkInfo.isConnected() && getLocalIpAddressForWifi() != null) {
                        TextView textView = (TextView) findViewById(R.id.textView_connection_status);
                        String connectedMessage = "Connected to " + connectionInfo.getSSID();
                        textView.setText(connectedMessage);
                        try {
                            InetAddress hostIp = InetAddress.getByName(connectionInfo.getServerIpAddress());
                            new ConnectToServerTask(hostIp, connectionInfo.getServerPort())
                                    .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                            ImageView imageView = (ImageView) findViewById(R.id.imageView_stream_client);

                            VideoStreamFrameListener.StreamThreadHandler streamThreadHandler =
                                    ((ChameleonApplication) getApplication()).getStreamListener().getHandler();
                            streamThreadHandler.sendMessage(streamThreadHandler.obtainMessage(
                                    VideoStreamFrameListener.StreamThreadHandler.IMAGEVIEW_AVAILABLE, imageView));

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

        connectToWifiNetwork(connectionInfo.getSSID(), connectionInfo.getPassPhrase());

    }

    private String getLocalIpAddressForWifi(){
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            log.info("Unable to get host address.");
            ipAddressString = null;
        }
        return ipAddressString;
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
            log.info("Sent message to server : " + messageToServer);
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
}
