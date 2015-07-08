package com.trioscope.chameleon.activity;

import android.app.FragmentManager;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.ReceiveConnectionInfoFragment;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReceiveConnectionInfoNFCActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private final static Logger LOG = LoggerFactory.getLogger(ReceiveConnectionInfoNFCActivity.class);
    private Gson mGson = new Gson();
    private TextView mTextViewConnectionStatus;
    private TextView mTextViewNfcInstructions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receive_connection_info_nfc);

        mTextViewConnectionStatus = (TextView) findViewById(R.id.textView_connection_status);
        mTextViewNfcInstructions = (TextView) findViewById(R.id.textView_nfc_instructions);

        LOG.debug("ReceiveConnectionInfoNFCActivity {}", this);
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
    public void onResume() {
        super.onResume();

        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {

            if(mTextViewConnectionStatus.getVisibility() == TextView.INVISIBLE) {
                mTextViewConnectionStatus.setVisibility(TextView.VISIBLE);
            }

            if(mTextViewNfcInstructions.getVisibility() == TextView.VISIBLE) {
                mTextViewNfcInstructions.setVisibility(TextView.INVISIBLE);
            }


            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present

        final WiFiNetworkConnectionInfo connectionInfo =
                mGson.fromJson(new String(msg.getRecords()[0].getPayload()), WiFiNetworkConnectionInfo.class);

        // find the fragment from previous instance of activity (if any)
        FragmentManager fm = getFragmentManager();
        ReceiveConnectionInfoFragment fragment = (ReceiveConnectionInfoFragment) fm.findFragmentById(R.id.fragment_receive_connection_info);
        fragment.establishConnection(connectionInfo);
    }

}
