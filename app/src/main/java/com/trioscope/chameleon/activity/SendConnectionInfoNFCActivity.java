package com.trioscope.chameleon.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.apache.http.conn.util.InetAddressUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import lombok.extern.slf4j.Slf4j;

import static android.nfc.NdefRecord.createMime;

@Slf4j
public class SendConnectionInfoNFCActivity extends ActionBarActivity implements NfcAdapter.CreateNdefMessageCallback {

    private Gson mGson = new Gson();
    private NfcAdapter mNfcAdapter;
    private ChameleonApplication chameleonApplication;
    private TextView connectionStatusTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info_nfc);

        connectionStatusTextView = (TextView) findViewById(R.id.textView_sender_connection_status);

        chameleonApplication = ((ChameleonApplication)getApplication());
        chameleonApplication.setDirector(true);

        final Context context = this.getApplicationContext();

        chameleonApplication.getServerEventListener().setContext(context);

        // If connection information not present, need to create new Wifi hotspot
        if (chameleonApplication.getWiFiNetworkConnectionInfo() == null){
            enableWifiAndCreateHotspot();
        }

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Register callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);

    }

    private void enableWifiAndCreateHotspot(){

        //TODO: Check if p2p is supported on device. What to do? Is there a way in AndroidManifest to check this?

        // Turn on Wifi device (if not already on)
        final WifiManager wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

        if (wifiManager.isWifiEnabled()){

            log.info("Wifi already enabled..");
            createWifiHotspot();

        } else {
            connectionStatusTextView.setText("Enabling WiFi..");
            // Enable and wait for Wifi state change
            wifiManager.setWifiEnabled(true);

            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

            BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    log.info("onReceive intent = " + intent.getAction());
                    if(wifiManager.isWifiEnabled()) {

                        // Done with checking Wifi state
                        unregisterReceiver(this);
                        log.info("Wifi enabled!!");

                        createWifiHotspot();
                    }
                }
            };
            // listen for change in Wifi state
            registerReceiver(broadcastReceiver, filter);
        }
    }

    private void createWifiHotspot(){

        connectionStatusTextView.setText("Creating WiFi network..");

        log.info("Creating Wifi hotspot");

        //Initialize wifiManager and wifiChannel
        chameleonApplication.initializeWifi();

        final WifiP2pManager wifiP2pManager = chameleonApplication.getWifiP2pManager();

        final WifiP2pManager.Channel wifiP2pChannel = chameleonApplication.getWifiP2pChannel();

        //disconnect(wifiP2pManager, wifiP2pChannel);

        // Remove any existing hotspots
        wifiP2pManager.removeGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                log.debug("WifiP2P removeGroup success");

                // Create new Wifi hotspot
                createWifiP2PGroup(wifiP2pManager, wifiP2pChannel);
            }

            @Override
            public void onFailure(int reasonCode) {
                log.warn("WifiP2P removeGroup. ReasonCode: {}", reasonCode);
                createWifiP2PGroup(wifiP2pManager, wifiP2pChannel);
            }
        });

    }

    private void createWifiP2PGroup(
            final WifiP2pManager wifiP2pManager,
            final WifiP2pManager.Channel wifiP2pChannel){

        wifiP2pManager.createGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                log.info("WifiP2P createGroup success");
                // Calling requestGroupInfo immediately after createGroup success results
                // in group info being null. Adding some sleep seems to work.
                // TODO : Is there a better way?
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
                getAndPersistWifiConnectionInfo(wifiP2pManager, wifiP2pChannel);
            }

            @Override
            public void onFailure(int reasonCode) {
                log.warn("WifiP2P createGroup. ReasonCode: {}", reasonCode);
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
                    Toast.makeText(getApplicationContext(), "Created Wifi Network " + group.getNetworkName(),
                            Toast.LENGTH_LONG).show();
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
                    chameleonApplication.setWiFiNetworkConnectionInfo(nci);

                    connectionStatusTextView.setText("Ready to send connection info..");

                }
            }
        });
    }


    private InetAddress getIpAddressForInterface(final String networkInterfaceName){
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
            throw new RuntimeException(e);
        }
        return null;
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

    public void disconnect(final WifiP2pManager wifiP2pManager, final WifiP2pManager.Channel wifiP2pChannel) {
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            wifiP2pManager.requestGroupInfo(wifiP2pChannel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && wifiP2pManager != null && wifiP2pChannel != null
                            && group.isGroupOwner()) {
                        wifiP2pManager.removeGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {

                            @Override
                            public void onSuccess() {
                                log.debug("removeGroup onSuccess -");
                            }

                            @Override
                            public void onFailure(int reason) {
                                log.debug("removeGroup onFailure -" + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {

        //TODO: Ensure that wiFiNetworkConnectionInfo in ChameleonApplication is initialized before user can send via NFC.
        WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo = ((ChameleonApplication)getApplication()).getWiFiNetworkConnectionInfo();

        if (wiFiNetworkConnectionInfo != null){
            String text = mGson.toJson(wiFiNetworkConnectionInfo, WiFiNetworkConnectionInfo.class);
            NdefMessage msg = new NdefMessage(
                    //TODO: Move mimeType to strings.xml
                    new NdefRecord[] { createMime(
                            "application/com.trioscope.chameleon.connect.wifi", text.getBytes())
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
        return  null;
    }
}
