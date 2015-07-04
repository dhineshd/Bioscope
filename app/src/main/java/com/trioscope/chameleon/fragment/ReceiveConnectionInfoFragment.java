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
    public static final String CONNECTION_INFO_KEY = "CONNECTION_INFO_KEY";

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

    public void establishConnection(final WiFiNetworkConnectionInfo connectionInfo){

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

                        // Done with checking Wifi state
                        getActivity().unregisterReceiver(this);

                        try {
                            PeerInfo peerInfo = PeerInfo.builder()
                                    .ipAddress(InetAddress.getByName(connectionInfo.getServerIpAddress()))
                                    .port(connectionInfo.getServerPort())
                                    .build();

                            Intent connectionEstablishedIntent =
                                    new Intent(context, ConnectionEstablishedActivity.class);
                            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_INFO,
                                    new Gson().toJson(peerInfo));
                            startActivity(connectionEstablishedIntent);

                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }

                    }
                }
            }
        };
        // Setup listener for connectivity change
        getActivity().registerReceiver(broadcastReceiver, filter);

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

}
