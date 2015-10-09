package com.trioscope.chameleon.activity;


import android.app.FragmentManager;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ProgressBar;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.fragment.FfmpegTaskFragment;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/19/15.
 */
@Slf4j
public class MergeVideosActivity extends AppCompatActivity implements ProgressUpdatable, SurfaceHolder.Callback {
    public static final String MAJOR_VIDEO_METADATA_KEY = "MAJOR_VIDEO_METADATA";
    public static final String MINOR_VIDEO_METADATA_KEY = "MINOR_VIDEO_METADATA";
    private static final String TASK_FRAGMENT_TAG = "ASYNC_TASK_FRAGMENT_TAG";

    private FfmpegTaskFragment taskFragment;
    private MediaPlayer majorVideoMediaPlayer, minorVideoMediaPlayer;
    private Gson gson = new Gson();
    private RecordingMetadata majorVideoMetadata;
    private RecordingMetadata minorVideoMetadata;
    private long majorVideoStartedBeforeMinorVideoOffsetMillis;

    private SurfaceHolder majorVideoHolder, minorVideoHolder;
    private SurfaceView minorVideoSurfaceView;
    private boolean majorVideoMediaPlayerPrepared, minorVideoMediaPlayerPrepared;
    private File outputFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Remove title bar
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_merge);

        if (getSupportActionBar() != null) {
            log.info("Removing actionbar");
            getSupportActionBar().hide();
        }

        log.info("Activity is created");

        final Intent intent = getIntent();

        majorVideoMetadata = gson.fromJson(
                intent.getStringExtra(MAJOR_VIDEO_METADATA_KEY), RecordingMetadata.class);
        minorVideoMetadata = gson.fromJson(
                intent.getStringExtra(MINOR_VIDEO_METADATA_KEY), RecordingMetadata.class);

        // We will use this to adjust local and remote videos to show/merge them in sync
        majorVideoStartedBeforeMinorVideoOffsetMillis =
                (int) (minorVideoMetadata.getStartTimeMillis()
                        - majorVideoMetadata.getStartTimeMillis());

        final ChameleonApplication chameleonApplication = (ChameleonApplication) getApplication();

        printArchInfo();

        FragmentManager fm = getFragmentManager();
        taskFragment = (FfmpegTaskFragment) fm.findFragmentByTag(TASK_FRAGMENT_TAG);

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change.
        if (taskFragment == null) {
            outputFile = chameleonApplication.getOutputMediaFile("Merged.mp4");
            taskFragment = FfmpegTaskFragment.newInstance(
                    intent.getStringExtra(MAJOR_VIDEO_METADATA_KEY),
                    intent.getStringExtra(MINOR_VIDEO_METADATA_KEY),
                    majorVideoStartedBeforeMinorVideoOffsetMillis,
                    outputFile.getAbsolutePath());
            fm.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
        } else {
            log.info("Task fragment exists - reusing (device rotated)");
        }

        // Local video will be shown on outer player and remote video on inner player
        majorVideoMediaPlayer = new MediaPlayer();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.outerVideoMerge);
        majorVideoHolder = surfaceView.getHolder();
        majorVideoHolder.addCallback(this);

        minorVideoMediaPlayer = new MediaPlayer();
        minorVideoSurfaceView = (SurfaceView) findViewById(R.id.innerVideoMerge);
        minorVideoHolder = minorVideoSurfaceView.getHolder();
        minorVideoHolder.addCallback(this);
    }

    private void printArchInfo() {
        String sysArch = System.getProperty("os.arch");
        log.info("System architecture is {}", sysArch);
    }

    private void printDir(String dir) {
        File dirFile = new File(dir);
        log.info("Dir exists? {} isDirectory? {}", dirFile.exists(), dirFile.isDirectory());
        File[] list = dirFile.listFiles();

        for (File f : list) {
            log.info("File in directory: {}", f.toString());
        }
    }

    @Override
    public void onProgress(double progress, double outOf) {
        int progressPerc = getPercent(progress, outOf);
        ProgressBar bar = (ProgressBar) findViewById(R.id.ffmpeg_progress_bar);
        if (View.VISIBLE != bar.getVisibility()) {
            bar.setVisibility(View.VISIBLE);
        }
        bar.setProgress(progressPerc);

        log.info("Now {}% done", progressPerc);
    }

    private static int getPercent(double progress, double outOf) {
        return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
    }

    @Override
    public void onCompleted() {
        ProgressBar bar = (ProgressBar) findViewById(R.id.ffmpeg_progress_bar);
        bar.setVisibility(View.GONE);
        log.info("FFMPEG Completed! Going to video library now!");

        //Send a broadcast about the newly added video file for Gallery Apps to recognize the video
        Intent addVideoIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        addVideoIntent.setData(Uri.fromFile(outputFile));
        sendBroadcast(addVideoIntent);

        Intent i = new Intent(MergeVideosActivity.this, VideoLibraryActivity.class);
        startActivity(i);
    }

    @Override
    public void onError() {
        // TODO
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        // TODO : Consider prepareAsync() if following code hogs UI thread

        if (holder == majorVideoHolder) {
            minorVideoSurfaceView.setZOrderOnTop(true);

            if (majorVideoMediaPlayer.isPlaying()) {
                majorVideoMediaPlayer.reset();
            }

            //majorVideoMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            majorVideoMediaPlayer.setDisplay(majorVideoHolder);

            try {
                majorVideoMediaPlayer.setDataSource(majorVideoMetadata.getAbsoluteFilePath());
                majorVideoMediaPlayer.prepare();
                majorVideoMediaPlayerPrepared = true;
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                log.error("Failed to start outer media player", e);
            }
        }

        if (holder == minorVideoHolder) {
            log.info("Starting inner video holder");
            if (minorVideoMediaPlayer.isPlaying()) {
                minorVideoMediaPlayer.reset();
            }

            //minorVideoMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            minorVideoMediaPlayer.setDisplay(minorVideoHolder);

            try {
                minorVideoMediaPlayer.setDataSource(minorVideoMetadata.getAbsoluteFilePath());
                minorVideoMediaPlayer.prepare();
                minorVideoMediaPlayerPrepared = true;
            } catch (IllegalArgumentException | IllegalStateException | IOException e) {
                log.error("Failed to start inner media player", e);
            }
        }

        // Begin both playback at same time when they are ready
        if (minorVideoMediaPlayerPrepared && majorVideoMediaPlayerPrepared) {

            log.info("mediaplayer prepared : currentThread = {}", Thread.currentThread());

            // Playback already done in PreviewMergeActivity.
            // TODO : Remove all mediaplayer code later
//            majorVideoMediaPlayer.start();
//            minorVideoMediaPlayer.start();

            log.info("local started before remote by {} ms",
                    majorVideoStartedBeforeMinorVideoOffsetMillis);
        }

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
