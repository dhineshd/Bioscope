package com.trioscope.chameleon.activity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.stream.ServerEventListener;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.PeerMessage;
import com.trioscope.chameleon.types.StartSessionMessageContents;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;
import com.trioscope.chameleon.util.QRCodeUtil;
import com.trioscope.chameleon.util.security.SSLUtil;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendConnectionInfoActivity extends AppCompatActivity
        implements ServerEventListener {
    private static final int MAX_ATTEMPTS_TO_CREATE_WIFI_HOTSPOT = 3;
    private TextView connectionStatusTextView;
    private ProgressBar progressBar;
    private SetupWifiHotspotTask setupWifiHotspotTask;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private WifiP2pManager.GroupInfoListener wifiP2pGroupInfoListener;
    private Button cancelButton;
    private WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo;
    private ChameleonApplication chameleonApplication;
    private Set<Intent> processedIntents = new HashSet<Intent>();
    private Gson gson = new Gson();
    private boolean isWifiHotspotRequiredForNextStep;
    private ImageView progressBarInteriorImageView;
    private ImageView qrCodeImageView;
    private X509Certificate serverCertificate;
    private SecretKey symmetricKey;
    private long latestUserInteractionTimeMillis;
    private TextView sendConnectionInfoInstructionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        log.info("Created");
        chameleonApplication = (ChameleonApplication) getApplication();

        // Start server in background
        new AsyncTask<Void, Void, X509Certificate>() {

            @Override
            protected X509Certificate doInBackground(Void... params) {
                // Start the server (will generate new certificate)
                return chameleonApplication.stopAndStartConnectionServer();
            }

            @Override
            protected void onPostExecute(X509Certificate certificate) {
                super.onPostExecute(certificate);
                serverCertificate = certificate;
                symmetricKey = SSLUtil.createSymmetricKey();
                log.info("Key type = {}", symmetricKey.getClass());

                chameleonApplication.getServerEventListenerManager().addListener(
                        SendConnectionInfoActivity.this);
                setupWifiHotspotTask = new SetupWifiHotspotTask();
                setupWifiHotspotTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        connectionStatusTextView = (TextView) findViewById(R.id.textView_sender_connection_status);
        progressBar = (ProgressBar) findViewById(R.id.send_conn_info_prog_bar);
        progressBarInteriorImageView = (ImageView) findViewById(R.id.send_conn_info_prog_bar_interior);
        qrCodeImageView = (ImageView) findViewById(R.id.imageview_qr_code);
        sendConnectionInfoInstructionTextView = (TextView) findViewById(R.id.send_conn_instructions);

        cancelButton = (Button) findViewById(R.id.button_cancel_send_connection_info);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finishing current activity will take us back to previous activity
                // since it is in the back stack
                finish();
            }
        });

        // Make UI elements visible
        progressBar.setVisibility(View.VISIBLE);
        progressBarInteriorImageView.setVisibility(View.VISIBLE);
        connectionStatusTextView.setVisibility(View.VISIBLE);
        connectionStatusTextView.setText("Preparing\nto\nconnect");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_connection_establishment_nfc, menu);
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
    protected void onResume() {
        super.onResume();

        log.info("Activity has resumed from background {}",
                PreferenceManager.getDefaultSharedPreferences(this).getAll());
    }

    @Override
    public void onPause() {
        super.onPause();
        log.info("onPause invoked");
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

    private void cleanup() {
        log.info("Cleanup invoked..");

        chameleonApplication.getServerEventListenerManager().removeListener(this);

        if (setupWifiHotspotTask != null) {
            setupWifiHotspotTask.cancel(true);
            setupWifiHotspotTask = null;
        }
        for (AsyncTask task : asyncTasks) {
            if (task != null) {
                task.cancel(true);
            }
        }
        wifiP2pGroupInfoListener = null;

        // Tear down wifi hotspot if not needed anymore
        if (!isWifiHotspotRequiredForNextStep) {
            chameleonApplication.tearDownWifiHotspot();
            chameleonApplication.stopConnectionServer();
        }
    }

    @Override
    public void onClientRequest(final Socket clientSocket, final PeerMessage messageFromClient) {

        if (PeerMessage.Type.START_SESSION.equals(messageFromClient.getType())) {
            log.info("Starting connection established activity!");

            String contents = messageFromClient.getContents();
            StartSessionMessageContents startSessionContents = gson.fromJson(contents, StartSessionMessageContents.class);

            // First check the password sent over the wire
            String expectedPassword = wiFiNetworkConnectionInfo.getInitPass();
            if (expectedPassword.equals(startSessionContents.getPassword())) {
                log.info("Expected password matches the given password, proceeding");
            } else {
                log.info("Expected password does not match - {} given", startSessionContents.getPassword());
                return;
            }

            final Intent intent = new Intent(SendConnectionInfoActivity.this,
                    ConnectionEstablishedActivity.class);
            PeerInfo peerInfo = PeerInfo.builder()
                    .ipAddress(clientSocket.getInetAddress())
                    .port(ChameleonApplication.SERVER_PORT)
                    .role(PeerInfo.Role.CREW_MEMBER)
                    .userName(messageFromClient.getSenderUserName())
                    .build();
            intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, gson.toJson(peerInfo));
            intent.putExtra(ConnectionEstablishedActivity.PEER_CERTIFICATE_KEY, startSessionContents.getBytes());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    log.info("Thread is " + Thread.currentThread());
                    connectionStatusTextView.setText("Connecting\nto\n" + messageFromClient.getSenderUserName());
                    progressBar.setVisibility(View.VISIBLE);
                    isWifiHotspotRequiredForNextStep = true;
                    startActivity(intent);
                }
            });
        }
    }


    class SetupWifiHotspotTask extends AsyncTask<Void, Void, Void> {

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

    private void enableWifiAndCreateHotspot() {

        //TODO: Check if p2p is supported on device. What to do? Is there a way in AndroidManifest to check this?

        // Turn on Wifi device (if not already on)
        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()) {
            log.info("Wifi already enabled..");
            createWifiHotspot();
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showProgressBar();
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

    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void createWifiP2PGroup(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel,
            final int maxAttemptsLeftToCreateWifiHotspot,
            final long initialDelayMs) {

        // Start task after initial delay
        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

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
                                    progressBar.setVisibility(View.INVISIBLE);
                                    connectionStatusTextView.setText("Oops!\nPlease try\nagain");

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

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                wifiP2pGroupInfoListener = new WifiP2pManager.GroupInfoListener() {
                    @Override
                    public void onGroupInfoAvailable(final WifiP2pGroup group) {

                        if (group != null && wiFiNetworkConnectionInfo == null) {

                            log.info("Wifi hotspot details: SSID ({}), Passphrase ({}), InterfaceName ({}), GO ({}) ",
                                    group.getNetworkName(), group.getPassphrase(), group.getInterface(), group.getOwner());

                            // Retrieve connection info in background
                            new AsyncTask<Void, Void, Void>() {

                                @Override
                                protected Void doInBackground(Void... params) {

                                    wiFiNetworkConnectionInfo =
                                            WiFiNetworkConnectionInfo.builder()
                                                    .version(WiFiNetworkConnectionInfo.CURRENT_VERSION)
                                                    .SSID(group.getNetworkName())
                                                    .passPhrase(group.getPassphrase())
                                                    .serverIpAddress(getIpAddressForInterface(
                                                            group.getInterface()).getHostAddress())
                                                    .serverPort(ChameleonApplication.SERVER_PORT)
                                                    .userName(getUserName())
                                                    .certificateType(WiFiNetworkConnectionInfo.X509_CERTIFICATE_TYPE)
                                                    .certificatePublicKey(chameleonApplication.getConnectionServerKeyPair().getPublic())
                                                    .initPass(RandomStringUtils.randomAlphanumeric(SSLUtil.INITIAL_PASSWORD_LENGTH))
                                                    .build();

                                    String serialized = wiFiNetworkConnectionInfo.getSerializedPublicKey();
                                    log.info("Serialized: {}", serialized);
                                    log.info("Deserialized: {}", wiFiNetworkConnectionInfo.fromSerializedPublicKey(serialized));

                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Void aVoid) {
                                    super.onPostExecute(aVoid);
                                    progressBar.setVisibility(View.INVISIBLE);
                                    sendConnectionInfoInstructionTextView.setVisibility(View.VISIBLE);
                                    //progressBarInteriorImageView.setVisibility(View.VISIBLE);
                                    //connectionStatusTextView.setVisibility(View.VISIBLE);
                                    //connectionStatusTextView.setText("Beam\nto\nconnect");

                                    String serializedWifiConnectionInfo = WiFiNetworkConnectionInfo.serializeConnectionInfo(wiFiNetworkConnectionInfo);

                                    log.info("Serialized info : length = {}, contents = '{}'",
                                            serializedWifiConnectionInfo.length(), serializedWifiConnectionInfo);
                                    Bitmap qrCodeImage = QRCodeUtil.generateQRCode(
                                            serializedWifiConnectionInfo, 150, 150);
                                    qrCodeImageView.setImageBitmap(qrCodeImage);
                                    qrCodeImageView.setVisibility(View.VISIBLE);
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getString(getString(R.string.pref_user_name_key), "");
    }

    private InetAddress getIpAddressForInterface(final String networkInterfaceName) {
        log.info("Retrieving IP address for interface = {}", networkInterfaceName);
        try {
            for (Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
                 networkInterfaces.hasMoreElements(); ) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                if (networkInterface.getName().equalsIgnoreCase(networkInterfaceName)) {
                    for (Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                         inetAddresses.hasMoreElements(); ) {
                        InetAddress inetAddress = inetAddresses.nextElement();
                        if (!inetAddress.isLoopbackAddress() &&
                                InetAddressUtils.isIPv4Address(inetAddress.getHostAddress())) {
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
