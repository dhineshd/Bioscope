package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.ui.GestureUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TutorialTwoActivity extends AppCompatActivity {

    private ImageButton nextButton;

    private GestureDetectorCompat gestureDetector;

    private Button skipButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial_two);

        nextButton = (ImageButton) findViewById(R.id.next_tutorial_two);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                showNextAndDestroyCurrentActivity();
                finish();
            }
        });

        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                log.info("Detected gesture");
                if (GestureUtils.isSwipeLeft(e1, e2, velocityX, velocityY)) {
                    log.info("Gesture is swipe left");
                    showNextAndDestroyCurrentActivity();
                    return true;
                } else if (GestureUtils.isSwipeRight(e1, e2, velocityX, velocityY)) {
                    log.info("Gesture is swipe right");
                    showPreviousAndDestroyCurrentActivity();
                    return true;
                }
                return super.onFling(e1, e2, velocityX, velocityY);
            }
        });

        skipButton = (Button) findViewById(R.id.skip_tutorial_two);

        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                boolean invokedFromBeginningOfApp = getIntent().getBooleanExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, false);

                // if it is not invoked from settings; it's invoked at the beginning of the app, go to userlogin
                if(invokedFromBeginningOfApp) {

                    setTutorialAlreadyShown();
                    startActivity(new Intent(TutorialTwoActivity.this, UserLoginActivity.class));
                }

                finish();
            }
        });
    }

    private void setTutorialAlreadyShown() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(ChameleonApplication.TUTORIAL_SHOWN_PREFERENCE_KEY, true);
        editor.commit();
    }

    private void showNextAndDestroyCurrentActivity() {

        boolean invokedFromBeginningOfApp = getIntent().getBooleanExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, false);

        Intent i = new Intent(TutorialTwoActivity.this, TutorialThreeActivity.class);
        i.putExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, invokedFromBeginningOfApp);
        startActivity(i);

        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private void showPreviousAndDestroyCurrentActivity() {
        boolean invokedFromBeginningOfApp = getIntent().getBooleanExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, false);

        Intent i = new Intent(TutorialTwoActivity.this, TutorialOneActivity.class);
        i.putExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, invokedFromBeginningOfApp);
        startActivity(i);

        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        finish();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_tutorial_two, menu);
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
}
