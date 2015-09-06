package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.EnableNfcAndAndroidBeamDialogFragment;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.types.SessionStatus;

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

        final Button joinSessionButton = (Button) findViewById(R.id.button_main_join_session);

        joinSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                chameleonApplication.setSessionStatus(SessionStatus.STARTED);
                Intent i = new Intent(MainActivity.this, ReceiveConnectionInfoNFCActivity.class);
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

        final Button mergePreviewButton = (Button) findViewById(R.id.button_main_merge_preview);

        mergePreviewButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordingMetadata localRecordingMetadata = RecordingMetadata.builder()
                        .absoluteFilePath("/storage/sdcard0/DCIM/Chameleon/LocalVideo.mp4")
                        .startTimeMillis(System.currentTimeMillis())
                        .build();
                RecordingMetadata remoteRecordingMetadata = RecordingMetadata.builder()
                        .absoluteFilePath("/storage/sdcard0/DCIM/Chameleon/PeerVideo.mp4")
                        .startTimeMillis(System.currentTimeMillis())
                        .build();
                Intent intent = new Intent(getApplicationContext(), PreviewMergeActivity.class);
                intent.putExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY, gson.toJson(localRecordingMetadata));
                intent.putExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                startActivity(intent);
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
        super.onPause();

        if (chameleonApplication.getPreviewDisplayer() != null)
            chameleonApplication.getPreviewDisplayer().stopPreview();

        // If we are not connected, we can release network resources
        if (SessionStatus.DISCONNECTED.equals(chameleonApplication.getSessionStatus())) {
            LOG.info("Teardown initiated from MainActivity");
            chameleonApplication.cleanupAndExit();
        }

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
    }

    @Override
    protected void onStop() {
        LOG.info("onStop: Activity is no longer visible to user");
        if (chameleonApplication.getPreviewDisplayer() != null)
            chameleonApplication.getPreviewDisplayer().stopPreview();
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
