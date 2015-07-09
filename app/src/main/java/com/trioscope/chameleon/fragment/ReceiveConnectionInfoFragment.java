package com.trioscope.chameleon.fragment;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoFragment extends Fragment {
    private static final String CONNECTION_STATUS_TEXT_KEY = "CONNECTION_STATUS_TEXT";
    private Gson mGson = new Gson();
    private BroadcastReceiver enableWifiBroadcastReceiver;
    private BroadcastReceiver connectToWifiNetworkBroadcastReceiver;
    private TextView connectionStatusTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Re-use the fragment on orientation change etc to retain the view
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_receive_connection_info, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        connectionStatusTextView = (TextView) view.findViewById(R.id.textView_receiver_connection_status);

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Retain the connection status
        if (connectionStatusTextView != null){
            outState.putString(CONNECTION_STATUS_TEXT_KEY, (String) connectionStatusTextView.getText());
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        if (savedInstanceState != null){
            String connectionStatusText = savedInstanceState.getString(CONNECTION_STATUS_TEXT_KEY);
            if (connectionStatusText != null){
                connectionStatusTextView.setText(connectionStatusText);
            }
        }
        super.onViewStateRestored(savedInstanceState);
    }

    public void enableWifiAndEstablishConnection(final WiFiNetworkConnectionInfo connectionInfo){
        // Turn on Wifi device (if not already on)
        final WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()){
            log.info("Wifi already enabled..");
            establishConnection(connectionInfo);

        } else {
            connectionStatusTextView.setText("Enabling WiFi..");
            IntentFilter filter = new IntentFilter();
            //filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

            enableWifiBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    log.info("onReceive intent = {}, wifi enabled = {}", intent.getAction(), wifiManager.isWifiEnabled());
                    if (wifiManager.isWifiEnabled()) {

                        // Done with checking Wifi state
                        getActivity().unregisterReceiver(this);
                        log.info("Wifi enabled!!");

                        establishConnection(connectionInfo);
                    }
                }
            };

            // register to listen for change in Wifi state
            getActivity().registerReceiver(enableWifiBroadcastReceiver, filter);

            // Enable and wait for Wifi state change
            wifiManager.setWifiEnabled(true);
        }
    }

    private void establishConnection(final WiFiNetworkConnectionInfo connectionInfo){

        connectionStatusTextView.setText("Connecting to " + connectionInfo.getSSID() + "..");

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        connectToWifiNetworkBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive intent = " + intent.getAction());

                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())){
                    NetworkInfo networkInfo =
                            intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                    log.info("Network info : connected = {}, type = {}", networkInfo.isConnected(), networkInfo.getType());
                    if(networkInfo.getType() == ConnectivityManager.TYPE_WIFI &&
                            networkInfo.isConnected() && getLocalIpAddressForWifi() != null) {

                        // Done with checking connectivity
                        getActivity().unregisterReceiver(this);

                        try {
                            PeerInfo peerInfo = PeerInfo.builder()
                                    .ipAddress(InetAddress.getByName(connectionInfo.getServerIpAddress()))
                                    .port(connectionInfo.getServerPort())
                                    .build();

                            Intent connectionEstablishedIntent =
                                    new Intent(context, ConnectionEstablishedActivity.class);
                            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_INFO,
                                    mGson.toJson(peerInfo));
                            startActivity(connectionEstablishedIntent);

                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
            }
        };
        // Setup listener for connectivity change
        getActivity().registerReceiver(connectToWifiNetworkBroadcastReceiver, filter);

        connectToWifiNetwork(connectionInfo.getSSID(), connectionInfo.getPassPhrase());
    }


    private String getLocalIpAddressForWifi(){
        WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        // Convert little-endian to big-endian if needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
            log.info("Local IP address on network = {}", ipAddressString);
        } catch (UnknownHostException ex) {
            log.info("Unable to get host address.");
            ipAddressString = null;
        }
        return ipAddressString;
    }

    private void connectToWifiNetwork(final String networkSSID, final String networkPassword){
        WifiConfiguration conf = new WifiConfiguration();
        conf.SSID = "\"" + networkSSID + "\"";
        conf.preSharedKey = "\""+ networkPassword +"\"";

        WifiManager wifiManager = (WifiManager)getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.addNetwork(conf);
        int netId = wifiManager.addNetwork(conf);
        log.info("Connecting to SSID = {}, netId = {}", networkSSID, netId);
        wifiManager.setWifiEnabled(true);
        wifiManager.disconnect();
        wifiManager.enableNetwork(netId, true);
        wifiManager.reconnect();
    }

    @Override
    public void onDestroy() {
        if (enableWifiBroadcastReceiver != null){
            try{
                getActivity().unregisterReceiver(enableWifiBroadcastReceiver);
                enableWifiBroadcastReceiver = null;
            } catch (IllegalArgumentException e){
                // ignore
            }
        }
        if (connectToWifiNetworkBroadcastReceiver != null){
            try{
                getActivity().unregisterReceiver(connectToWifiNetworkBroadcastReceiver);
                connectToWifiNetworkBroadcastReceiver = null;
            } catch (IllegalArgumentException e){
                // ignore
            }
        }
        super.onDestroy();
    }
}
