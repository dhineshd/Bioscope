package com.trioscope.chameleon.activity;

import android.content.Context;
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
import android.widget.ImageButton;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.ui.GestureUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TutorialFiveActivity extends AppCompatActivity {

    private ImageButton nextButton;

    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial_five);

        nextButton = (ImageButton) findViewById(R.id.next_tutorial_five);
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Set tutorial already shown
                setTutorialAlreadyShown();
                showNextAndDestroyCurrentActivity();
            }
        });

        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                log.info("Detected gesture");
                if (GestureUtils.isSwipeLeft(e1, e2, velocityX, velocityY)) {
                    log.info("Gesture is swipe left");

                    //Set tutorial already shown
                    setTutorialAlreadyShown();
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
    }

    private void showNextAndDestroyCurrentActivity() {

        boolean invokedFromBeginningOfApp = getIntent().getBooleanExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, false);

        // if it's invoked at the beginning of the app, go to userlogin
        if(invokedFromBeginningOfApp) {

            startActivity(new Intent(TutorialFiveActivity.this, UserLoginActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        finish();
    }

    private void showPreviousAndDestroyCurrentActivity() {

        boolean invokedFromSettings = getIntent().getBooleanExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, false);

        Intent i = new Intent(TutorialFiveActivity.this, TutorialFourActivity.class);
        i.putExtra(ChameleonApplication.TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY, invokedFromSettings);
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
        getMenuInflater().inflate(R.menu.menu_tutorial_five, menu);
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

    private void setTutorialAlreadyShown() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(ChameleonApplication.TUTORIAL_SHOWN_PREFERENCE_KEY, true);
        editor.commit();
    }
}
