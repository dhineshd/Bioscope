package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.MultipleWifiHotspotAlertDialogFragment;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import lombok.extern.slf4j.Slf4j;

import static android.nfc.NdefRecord.createMime;

@Slf4j
public class SendConnectionInfoNFCActivity extends ActionBarActivity implements NfcAdapter.CreateNdefMessageCallback {
    private Gson mGson = new Gson();
    private NfcAdapter mNfcAdapter;
    //private String mimeTypeNFCWifiConnect = getString(R.string.mime_type_nfc_connect_wifi);
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;
    String[][] techListsArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_connection_info_nfc);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Register callback
        mNfcAdapter.setNdefPushMessageCallback(this, this);


        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            //Only specify mimeTypes we want to handle, others will be handled by Android's intent dispatch system.
            ndef.addDataType(getString(R.string.mime_type_nfc_connect_wifi));
        }
        catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }

        intentFiltersArray = new IntentFilter[] {ndef, };
        techListsArray = new String[][] { new String[] { Ndef.class.getName() } };
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

        //TODO: Ensure that wiFiNetworkConnectionInfo in ChameleonApplication is initialized before user can send via NFC.
        WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo =
                ((ChameleonApplication) getApplication()).getWiFiNetworkConnectionInfo();

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

    public void onPause() {
        super.onPause();
        mNfcAdapter.disableForegroundDispatch(this);
    }

    public void onResume() {
        super.onResume();
        mNfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
    }

    public void onNewIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present

        final WiFiNetworkConnectionInfo connectionInfo =
                new Gson().fromJson(new String(msg.getRecords()[0].getPayload()), WiFiNetworkConnectionInfo.class);


        DialogFragment newFragment = MultipleWifiHotspotAlertDialogFragment.newInstance(connectionInfo);
        newFragment.show(getFragmentManager(), "dialog");
    }

}
