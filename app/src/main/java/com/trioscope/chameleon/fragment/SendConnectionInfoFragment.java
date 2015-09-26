package com.trioscope.chameleon.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
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
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;


/**
 * A placeholder fragment containing a simple view.
 */
@Slf4j
public class SendConnectionInfoFragment extends Fragment {
    private static final int MAX_ATTEMPTS_TO_CREATE_WIFI_HOTSPOT = 3;
    private static final String CONNECTION_STATUS_TEXT_KEY = "CONNECTION_STATUS_TEXT";
    private ChameleonApplication chameleonApplication;
    private TextView connectionStatusTextView;
    private ProgressBar progressBar;
    private SetupWifiHotspotTask setupWifiHotspotTask;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private Activity attachedActivity;
    private WifiP2pManager.GroupInfoListener wifiP2pGroupInfoListener;
    private Button cancelButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        log.info("onCreate : SendConnectionInfoFragment");

        // Re-use the fragment on orientation change etc to retain the view
        //setRetainInstance(true);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        log.info("onViewCreated : SendConnectionInfoFragment");

        chameleonApplication = (ChameleonApplication) getActivity().getApplication();
        connectionStatusTextView = (TextView) view.findViewById(R.id.textView_sender_connection_status);
        progressBar = (ProgressBar) view.findViewById(R.id.send_conn_info_prog_bar);
        cancelButton = (Button) view.findViewById(R.id.button_cancel_send_connection_info);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finishing current activity will take us back to previous activity
                // since it is in the back stack
                getActivity().finish();
            }
        });

        setupWifiHotspotTask = new SetupWifiHotspotTask();
        setupWifiHotspotTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

    @Override
    public void onPause() {
        super.onPause();
        log.info("Fragment onPause invoked");
        if (setupWifiHotspotTask != null) {
            setupWifiHotspotTask.cancel(true);
        }
        for (AsyncTask task : asyncTasks) {
            if (task != null) {
                task.cancel(true);
            }
        }
        if (wifiP2pGroupInfoListener != null) {
            wifiP2pGroupInfoListener = null;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        attachedActivity = activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        attachedActivity = null;
        log.info("Fragment detached!");
    }

    class SetupWifiHotspotTask extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                enableWifiAndCreateHotspot();
            } catch (Exception e) {
                log.error("Failed to create wifi hotspot", e);
            }
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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showProgressBar();
                    connectionStatusTextView.setText("Preparing to connect");
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressBar();
                connectionStatusTextView.setText("Preparing to connect");

            }
        });

        log.info("Creating Wifi hotspot. Thread = {}", Thread.currentThread());

        //Initialize wifiManager and wifiChannel
        chameleonApplication.initializeWifiP2p();
        final WifiP2pManager wifiP2pManager = chameleonApplication.getWifiP2pManager();
        final WifiP2pManager.Channel wifiP2pChannel = chameleonApplication.getWifiP2pChannel();

        // Remove any old P2p connections
        wifiP2pManager.removeGroup(wifiP2pChannel, null);

        // Wait for some time after removing old connections before creating new p2p group
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Create new Wifi hotspot
                        createWifiP2PGroup(wifiP2pManager, wifiP2pChannel, MAX_ATTEMPTS_TO_CREATE_WIFI_HOTSPOT, 0);
                    }
                }, 1000);
            }
        });
    }

    private void runOnUiThread(Runnable runnable) {
        if (attachedActivity != null) {
            attachedActivity.runOnUiThread(runnable);
        }
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
        int color = 0xffffa500;
        Drawable drawable = progressBar.getIndeterminateDrawable();
        drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void createWifiP2PGroup(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel,
            final int maxAttemptsLeftToCreateWifiHotspot,
            final long initialDelayMs) {

        // Start task after initial delay
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {

                log.info("Cresting wifi p2p group in Thread = {}", Thread.currentThread());

                wifiP2pManager.createGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        log.info("WifiP2P createGroup success. Thread = {}", Thread.currentThread());
                        getAndPersistWifiConnectionInfo(wifiP2pManager, wifiP2pChannel, 1000);
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        log.warn("WifiP2P createGroup. ReasonCode: {}", reasonCode);

                        if (maxAttemptsLeftToCreateWifiHotspot > 0) {
                            // Failure happens regularly when enabling WiFi and then creating group.
                            // Retry with some backoff (Needs at least 1 sec. Tried 500 ms and it
                            // still needed 2 retries)
                            // TODO : Is there a better way?

                            createWifiP2PGroup(
                                    wifiP2pManager,
                                    wifiP2pChannel,
                                    maxAttemptsLeftToCreateWifiHotspot - 1,
                                    1000);
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    connectionStatusTextView.setText("Oops! Please try again");
                                }
                            });
                        }
                    }
                });
                return null;
            }
        };
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        asyncTasks.add(task);
                    }
                }, initialDelayMs);
            }
        });
    }

    private void getAndPersistWifiConnectionInfo(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel,
            final long initialDelayMs) {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {

                wifiP2pGroupInfoListener = new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                        log.info("Group info = {}", group);

                        if (group != null) {
                            try {
                                log.info("Wifi hotspot details {}, Thread = {}", group, Thread.currentThread());

                                log.info("Wifi hotspot details: SSID ({}), Passphrase ({}), InterfaceName ({}), GO ({}) ",
                                        group.getNetworkName(), group.getPassphrase(), group.getInterface(), group.getOwner());

                                WiFiNetworkConnectionInfo nci =
                                        WiFiNetworkConnectionInfo.builder()
                                                .SSID(group.getNetworkName())
                                                .passPhrase(group.getPassphrase())
                                                .serverIpAddress(getIpAddressForInterface(group.getInterface()).getHostAddress())
                                                .serverPort(ChameleonApplication.SERVER_PORT)
                                                .userName(getUserName())
                                                .build();

                                // Connection info will be used in other components of the app
                                ((WifiConnectionInfoListener) getActivity()).onWifiNetworkCreated(nci);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressBar.setVisibility(View.INVISIBLE);
                                        connectionStatusTextView.setText("Use NFC to connect");
                                    }
                                });
                            } catch (Exception e) {
                                log.error("Failed to set connection info", e);
                            }
                        }
                    }
                };
                wifiP2pManager.requestGroupInfo(wifiP2pChannel, wifiP2pGroupInfoListener);
                return null;
            }
        };
        // Calling requestGroupInfo immediately after createGroup success results
        // in group info being null. Adding some sleep seems to work.
        // TODO : Is there a better way?

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        asyncTasks.add(task);
                    }
                }, initialDelayMs);
            }
        });
    }

    private String getUserName() {
        if (attachedActivity != null) {
            return PreferenceManager.getDefaultSharedPreferences(attachedActivity)
                    .getString(getString(R.string.pref_user_name_key), "");
        }
        return null;
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
