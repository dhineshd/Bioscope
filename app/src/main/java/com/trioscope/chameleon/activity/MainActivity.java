package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;

import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.ui.GestureUtils;

import lombok.extern.slf4j.Slf4j;

import static android.view.View.OnClickListener;

@Slf4j
public class MainActivity extends AppCompatActivity {
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
                log.info("Start session button pressed");
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
                boolean isOpenHEnabled = preferences.getBoolean(getString(R.string.pref_codec_key), true);

                if (isOpenHEnabled) {
                    Intent i = new Intent(MainActivity.this, SendConnectionInfoActivity.class);
                    startActivity(i);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                } else {
                    log.info("OpenH264 is disabled, not going to move to SendConnectionInfoActivity");
                    new AlertDialog.Builder(MainActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setTitle(R.string.openh_disabled_warn_title)
                            .setMessage(R.string.openh_disabled_warn)
                            .setPositiveButton(R.string.ok, null)
                            .setCancelable(false)
                            .show();
                }
            }
        });

        final Button joinSessionButton = (Button) findViewById(R.id.button_main_join_session);
        joinSessionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                log.info("Join session button pressed");
                Intent i = new Intent(MainActivity.this, ReceiveConnectionInfoActivity.class);
                startActivity(i);
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
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
        overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
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

        log.info("Activity has resumed from background {}",
                PreferenceManager.getDefaultSharedPreferences(this).getAll());
    }
}
