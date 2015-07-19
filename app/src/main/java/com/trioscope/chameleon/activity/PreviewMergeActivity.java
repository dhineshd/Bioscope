package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.RecordingMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity {
    public static final String LOCAL_RECORDING_METADATA_KEY = "LOCAL_RECORDING_METADATA";
    public static final String REMOTE_RECORDING_METADATA_KEY = "REMOTE_RECORDING_METADATA";
    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);

        Intent intent = getIntent();
        final RecordingMetadata localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
        final RecordingMetadata remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);

        final int absDifferenceBetweenLocalAndRemoteStartTimes = (int) Math.abs(localRecordingMetadata.getStartTimeMillis()
                - remoteRecordingMetadata.getStartTimeMillis());
        final VideoView localRecordingVideoView = (VideoView) findViewById(R.id.videoView_local_video);
        localRecordingVideoView.setMediaController(null);
        localRecordingVideoView.setVideoPath(localRecordingMetadata.getAbsoluteFilePath());
        localRecordingVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                log.info("Local video prepared");
                if (localRecordingMetadata.getStartTimeMillis() < remoteRecordingMetadata.getStartTimeMillis()) {
                    // Adjust playback if local video started before the remote video

                    log.info("local started before remote by {} ms", absDifferenceBetweenLocalAndRemoteStartTimes);
                    mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                        @Override
                        public void onSeekComplete(MediaPlayer mediaPlayer) {
                            log.info("local video onSeek complete");
                        }
                    });
                    mediaPlayer.seekTo(absDifferenceBetweenLocalAndRemoteStartTimes);
                }
            }
        });

        final VideoView remoteRecordingVideoView = (VideoView) findViewById(R.id.videoView_remote_video);
        remoteRecordingVideoView.setVideoPath(remoteRecordingMetadata.getAbsoluteFilePath());
        remoteRecordingVideoView.setMediaController(null);
        remoteRecordingVideoView.setZOrderMediaOverlay(true);
        remoteRecordingVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                log.info("Remote video prepared");

                // Disable sound for remote video playback
                //mediaPlayer.setVolume(0f, 0f);
                if (localRecordingMetadata.getStartTimeMillis() > remoteRecordingMetadata.getStartTimeMillis()) {
                    // Adjust playback if remote video started before the local video
                    log.info("remote started before local by {} ms", absDifferenceBetweenLocalAndRemoteStartTimes);
                    mediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                        @Override
                        public void onSeekComplete(MediaPlayer mediaPlayer) {
                            log.info("remote video onSeek complete : current position = {}", mediaPlayer.getCurrentPosition());
                            localRecordingVideoView.start();
                            mediaPlayer.start();
                            log.info("remote video : current position = {}", mediaPlayer.getCurrentPosition());
                        }
                    });
                    mediaPlayer.seekTo(absDifferenceBetweenLocalAndRemoteStartTimes);
                }
            }
        });

        Button startMergeButton = (Button) findViewById(R.id.button_preview_and_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Wait and start preview since mediaPlayer seek takes time
                // and there is no OnSeekCompletedListener.
                //localRecordingVideoView.start();
                //remoteRecordingVideoView.start();
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
