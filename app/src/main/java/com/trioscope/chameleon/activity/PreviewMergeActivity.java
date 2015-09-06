package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.VideoView;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.RecordingMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewMergeActivity extends EnableForegroundDispatchForNFCMessageActivity
        implements MediaController.MediaPlayerControl{
    private final Gson gson = new Gson();
    private VideoView majorVideoView;
    private VideoView minorVideoView;
    private String majorVideoViewVideoPath, minorVideoViewVideoPath;
    private int majorVideoAheadOfMinorVideoByMillis;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_merge);

        Intent intent = getIntent();
        final RecordingMetadata localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
        final RecordingMetadata remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(ConnectionEstablishedActivity.REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);

        majorVideoAheadOfMinorVideoByMillis = (int) (remoteRecordingMetadata.getStartTimeMillis() -
                localRecordingMetadata.getStartTimeMillis());

        majorVideoView = (VideoView) findViewById(R.id.videoView_local_video);
        minorVideoView = (VideoView) findViewById(R.id.videoView_remote_video);
        minorVideoView.setZOrderMediaOverlay(true);

        startVideoViews(
                localRecordingMetadata.getAbsoluteFilePath(),
                remoteRecordingMetadata.getAbsoluteFilePath(),
                majorVideoAheadOfMinorVideoByMillis);

        Button playMergePreviewButton = (Button) findViewById(R.id.button_play_merge_preview);
        playMergePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startVideoViews(
                        majorVideoViewVideoPath,
                        minorVideoViewVideoPath,
                        majorVideoAheadOfMinorVideoByMillis);
            }
        });

        Button startMergeButton = (Button) findViewById(R.id.button_merge);
        startMergeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), MergeVideosActivity.class);
                // Decide which is major video depending on user's latest choice of preview playback
                if (localRecordingMetadata.getAbsoluteFilePath().equalsIgnoreCase(majorVideoViewVideoPath)) {
                    intent.putExtra(MergeVideosActivity.MAJOR_VIDEO_METADATA_KEY, gson.toJson(localRecordingMetadata));
                    intent.putExtra(MergeVideosActivity.MINOR_VIDEO_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                } else {
                    intent.putExtra(MergeVideosActivity.MAJOR_VIDEO_METADATA_KEY, gson.toJson(remoteRecordingMetadata));
                    intent.putExtra(MergeVideosActivity.MINOR_VIDEO_METADATA_KEY, gson.toJson(localRecordingMetadata));
                }
                startActivity(intent);
            }
        });

        Button swapMergePreviewButton = (Button) findViewById(R.id.button_swap_merge_preview);
        swapMergePreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // swapping the video paths
                majorVideoAheadOfMinorVideoByMillis = -majorVideoAheadOfMinorVideoByMillis;
                startVideoViews(minorVideoViewVideoPath, majorVideoViewVideoPath, majorVideoAheadOfMinorVideoByMillis);
            }
        });
    }

    private void startVideoViews(
            final String majorVideoPath,
            final String minorVideoPath,
            final int majorVideoAheadOfMinorVideoByTimeMillis) {

        log.info("outer video ahead of local videos = {} ms", majorVideoAheadOfMinorVideoByTimeMillis);

        if (majorVideoView.isPlaying()) {
            majorVideoView.stopPlayback();
        }
        if (minorVideoView.isPlaying()) {
            minorVideoView.stopPlayback();
        }

        majorVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(final MediaPlayer mpMajor) {
                minorVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mpMinor) {
                        mpMinor.setVolume(0.0f, 0.0f);
                        mpMajor.start();
                        mpMinor.start();
                    }
                });
            }
        });

        majorVideoViewVideoPath = majorVideoPath;
        majorVideoView.setVideoPath(majorVideoViewVideoPath);
        minorVideoViewVideoPath = minorVideoPath;
        minorVideoView.setVideoPath(minorVideoViewVideoPath);
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

    @Override
    public void start() {
        if (majorVideoView != null && minorVideoView != null) {
            startVideoViews(majorVideoViewVideoPath, minorVideoViewVideoPath,
                    majorVideoAheadOfMinorVideoByMillis);
        }
    }

    @Override
    public void pause() {
        if (majorVideoView != null && minorVideoView != null) {
            majorVideoView.pause();
            minorVideoView.pause();
        }
    }

    @Override
    public int getDuration() {
        if (majorVideoView != null && minorVideoView != null) {
            return Math.min(majorVideoView.getDuration(), minorVideoView.getDuration());
        }
        return 0;
    }

    @Override
    public int getCurrentPosition() {
        if (majorVideoView != null && minorVideoView != null) {
            return Math.min(majorVideoView.getCurrentPosition(), minorVideoView.getCurrentPosition());
        }
        return 0;
    }

    @Override
    public void seekTo(int pos) {

    }

    @Override
    public boolean isPlaying() {
        return (majorVideoView != null && majorVideoView.isPlaying());
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }
}
