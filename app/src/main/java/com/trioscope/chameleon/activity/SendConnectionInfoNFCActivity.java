package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.MultipleWifiHotspotAlertDialogFragment;
import com.trioscope.chameleon.stream.WifiConnectionInfoListener;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import lombok.extern.slf4j.Slf4j;

import static android.nfc.NdefRecord.createMime;

@Slf4j
public class SendConnectionInfoNFCActivity
        extends EnableForegroundDispatchForNFCMessageActivity
        implements NfcAdapter.CreateNdefMessageCallback, WifiConnectionInfoListener {
    private WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo;
    private Gson mGson = new Gson();
    private ChameleonApplication chameleonApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info_nfc);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        log.info("Created");
        chameleonApplication = (ChameleonApplication) getApplication();
        chameleonApplication.startConnectionServerIfNotRunning();

        // Register callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);
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
        if (wiFiNetworkConnectionInfo != null){
            String text = mGson.toJson(wiFiNetworkConnectionInfo, WiFiNetworkConnectionInfo.class);
            NdefMessage msg = new NdefMessage(
                    new NdefRecord[] { createMime(
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
        return  null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present

        final WiFiNetworkConnectionInfo connectionInfo =
                mGson.fromJson(new String(msg.getRecords()[0].getPayload()), WiFiNetworkConnectionInfo.class);


        DialogFragment newFragment = MultipleWifiHotspotAlertDialogFragment.newInstance(connectionInfo);
        newFragment.show(getFragmentManager(), "dialog");
    }

    @Override
    public void onWifiNetworkCreated(WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo) {
        this.wiFiNetworkConnectionInfo = wiFiNetworkConnectionInfo;
    }

    public void onBackPressed() {

        //Re-use MainActivity instance if already present. If not, create new instance.
        Intent openMainActivity= new Intent(this, MainActivity.class);
        openMainActivity.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(openMainActivity);
        super.onBackPressed();
    }
}
