package com.trioscope.chameleon.activity;

import android.app.DialogFragment;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.EnableNfcAndAndroidBeamDialogFragment;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.ui.GestureUtils;

import java.util.HashSet;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import static android.view.View.OnClickListener;

@Slf4j
public class MainActivity extends EnableForegroundDispatchForNFCMessageActivity {
    private ChameleonApplication chameleonApplication;
    private GestureDetectorCompat gestureDetector;
    private Set<Intent> processedIntents = new HashSet<Intent>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        chameleonApplication = (ChameleonApplication) getApplication();

        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                log.info("Detected gesture");
                if (GestureUtils.isSwipeUp(e1, e2, velocityX, velocityY)) {
                    log.info("Gesture is swipe up");
                    showLibraryActivity();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        log.info("Created main activity");

        final Button startSessionButton = (Button) findViewById(R.id.button_main_start_session);

        startSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Start sesion button pressed");
                Intent i = new Intent(MainActivity.this, SendConnectionInfoNFCActivity.class);
                startActivity(i);
            }
        });

        final ImageButton showLibraryButton = (ImageButton) findViewById(R.id.button_main_library);
        showLibraryButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showLibraryActivity();
            }
        });

        FfmpegVideoMerger merger = new FfmpegVideoMerger();
        merger.setContext(this);
    }

    private void showLibraryActivity() {
        Intent i = new Intent(MainActivity.this, VideoLibraryGridActivity.class);
        startActivity(i);
        overridePendingTransition(R.anim.abc_slide_in_bottom, R.anim.abc_slide_out_top);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Disabling until library is ready
        return gestureDetector.onTouchEvent(event);

        //return false;
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
        super.onPause();
        log.info("onPause invoked!");
    }

    @Override
    protected void onResume() {
        super.onResume();

        log.info("Activity has resumed from background {}", PreferenceManager.getDefaultSharedPreferences(this).getAll());

        if (doesDeviceSupportNFC()) {
            if (!mNfcAdapter.isEnabled() || !mNfcAdapter.isNdefPushEnabled()) {
                DialogFragment newFragment = EnableNfcAndAndroidBeamDialogFragment
                        .newInstance(mNfcAdapter.isEnabled(), mNfcAdapter.isNdefPushEnabled());
                newFragment.show(getFragmentManager(), "dialog");
            }
        }

        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        log.info("new intent received {}", intent);
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
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

        String msgAsJson = new String(msg.getRecords()[0].getPayload());

        //call ReceiveConnectionInfoActivity

        Intent i = new Intent(this, ReceiveConnectionInfoNFCActivity.class);
        i.putExtra(ConnectionEstablishedActivity.CONNECTION_INFO_AS_JSON_EXTRA, msgAsJson);
        startActivity(i);
        processedIntents.add(intent);
    }
}
