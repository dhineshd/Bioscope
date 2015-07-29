package com.trioscope.chameleon.fragment;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.stream.WifiConnectionInfoListener;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import lombok.extern.slf4j.Slf4j;


/**
 * A placeholder fragment containing a simple view.
 */
@Slf4j
public class SendConnectionInfoFragment extends Fragment {
    private static final String CONNECTION_STATUS_TEXT_KEY = "CONNECTION_STATUS_TEXT";
    private ChameleonApplication chameleonApplication;
    private TextView connectionStatusTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        log.info("onCreate : SendConnectionInfoFragment");

        // Re-use the fragment on orientation change etc to retain the view
        setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        log.info("onViewCreated : SendConnectionInfoFragment");

        connectionStatusTextView = (TextView) view.findViewById(R.id.textView_sender_connection_status);

        chameleonApplication = (ChameleonApplication) getActivity().getApplication();
        new SetupWifiHotspotTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        log.info("onViewStateRestored : SendConnectionInfoFragment");
        super.onViewStateRestored(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_send_connection_info, container, false);
    }

    class SetupWifiHotspotTask extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            enableWifiAndCreateHotspot();
            return null;
        }
    }

    private void enableWifiAndCreateHotspot(){

        //TODO: Check if p2p is supported on device. What to do? Is there a way in AndroidManifest to check this?

        // Turn on Wifi device (if not already on)
        final WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {
            log.info("Wifi already enabled..");
            createWifiHotspot();
        } else {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    connectionStatusTextView.setText("Enabling Wifi..");
                }
            });
            chameleonApplication.enableWifiAndPerformActionWhenEnabled(new Runnable() {
                @Override
                public void run() {
                    createWifiHotspot();
                }
            });
        }
    }

    private void createWifiHotspot() {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectionStatusTextView.setText("Creating WiFi network..");
            }
        });

        log.info("Creating Wifi hotspot");

        //Initialize wifiManager and wifiChannel
        chameleonApplication.initializeWifiP2p();
        final WifiP2pManager wifiP2pManager = chameleonApplication.getWifiP2pManager();
        final WifiP2pManager.Channel wifiP2pChannel = chameleonApplication.getWifiP2pChannel();

        // Remove any old P2p connections
        wifiP2pManager.removeGroup(wifiP2pChannel, null);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Create new Wifi hotspot
        createWifiP2PGroup(wifiP2pManager, wifiP2pChannel);

    }

    private void createWifiP2PGroup(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel) {

        final int maxRetries = 50;

        wifiP2pManager.createGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            int retryCount = 0;

            @Override
            public void onSuccess() {
                log.info("WifiP2P createGroup success");
                // Calling requestGroupInfo immediately after createGroup success results
                // in group info being null. Adding some sleep seems to work.
                // TODO : Is there a better way?
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                getAndPersistWifiConnectionInfo(wifiP2pManager, wifiP2pChannel);
            }

            @Override
            public void onFailure(int reasonCode) {
                log.warn("WifiP2P createGroup. ReasonCode: {}", reasonCode);
                // Failure happens regularly when enabling WiFi and then creating group.
                // Retry with some backoff (Needs at least 1 sec. Tried 500 ms and it
                // still needed 2 retries)
                // TODO : Is there a better way?
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }

                if (retryCount++ < maxRetries) {
                    wifiP2pManager.createGroup(wifiP2pChannel, this);
                } else {
                    // TODO : What do we do if we can't create Wifi network?

                }
            }
        });
    }

    private void getAndPersistWifiConnectionInfo(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel){

        wifiP2pManager.requestGroupInfo(wifiP2pChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                log.info("Group info = {}", group);

                if (group != null) {
                    log.debug("Wifi hotspot details {} ", group);

                    log.info("Wifi hotspot details: SSID ({}), Passphrase ({}), InterfaceName ({}), GO ({}) ",
                            group.getNetworkName(), group.getPassphrase(), group.getInterface(), group.getOwner());

                    WiFiNetworkConnectionInfo nci =
                            WiFiNetworkConnectionInfo.builder()
                                    .SSID(group.getNetworkName())
                                    .passPhrase(group.getPassphrase())
                                    .serverIpAddress(getIpAddressForInterface(group.getInterface()).getHostAddress())
                                    .serverPort(ChameleonApplication.SERVER_PORT)
                                    .build();

                    // Connection info will be used in other components of the app
                    ((WifiConnectionInfoListener) getActivity()).onWifiNetworkCreated(nci);

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatusTextView.setText("Ready to send connection info..");
                        }
                    });

                }
            }
        });
    }


    private InetAddress getIpAddressForInterface(final String networkInterfaceName){
        log.info("Retrieving IP address for interface = {}", networkInterfaceName);
        try {
            for (Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                 networkInterfaces.hasMoreElements();){
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.getName().equalsIgnoreCase(networkInterfaceName)){
                    for (Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                         inetAddresses.hasMoreElements();){
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!inetAddress.isLoopbackAddress() &&
                                InetAddressUtils.isIPv4Address(inetAddress.getHostAddress())){
                            return inetAddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Failed to get IP address for interface = {}", networkInterfaceName);
        }
        return null;
    }
}
