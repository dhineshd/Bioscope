package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SplashScreenActivity extends AppCompatActivity {

    /**
     * Duration of wait
     **/
    private final int SPLASH_DISPLAY_LENGTH = 1000;
    private final int SPLASH_DISPLAY_LENGTH_WITH_DOWNLOAD = 7000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        FfmpegVideoMerger merger = new FfmpegVideoMerger(this);
        if (!merger.hasRequiredComponents()) {
            // We need to do an initial download of codecs
            TextView text = (TextView) findViewById(R.id.splash_status_text);
            text.setText("Loading...");
            merger.prepare(new DownloadProgressUpdatable());
        } else {
             /* New Handler to start the Menu-Activity
              * and close this Splash-Screen after some seconds.
              */
            transitionAfterDelay(SPLASH_DISPLAY_LENGTH);
        }
    }

    private void transitionAfterDelay(int waitTimeMillis) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
            /* Create an Intent that will start the Menu-Activity. */
                Intent mainIntent = new Intent(SplashScreenActivity.this, UserLoginActivity.class);
                // create the transition animation - the images in the layouts
                // of both activities are defined with android:transitionName="logo"

                // get the common element for the transition in this activity
                final ImageView logoImageView = (ImageView) findViewById(R.id.splash_logo);

                ActivityOptionsCompat options = ActivityOptionsCompat
                        .makeSceneTransitionAnimation(SplashScreenActivity.this, logoImageView, "logo");
                startActivity(mainIntent);
                finish();
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
            }
        }, waitTimeMillis);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_splash_screen, menu);
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

    private class DownloadProgressUpdatable implements ProgressUpdatable {
        @Override
        public void onProgress(double progress, double outOf) {
            //log.info("Completed {}%", progress / outOf * 100.0);
        }

        @Override
        public void onCompleted() {
            log.info("Download completed!");
            transitionAfterDelay(SPLASH_DISPLAY_LENGTH);
        }

        public void onError() {
            log.info("Thread is {}", Thread.currentThread());
            TextView text = (TextView) findViewById(R.id.splash_status_text);
            text.setText("Error downloading necessary video encoders.\nPlease check your internet connection and restart the app.");
        }
    }
}
