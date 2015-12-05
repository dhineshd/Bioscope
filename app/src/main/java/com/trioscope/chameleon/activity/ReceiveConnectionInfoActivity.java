package com.trioscope.chameleon.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CaptureRequest;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.camera.CameraParams;
import com.trioscope.chameleon.listener.QRCodeScanEventListener;
import com.trioscope.chameleon.metrics.MetricNames;
import com.trioscope.chameleon.qrcode.QRCodeScanner;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;
import com.trioscope.chameleon.util.network.WifiUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoActivity extends AppCompatActivity
        implements QRCodeScanEventListener {

    private Gson gson = new Gson();
    private static final long MAX_CONNECTION_ESTABLISH_WAIT_TIME_MS = 10000;
    private static final int MAX_ATTEMPTS_TO_ADD_NETWORK = 3;
    private BroadcastReceiver connectToWifiNetworkBroadcastReceiver;
    private TextView connectionStatusTextView;
    private ChameleonApplication chameleonApplication;
    private ProgressBar progressBar;
    private Button cancelButton;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private Handler connectionTimerHandler;
    private Runnable connectionTimerRunnable;
    private long startTime;
    private SurfaceView previewDisplay;
    private QRCodeScanner qrCodeScanner;
    private RelativeLayout relativeLayoutFocusOverlay;
    private ImageView progressBarInterior;
    private volatile WiFiNetworkConnectionInfo connectionInfo;
    private long latestUserInteractionTimeMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startTime = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_connection_info);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Tear down Wifi hotspot since we are going to join
        // the peer's hotspot.
        ((ChameleonApplication) getApplication()).tearDownWifiHotspot();
        // Stop server since we will start that after connecting with director
        ((ChameleonApplication) getApplication()).stopConnectionServer();

        log.debug("ReceiveConnectionInfoActivity {}", this);

        connectionStatusTextView = (TextView) findViewById(R.id.textView_receiver_connection_status);
        progressBar = (ProgressBar) findViewById(R.id.receive_conn_info_prog_bar);
        progressBarInterior = (ImageView) findViewById(R.id.receive_conn_info_prog_bar_interior);
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
        relativeLayoutFocusOverlay = (RelativeLayout) findViewById(R.id.relativeLayout_qrcode_focus_overlay);
        connectionTimerHandler = new Handler();

        // Prepare camera preview
        chameleonApplication.preparePreview();

        chameleonApplication.getPreviewDisplayer().addOnPreparedCallback(new Runnable() {
            @Override
            public void run() {
                log.info("Preview displayer is ready to display a preview - " +
                        "adding one to the ConnectionEstablished activity");
                addCameraPreviewSurface();
                CameraParams cameraParams = CameraParams.builder()
                        .autoFocusMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE).build();
                chameleonApplication.startPreview(cameraParams);
            }
        });

        qrCodeScanner = new QRCodeScanner(chameleonApplication.getCameraFrameBuffer(), this);
        qrCodeScanner.start();

    }

    private void addCameraPreviewSurface() {
        log.debug("Creating surfaceView on thread {}", Thread.currentThread());

        try {
            ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();
            RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_qrcode_scan_preview);
            previewDisplay = chameleonApplication.createPreviewDisplay();
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            layout.addView(previewDisplay, layoutParams);
        } catch (Exception e) {
            log.error("Failed to add camera preview surface", e);
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
        if (isFinishing()) {
            cleanup();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        latestUserInteractionTimeMillis = System.currentTimeMillis();
        log.info("User is interacting with the app");
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        log.info("User leave hint triggered");
        if (ChameleonApplication.isUserLeavingOnLeaveHintTriggered(latestUserInteractionTimeMillis)) {
            log.info("User leave hint triggered and interacted with app recently. " +
                    "Assuming that user pressed home button.Finishing activity");
            finish();
        }
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

        if (qrCodeScanner != null) {
            qrCodeScanner.stop();
            qrCodeScanner = null;
        }

        chameleonApplication.stopPreview();
    }

    public void enableWifiAndEstablishConnection(final WiFiNetworkConnectionInfo connectionInfo) {

        log.info("Enable wifi and establish connection invoked..");
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                // Turn on Wifi device (if not already on)
                final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

                if (wifiManager.isWifiEnabled()) {
                    log.info("Wifi already enabled..");
                    establishConnection(connectionInfo);

                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showProgressBar();
                            connectionStatusTextView.setText(
                                    "Connecting\nto\n" + connectionInfo.getUserName());
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

                if (currentSSID != null && currentSSID.equals(connectionInfo.getSSID())) {
                    unregisterReceiver(this);
                    retrieveIpAddressAndEstablishConnection(connectionInfo);
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
                connectToWifiNetwork(connectionInfo);
                connectionTimerHandler.postDelayed(this, MAX_CONNECTION_ESTABLISH_WAIT_TIME_MS);
            }
        };

        connectionTimerHandler.post(connectionTimerRunnable);
    }

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void connectToWifiNetwork(final WiFiNetworkConnectionInfo connectionInfo) {

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                // Connect only if not already connected
                if (!connectionInfo.getSSID().equalsIgnoreCase(WifiUtil.getCurrentSSID(getApplicationContext()))) {
                    WifiConfiguration conf = new WifiConfiguration();
                    conf.SSID = "\"" + connectionInfo.getSSID() + "\"";
                    conf.preSharedKey = "\"" + connectionInfo.getPassPhrase() + "\"";

                    final WifiManager wifiManager =
                            (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    int remainingAttemptsToAddNetwork = MAX_ATTEMPTS_TO_ADD_NETWORK;
                    while (remainingAttemptsToAddNetwork-- > 0) {
                        int netId = wifiManager.addNetwork(conf);
                        if (netId != -1) {
                            log.info("Connecting to SSID = {}, netId = {}", connectionInfo.getSSID(), netId);
                            // Enable only our network and disable others
                            wifiManager.disconnect();
                            wifiManager.enableNetwork(netId, true);
                            break;
                        }
                    }
                } else {
                    log.info("Already connected to SSID = {}", connectionInfo.getSSID());
                    // No need to create another connection task
                    connectionTimerHandler.removeCallbacks(connectionTimerRunnable);
                    retrieveIpAddressAndEstablishConnection(connectionInfo);
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

    private void retrieveIpAddressAndEstablishConnection(
            final WiFiNetworkConnectionInfo connectionInfo) {
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
                    performConnectionEstablishedActions(connectionInfo);
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

    private void performConnectionEstablishedActions(final WiFiNetworkConnectionInfo connectionInfo) {
        progressBar.setVisibility(View.INVISIBLE);
        connectionStatusTextView.setText("Connected\nto\n" + connectionInfo.getUserName());

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
                    gson.toJson(peerInfo));
            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_CERTIFICATE_KEY,
                    connectionInfo.getCertificate());
            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_CERTIFICATE_PUBLIC_KEY_KEY, connectionInfo.getSerializedPublicKey());
            connectionEstablishedIntent.putExtra(ConnectionEstablishedActivity.PEER_INITIAL_PASSWORD_KEY, connectionInfo.getInitPass());
            startActivity(connectionEstablishedIntent);

            //publish time to establish connection from crew side
            publishTimeMetrics();

            finish();
        } catch (UnknownHostException e) {
            log.error("Failed to resolve peer IP", e);
        }
    }

    private void publishTimeMetrics() {
        ChameleonApplication.getMetrics().sendTime(
                MetricNames.Category.WIFI.getName(),
                MetricNames.Label.CREW_ESTABLISH_CONNECTION_TIME.getName(),
                System.currentTimeMillis() - startTime);
    }

    @Override
    public void onTextDecoded(final String decodedText) {

        if (connectionInfo == null) {

            if (decodedText != null) {

                try {
                    connectionInfo = WiFiNetworkConnectionInfo.deserializeConnectionInfo(decodedText);
                } catch (Exception e) {
                    log.warn("Received unknown QR code. Ignoring.. ", e);
                    return;
                }

                log.info("Received connection info : {}", connectionInfo);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        // Done with QR code scanning
                        qrCodeScanner.stop();

                        // Vibrate indicating QR code detected
                        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                        vibrator.vibrate(500);

                        //relativeLayoutCameraPreview.setVisibility(View.INVISIBLE);
                        relativeLayoutFocusOverlay.setVisibility(View.INVISIBLE);
                        connectionStatusTextView.setVisibility(View.VISIBLE);
                        progressBar.setVisibility(View.VISIBLE);
                        progressBarInterior.setVisibility(View.VISIBLE);

                        log.info("decoded text length = {}, contents = {}",
                                decodedText.length(), decodedText);

//                    WiFiNetworkConnectionInfo connectionInfo =
//                            WiFiNetworkConnectionInfo.deserializeConnectionInfo(
//                                    decodedText.getBytes(StandardCharsets.UTF_8));

                        log.info("connection info = {}", connectionInfo);
                        enableWifiAndEstablishConnection(connectionInfo);
                    }
                });
            } else {
                log.warn("decodedText is null");
            }
        }
    }
}
