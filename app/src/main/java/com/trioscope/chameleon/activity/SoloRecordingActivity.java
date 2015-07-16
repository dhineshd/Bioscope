package com.trioscope.chameleon.activity;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.camera.VideoRecorder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SoloRecordingActivity extends ActionBarActivity {
    private ChameleonApplication chameleonApplication;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solo_recording);

        chameleonApplication = (ChameleonApplication) getApplication();

        chameleonApplication.createBackgroundRecorder();

        // Display camera preview
        RelativeLayout layout = (RelativeLayout) findViewById(R.id.relativeLayout_solo_preview);
        layout.addView(chameleonApplication.generatePreviewDisplay(
                chameleonApplication.getGlobalEglContextInfo()));

        final Button startSoloRecordingButton = (Button) findViewById(R.id.button_start_solo_recording);
        startSoloRecordingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                VideoRecorder videoRecorder = chameleonApplication.getVideoRecorder();
                log.info("Capture video button clicked");
                if (isRecording) {
                    chameleonApplication.finishVideoRecording();

                    isRecording = false;
                    log.info("isRecording is {}", isRecording);
                    startSoloRecordingButton.setText("Record!");
                } else {
                    // initialize video camera
                    if (chameleonApplication.prepareVideoRecorder()) {
                        videoRecorder.startRecording();
                        startSoloRecordingButton.setText("Done!");
                        isRecording = true;
                        log.info("isRecording is {}", isRecording);
                    } else {
                        // inform user
                        Toast.makeText(getApplicationContext(), "Could Not Record Video :(", Toast.LENGTH_LONG).show();
                        log.error("Failed to initialize media recorder");
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_solo_recording, menu);
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
