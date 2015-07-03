package com.trioscope.chameleon.activity;

import android.content.Context;
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
import android.widget.Toast;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.apache.http.conn.util.InetAddressUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import lombok.extern.slf4j.Slf4j;

import static android.nfc.NdefRecord.createMime;

@Slf4j
public class SendConnectionInfoNFCActivity extends ActionBarActivity implements NfcAdapter.CreateNdefMessageCallback {

    private static final Logger LOG = LoggerFactory.getLogger(SendConnectionInfoNFCActivity.class);

    private Gson mGson = new Gson();
    private NfcAdapter mNfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info_nfc);

        final ChameleonApplication chameleonApplication = ((ChameleonApplication)getApplication());
        chameleonApplication.setDirector(true);

        final Context context = this.getApplicationContext();

        chameleonApplication.getServerEventListener().setContext(context);

        //Initialize wifiManager and wifiChannel

        chameleonApplication.initializeWifi();

        final WifiP2pManager wifiP2pManager = chameleonApplication.getWifiP2pManager();

        final WifiP2pManager.Channel wifiP2pChannel = chameleonApplication.getWifiP2pChannel();

        wifiP2pManager.createGroup(wifiP2pChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                LOG.debug("WifiP2P createGroup success");
            }

            @Override
            public void onFailure(int reasonCode) {
                LOG.warn("WifiP2P createGroup. ReasonCode: {}", reasonCode);
            }
        });

        wifiP2pManager.requestGroupInfo(wifiP2pChannel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {

                Toast.makeText(getApplicationContext(), "Created Wifi Network " + group.getNetworkName(),
                        Toast.LENGTH_LONG).show();
                LOG.debug("Wifi hotspot details {} ", group);

                LOG.info("Wifi hotspot details: SSID ({}), Passphrase ({}), InterfaceName ({}), GO ({}) ",
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
            }
        });


        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Register callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {

        //TODO: Ensure that wiFiNetworkConnectionInfo in ChameleonApplication is initialized before user can send via NFC.
        WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo = ((ChameleonApplication)getApplication()).getWiFiNetworkConnectionInfo();

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
}
