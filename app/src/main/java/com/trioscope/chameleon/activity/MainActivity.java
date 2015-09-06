package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.EnableNfcAndAndroidBeamDialogFragment;

import com.trioscope.chameleon.fragment.ReceiveConnectionInfoFragment;
import com.trioscope.chameleon.types.RecordingMetadata;

import com.trioscope.chameleon.types.SessionStatus;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static android.view.View.OnClickListener;

public class MainActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private static final Logger LOG = LoggerFactory.getLogger(MainActivity.class);
    private SurfaceView previewDisplay;
    private ChameleonApplication chameleonApplication;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.updateOrientation();

        setContentView(R.layout.activity_main);

        LOG.info("Created main activity");

        final Button startSessionButton = (Button) findViewById(R.id.button_main_start_session);

        startSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                LOG.info("Start sesion button pressed");
                chameleonApplication.setSessionStatus(SessionStatus.STARTED);
                Intent i = new Intent(MainActivity.this, SendConnectionInfoNFCActivity.class);
                startActivity(i);
            }
        });

        final Button editSettingsButton = (Button) findViewById(R.id.app_settings_button);

        editSettingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                LOG.info("Clicked on preferences button for {}", PreferencesActivity.class);
                Intent i = new Intent(MainActivity.this, PreferencesActivity.class);
                startActivity(i);
            }
        });

        final Button libraryButton = (Button) findViewById(R.id.library_button);

        libraryButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                LOG.info("Clicked on preferences button for {}", VideoLibraryActivity.class);
                Intent i = new Intent(MainActivity.this, VideoLibraryActivity.class);
                startActivity(i);
            }
        });

        chameleonApplication.preparePreview();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
        LOG.info("onPause: Activity is no longer in foreground");


        if (chameleonApplication.getPreviewDisplayer() != null)
            chameleonApplication.getPreviewDisplayer().stopPreview();



        super.onPause();
        LOG.info("Activity has been paused");
    }

    @Override
    protected void onResume() {
        super.onResume();

        LOG.info("Activity has resumed from background {}", PreferenceManager.getDefaultSharedPreferences(this).getAll());

        chameleonApplication.startConnectionServerIfNotRunning();

        if (!mNfcAdapter.isEnabled() || !mNfcAdapter.isNdefPushEnabled()) {

            DialogFragment newFragment = EnableNfcAndAndroidBeamDialogFragment.newInstance(
                    mNfcAdapter.isEnabled(), mNfcAdapter.isNdefPushEnabled());
            newFragment.show(getFragmentManager(), "dialog");
        }

        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {

            chameleonApplication.setSessionStatus(SessionStatus.STARTED);
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        LOG.info("new intent received {}", intent);
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

        String msgAsJson = new String(msg.getRecords()[0].getPayload());

        //call ReceiveConnectionInfoActivity

        Intent i = new Intent(this, ReceiveConnectionInfoNFCActivity.class);
        i.putExtra(ConnectionEstablishedActivity.CONNECTION_INFO_AS_JSON_EXTRA, msgAsJson);
        startActivity(i);
    }

    @Override
    protected void onStop() {
        LOG.info("onStop: Activity is no longer visible to user");
        if (chameleonApplication.getPreviewDisplayer() != null)
            chameleonApplication.getPreviewDisplayer().stopPreview();

        // If we are not connected, we can release network resources
        if (SessionStatus.DISCONNECTED.equals(chameleonApplication.getSessionStatus())) {
            LOG.info("Teardown initiated from MainActivity");
            chameleonApplication.cleanupAndExit();
        }

        super.onStop();
    }


    @Override
    public void onBackPressed() {
        LOG.info("User pressed back");

        //Disable NFC Foreground dispatch
        super.disableForegroundDispatch();

        super.onBackPressed();

        ((ChameleonApplication) getApplication()).cleanupAndExit();
    }

    private void addCameraPreviewSurface() {
        LOG.info("Creating surfaceView on thread {}", Thread.currentThread());

        ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_main_preview);
        previewDisplay = chameleonApplication.createPreviewDisplay();
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        layout.addView(previewDisplay, layoutParams);
    }
}
