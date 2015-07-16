package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.VideoView;

import com.trioscope.chameleon.R;

import java.io.File;

public class PreviewMergeActivity extends ActionBarActivity {
    public static final String LOCAL_RECORDING_FILENAME_KEY = "LOCAL_RECORDING";
    public static final String REMOTE_RECORDING_FILENAME_KEY = "REMOTE_RECORDING";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);

        Intent intent = getIntent();
        File localRecording = new File(intent.getStringExtra(LOCAL_RECORDING_FILENAME_KEY));
        File remoteRecording = new File(intent.getStringExtra(REMOTE_RECORDING_FILENAME_KEY));

        final VideoView localRecordingVideoView = (VideoView) findViewById(R.id.videoView_local_video);
        localRecordingVideoView.setMediaController(null);
        localRecordingVideoView.setVideoPath(localRecording.getAbsolutePath());
        localRecordingVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                localRecordingVideoView.start();
            }
        });
        final VideoView remoteRecordingVideoView = (VideoView) findViewById(R.id.videoView_remote_video);
        remoteRecordingVideoView.setVideoPath(remoteRecording.getAbsolutePath());
        remoteRecordingVideoView.setMediaController(null);
        remoteRecordingVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                remoteRecordingVideoView.start();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_preview_merge, menu);
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
