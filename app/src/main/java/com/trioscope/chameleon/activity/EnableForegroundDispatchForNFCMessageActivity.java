package com.trioscope.chameleon.activity;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.trioscope.chameleon.R;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by rohitraghunathan on 7/6/15.
 */
@Slf4j
public abstract class EnableForegroundDispatchForNFCMessageActivity extends AppCompatActivity {

    protected NfcAdapter mNfcAdapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;
    private String[][] techListsArray;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        log.info("Retrieved mNfcAdapter {}", mNfcAdapter);

        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
        try {
            //Only specify mimeTypes we want to handle, others will be handled by Android's intent dispatch system.
            ndef.addDataType(getString(R.string.mime_type_nfc_connect_wifi));
        } catch (IntentFilter.MalformedMimeTypeException e) {
            throw new RuntimeException("fail", e);
        }

        intentFiltersArray = new IntentFilter[]{ndef,};
        techListsArray = new String[][]{new String[]{Ndef.class.getName()}};

    }

    protected boolean doesDeviceSupportNFC() {
        boolean ret =  getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);

        log.info("Device supports NFC: {}", ret);
        return ret;
    }


    @Override
    protected void onPause() {
        disableForegroundDispatch();
        super.onPause();
    }

    protected void disableForegroundDispatch() {
        if (doesDeviceSupportNFC())
            mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableForegroundDispatch();
    }

    protected void enableForegroundDispatch() {
        if (doesDeviceSupportNFC())
            mNfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, techListsArray);
    }

    @Override
    public void onNewIntent(Intent intent) {
        // Purposely left blank as we do not want to perform any action
        // Clients can override this with their implementation if needed.
    }

}
