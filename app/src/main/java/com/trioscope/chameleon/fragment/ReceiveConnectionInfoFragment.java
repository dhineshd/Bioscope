package com.trioscope.chameleon.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.activity.ConnectionEstablishedActivity;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoFragment extends Fragment {
    private static final String CONNECTION_STATUS_TEXT_KEY = "CONNECTION_STATUS_TEXT";
    private static final long MAX_CONNECTION_ESTABLISH_WAIT_TIME_MS = 10000;
    private static final int MAX_ATTEMPTS_TO_ADD_NETWORK = 3;
    private Gson mGson = new Gson();
    private BroadcastReceiver connectToWifiNetworkBroadcastReceiver;
    private TextView connectionStatusTextView;
    private ChameleonApplication chameleonApplication;
    private ProgressBar progressBar;
    private Button cancelButton;
    private Activity attachedActivity;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private Handler connectionTimerHandler;
    private Runnable connectionTimerRunnable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Re-use the fragment on orientation change etc to retain the view
        //setRetainInstance(true);
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
        progressBar = (ProgressBar) view.findViewById(R.id.receive_conn_info_prog_bar);
        chameleonApplication = (ChameleonApplication) getActivity().getApplication();
        cancelButton = (Button) view.findViewById(R.id.button_cancel_receive_connection_info);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finishing current activity will take us back to previous activity
                // since it is in the back stack
                chameleonApplication.stopConnectionServer();
                getActivity().finish();
            }
        });
        connectionTimerHandler = new Handler();
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

    @Override
    public void onPause() {
        super.onPause();
        log.info("Fragment onPause invoked");

        chameleonApplication.unregisterReceiverSafely(connectToWifiNetworkBroadcastReceiver);
        connectToWifiNetworkBroadcastReceiver = null;

        for (AsyncTask task : asyncTasks) {
            if (task != null) {
                task.cancel(true);
            }
        }

        connectionTimerHandler.removeCallbacks(connectionTimerRunnable);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        attachedActivity = activity;
        log.info("Fragment attached!");
    }

    @Override
    public void onDetach() {
        super.onDetach();
        attachedActivity = null;
        log.info("Fragment detached!");
    }

    private void runOnUiThread(Runnable runnable) {
        if (attachedActivity != null) {
            attachedActivity.runOnUiThread(runnable);
        }
    }

    public void enableWifiAndEstablishConnection(final WiFiNetworkConnectionInfo connectionInfo) {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... voids) {
                // Turn on Wifi device (if not already on)
                final WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

                if (wifiManager.isWifiEnabled()){
                    log.info("Wifi already enabled..");
                    establishConnection(connectionInfo);

                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showProgressBar();
                            connectionStatusTextView.setText("Enabling WiFi..");
                        }
                    });

                    chameleonApplication.enableWifiAndPerformActionWhenEnabled(new Runnable() {
                        @Override
                        public void run() {
                            establishConnection(connectionInfo);
                        }
                    });
                }
                return null;
            }
        };
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                asyncTasks.add(task);
            }
        });

    }

    private void establishConnection(final WiFiNetworkConnectionInfo connectionInfo){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressBar();
                connectionStatusTextView.setText("Connecting\nto\n" + connectionInfo.getUserName());

            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);

        connectToWifiNetworkBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive intent = " + intent.getAction());
                final String currentSSID = getCurrentSSID();
                String localIPAddress = getLocalIpAddressForWifi();
                log.info("current SSID = {}, local IP = {}", currentSSID, localIPAddress);

                // Fragment is finishing
                if (attachedActivity == null) {
                    unregisterReceiverSafely(this);
                    return;
                }

                if(currentSSID != null &&
                        currentSSID.equals(connectionInfo.getSSID()) &&
                        localIPAddress != null) {

                    progressBar.setVisibility(View.INVISIBLE);
                    connectionStatusTextView.setText("Connected to " + connectionInfo.getUserName());

                    // Done with checking connectivity
                    unregisterReceiverSafely(this);

                    try {
                        InetAddress remoteIp = InetAddress.getByName(connectionInfo.getServerIpAddress());
                        PeerInfo peerInfo = PeerInfo.builder()
                                .ipAddress(remoteIp)
                                .port(connectionInfo.getServerPort())
                                .role(PeerInfo.Role.DIRECTOR)
                                .userName(connectionInfo.getUserName())
                                .build();

                        Intent connectionEstablishedIntent =
                                new Intent(context, ConnectionEstablishedActivity.class);
                        connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_INFO,
                                mGson.toJson(peerInfo));
                        startActivity(connectionEstablishedIntent);

                    } catch (UnknownHostException e) {
                        log.error("Failed to resolve peer IP", e);
                    }

                }
            }
        };
        // Setup listener for connectivity change
        attachedActivity.registerReceiver(connectToWifiNetworkBroadcastReceiver, filter);

        connectionTimerRunnable = new Runnable() {

            @Override
            public void run() {
                log.info("Current thread = {}", Thread.currentThread());
                connectToWifiNetwork(connectionInfo.getSSID(), connectionInfo.getPassPhrase());
                connectionTimerHandler.postDelayed(this, MAX_CONNECTION_ESTABLISH_WAIT_TIME_MS);
            }
        };

        connectionTimerHandler.post(connectionTimerRunnable);
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        int color = 0xffffa500;
        progressBar.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void unregisterReceiverSafely(final BroadcastReceiver receiver){
        if (receiver != null){
            try{
                getActivity().unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                // ignoring this since this can happen due to some race conditions
            }
        }
    }

    public String getCurrentSSID() {
        String ssid = null;
        ConnectivityManager connManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null && connectionInfo.getSSID() != null) {
                ssid = connectionInfo.getSSID().replace("\"", ""); // Remove quotes
            }
        }
        return ssid;
    }

    private String getLocalIpAddressForWifi() {
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

    private void connectToWifiNetwork(final String networkSSID, final String networkPassword) {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {
                // Connect only if not already connected
                if (!networkSSID.equalsIgnoreCase(getCurrentSSID())) {
                    WifiConfiguration conf = new WifiConfiguration();
                    conf.SSID = "\"" + networkSSID + "\"";
                    conf.preSharedKey = "\"" + networkPassword + "\"";

                    final WifiManager wifiManager =
                            (WifiManager)getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    int remainingAttemptsToAddNetwork = MAX_ATTEMPTS_TO_ADD_NETWORK;
                    while (remainingAttemptsToAddNetwork-- > 0) {
                        int netId = wifiManager.addNetwork(conf);
                        if (netId != -1) {
                            log.info("Connecting to SSID = {}, netId = {}", networkSSID, netId);
                            // Enable only our network and disable others
                            wifiManager.disconnect();
                            wifiManager.enableNetwork(netId, true);
                            break;
                        }
                    }
                } else {
                    log.info("Already connected to SSID = {}", networkSSID);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                log.info("Connection initiation task completed!");
            }
        };

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                asyncTasks.add(task);
            }
        });
    }
}
