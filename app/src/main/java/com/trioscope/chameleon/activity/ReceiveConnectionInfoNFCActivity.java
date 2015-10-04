package com.trioscope.chameleon.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;
import com.trioscope.chameleon.util.network.WifiUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoNFCActivity extends EnableForegroundDispatchForNFCMessageActivity {

    private Gson gson = new Gson();
    private static final long MAX_CONNECTION_ESTABLISH_WAIT_TIME_MS = 10000;
    private static final int MAX_ATTEMPTS_TO_ADD_NETWORK = 3;
    private Gson mGson = new Gson();
    private BroadcastReceiver connectToWifiNetworkBroadcastReceiver;
    private TextView connectionStatusTextView;
    private ChameleonApplication chameleonApplication;
    private ProgressBar progressBar;
    private Button cancelButton;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private Handler connectionTimerHandler;
    private Runnable connectionTimerRunnable;
    private WiFiNetworkConnectionInfo connectionInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_connection_info_nfc);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Tear down Wifi hotspot since we are going to join
        // the peer's hotspot.
        ((ChameleonApplication)getApplication()).tearDownWifiHotspot();

        log.debug("ReceiveConnectionInfoNFCActivity {}", this);

        ((ChameleonApplication)getApplication()).startConnectionServerIfNotRunning();

        connectionStatusTextView = (TextView) findViewById(R.id.textView_receiver_connection_status);
        progressBar = (ProgressBar) findViewById(R.id.receive_conn_info_prog_bar);
        chameleonApplication = (ChameleonApplication) getApplication();
        cancelButton = (Button) findViewById(R.id.button_cancel_receive_connection_info);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finishing current activity will take us back to previous activity
                // since it is in the back stack
                finish();
            }
        });
        connectionTimerHandler = new Handler();

        String connectionInfoAsJson = getIntent().getStringExtra(
                ConnectionEstablishedActivity.CONNECTION_INFO_AS_JSON_EXTRA);

        if (connectionInfoAsJson != null) {
            connectionStatusTextView.setVisibility(TextView.VISIBLE);
            connectionInfo = gson.fromJson(connectionInfoAsJson, WiFiNetworkConnectionInfo.class);
            enableWifiAndEstablishConnection();
        } else {
            log.warn("connectionInfoAsJson is null");
        }
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
    protected void onPause() {
        super.onPause();
        log.info("onPause invoked!");
        cleanup();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private void cleanup() {
        if (connectToWifiNetworkBroadcastReceiver != null) {
            chameleonApplication.unregisterReceiverSafely(connectToWifiNetworkBroadcastReceiver);
            connectToWifiNetworkBroadcastReceiver = null;
        }

        for (AsyncTask task : asyncTasks) {
            if (task != null) {
                task.cancel(true);
            }
        }

        connectionTimerHandler.removeCallbacks(connectionTimerRunnable);
    }

    public void enableWifiAndEstablishConnection() {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... voids) {
                // Turn on Wifi device (if not already on)
                final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

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

    private void establishConnection(final WiFiNetworkConnectionInfo connectionInfo) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressBar();
                connectionStatusTextView.setText("Connecting\nto\n" + connectionInfo.getUserName());

            }
        });

        connectToWifiNetworkBroadcastReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive intent = " + intent.getAction());
                final String currentSSID = WifiUtil.getCurrentSSID(context);
                log.info("Current SSID = {}", currentSSID);

                if(currentSSID != null && currentSSID.equals(connectionInfo.getSSID())) {
                    unregisterReceiver(this);
                    retrieveIpAddressAndEstablishConnection();
                }
            }
        };

        // Setup receiver to listen for connectivity change
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(connectToWifiNetworkBroadcastReceiver, filter);

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

    private void connectToWifiNetwork(final String networkSSID, final String networkPassword) {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {
                // Connect only if not already connected
                if (!networkSSID.equalsIgnoreCase(WifiUtil.getCurrentSSID(getApplicationContext()))) {
                    WifiConfiguration conf = new WifiConfiguration();
                    conf.SSID = "\"" + networkSSID + "\"";
                    conf.preSharedKey = "\"" + networkPassword + "\"";

                    final WifiManager wifiManager =
                            (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
                    // No need to create another connection task
                    connectionTimerHandler.removeCallbacks(connectionTimerRunnable);
                    retrieveIpAddressAndEstablishConnection();
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

    private void retrieveIpAddressAndEstablishConnection() {
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            String ipAddress = null;
            @Override
            protected Void doInBackground(Void... params) {
                do {
                    ipAddress = WifiUtil.getLocalIpAddressForWifi(getApplicationContext());
                } while (!isCancelled() && ipAddress == null);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                if (ipAddress != null) {
                    log.info("Successfully retrieved local IP = {}", ipAddress);
                    performConnectionEstablishedActions();
                }
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

    private void performConnectionEstablishedActions() {
        progressBar.setVisibility(View.INVISIBLE);
        connectionStatusTextView.setText("Connected to " + connectionInfo.getUserName());

        try {
            InetAddress remoteIp = InetAddress.getByName(connectionInfo.getServerIpAddress());
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(remoteIp)
                    .port(connectionInfo.getServerPort())
                    .role(PeerInfo.Role.DIRECTOR)
                    .userName(connectionInfo.getUserName())
                    .build();

            Intent connectionEstablishedIntent =
                    new Intent(this, ConnectionEstablishedActivity.class);
            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_INFO,
                    mGson.toJson(peerInfo));
            startActivity(connectionEstablishedIntent);
            finish();
        } catch (UnknownHostException e) {
            log.error("Failed to resolve peer IP", e);
        }
    }
}
