package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.MultipleWifiHotspotAlertDialogFragment;
import com.trioscope.chameleon.stream.ServerEventListener;
import com.trioscope.chameleon.stream.messages.PeerMessage;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static android.nfc.NdefRecord.createMime;

@Slf4j
public class SendConnectionInfoNFCActivity
        extends EnableForegroundDispatchForNFCMessageActivity
        implements NfcAdapter.CreateNdefMessageCallback, ServerEventListener {
    private static final int MAX_ATTEMPTS_TO_CREATE_WIFI_HOTSPOT = 3;
    private TextView connectionStatusTextView;
    private ProgressBar progressBar;
    private SetupWifiHotspotTask setupWifiHotspotTask;
    private Set<AsyncTask<Void, Void, Void>> asyncTasks = new HashSet<>();
    private WifiP2pManager.GroupInfoListener wifiP2pGroupInfoListener;
    private Button cancelButton;
    private WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo;
    private Gson mGson = new Gson();
    private ChameleonApplication chameleonApplication;
    private Set<Intent> processedIntents = new HashSet<Intent>();
    private Gson gson = new Gson();
    private boolean isWifiHotspotRequiredForNextStep;
    private VideoView nfcTutVideoView;
    private boolean firstClientRequestReceived;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info_nfc);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        log.info("Created");
        chameleonApplication = (ChameleonApplication) getApplication();
        chameleonApplication.startConnectionServerIfNotRunning();

        connectionStatusTextView = (TextView) findViewById(R.id.textView_sender_connection_status);
        progressBar = (ProgressBar) findViewById(R.id.send_conn_info_prog_bar);
        cancelButton = (Button) findViewById(R.id.button_cancel_send_connection_info);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Finishing current activity will take us back to previous activity
                // since it is in the back stack
                finish();
            }
        });

        setupWifiHotspotTask = new SetupWifiHotspotTask();
        setupWifiHotspotTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        // Register callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);

        chameleonApplication.getServerEventListenerManager().addListener(this);

        nfcTutVideoView = (VideoView) findViewById(R.id.nfc_tut_video_view);
        nfcTutVideoView.setVideoPath("android.resource://" + getPackageName() + "/" + R.raw.nfc_tutorial);

        nfcTutVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                                  @Override
                                                  public void onPrepared(MediaPlayer mp) {
                                                      mp.setLooping(true);
                                                      mp.setVolume(0, 0);
                                                  }
                                              }
        );

        nfcTutVideoView.start();

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
    public NdefMessage createNdefMessage(NfcEvent event) {
        if (wiFiNetworkConnectionInfo != null) {
            String text = mGson.toJson(wiFiNetworkConnectionInfo, WiFiNetworkConnectionInfo.class);
            NdefMessage msg = new NdefMessage(
                    new NdefRecord[]{createMime(
                            getString(R.string.mime_type_nfc_connect_wifi), text.getBytes())
                            /**
                             * The Android Application Record (AAR) is commented out. When a device
                             * receives a push with an AAR in it, the application specified in the AAR
                             * is guaranteed to run. The AAR overrides the tag dispatch system.
                             * You can add it back in to guarantee that this
                             * activity starts when receiving a beamed message. For now, this code
                             * uses the tag dispatch system.
                             */
                            //,NdefRecord.createApplicationRecord("com.example.android.beam")
                    });
            return msg;
        }
        // TODO: User friendly message?
        log.warn("Wifi connection info not available to send via NFC");
        return null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (processedIntents.contains(intent)) {
            log.info("Ignoring already processed intent = {}", intent);
            return;
        }

        log.info("Processing intent = {}", intent);
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present

        final WiFiNetworkConnectionInfo connectionInfo =
                mGson.fromJson(new String(msg.getRecords()[0].getPayload()), WiFiNetworkConnectionInfo.class);
        processedIntents.add(intent);

        DialogFragment newFragment = MultipleWifiHotspotAlertDialogFragment.newInstance(connectionInfo);
        newFragment.show(getFragmentManager(), "dialog");
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
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        log.info("User is leaving. Finishing activity");
        finish();
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
        if (wifiP2pGroupInfoListener != null) {
            wifiP2pGroupInfoListener = null;
        }

        // Tear down wifi hotspot if not needed anymore
        if (!isWifiHotspotRequiredForNextStep) {
            chameleonApplication.tearDownWifiHotspot();
            chameleonApplication.stopConnectionServer();
        }
    }

    @Override
    public void onClientRequest(final Socket clientSocket, final PeerMessage messageFromClient) {
        log.info("Starting connection establshed activity!");
        final Intent intent = new Intent(SendConnectionInfoNFCActivity.this, ConnectionEstablishedActivity.class);
        PeerInfo peerInfo = PeerInfo.builder()
                .ipAddress(clientSocket.getInetAddress())
                .port(ChameleonApplication.SERVER_PORT)
                .role(PeerInfo.Role.CREW_MEMBER)
                .userName(messageFromClient.getSenderUserName())
                .build();
        intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, gson.toJson(peerInfo));

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
                    connectionStatusTextView.setText("Enabling\nwifi");
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
                connectionStatusTextView.setText("Preparing\nto\nconnect");

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
                    public void onGroupInfoAvailable(WifiP2pGroup group) {

                        if (group != null && wiFiNetworkConnectionInfo == null) {

                            try {

                                log.info("Wifi hotspot details: SSID ({}), Passphrase ({}), InterfaceName ({}), GO ({}) ",
                                        group.getNetworkName(), group.getPassphrase(), group.getInterface(), group.getOwner());

                                wiFiNetworkConnectionInfo =
                                        WiFiNetworkConnectionInfo.builder()
                                                .SSID(group.getNetworkName())
                                                .passPhrase(group.getPassphrase())
                                                .serverIpAddress(getIpAddressForInterface(group.getInterface()).getHostAddress())
                                                .serverPort(ChameleonApplication.SERVER_PORT)
                                                .userName(getUserName())
                                                .build();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        progressBar.setVisibility(View.INVISIBLE);
                                        connectionStatusTextView.setText("Beam\nto\nconnect");
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
