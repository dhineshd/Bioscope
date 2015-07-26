package com.trioscope.chameleon.activity;


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.NotificationIds;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.MergeConfiguration;
import com.trioscope.chameleon.util.merge.MergeConfiguration.MergeConfigurationBuilder;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/19/15.
 */
public class MergeVideosActivity extends AppCompatActivity implements ProgressUpdatable, SurfaceHolder.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(MergeVideosActivity.class);
    public static final String LOCAL_RECORDING_METADATA_KEY = "LOCAL_RECORDING_METADATA";
    public static final String REMOTE_RECORDING_METADATA_KEY = "REMOTE_RECORDING_METADATA";
    private static final String TASK_FRAGMENT_TAG = "ASYNC_TASK_FRAGMENT_TAG";
    private static final int MERGING_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS.getId();
    private static final int COMPLETED_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS_COMPLETE.getId();
    private FfmpegTaskFragment taskFragment;
    private MediaPlayer outerMediaPlayer, innerMediaPlayer;
    private Gson gson = new Gson();
    private RecordingMetadata localRecordingMetadata;
    private RecordingMetadata remoteRecordingMetadata;

    private SurfaceHolder outerVideoHolder, innerVideoHolder;
    private SurfaceView innerSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Remove title bar
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_merge);

        if (getSupportActionBar() != null) {
            LOG.info("Removing actionbar");
            getSupportActionBar().hide();
        }

        LOG.info("Activity is created");

        final Button continueSessionButton = (Button) findViewById(R.id.button_continue_session);

        continueSessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), ConnectionEstablishedActivity.class);
                intent.putExtra(ConnectionEstablishedActivity.PEER_INFO, getIntent().getStringExtra(ConnectionEstablishedActivity.PEER_INFO));
                startActivity(intent);
            }
        });

        Intent intent = getIntent();

        localRecordingMetadata = gson.fromJson(
                intent.getStringExtra(LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
        remoteRecordingMetadata = gson.fromJson(
                intent.getStringExtra(REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);

        printArchInfo();
        runFfmpeg();

        FragmentManager fm = getFragmentManager();
        taskFragment = (FfmpegTaskFragment) fm.findFragmentByTag(TASK_FRAGMENT_TAG);

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change.
        if (taskFragment == null) {
            taskFragment = FfmpegTaskFragment.newInstance(
                    intent.getStringExtra(LOCAL_RECORDING_METADATA_KEY),
                    intent.getStringExtra(REMOTE_RECORDING_METADATA_KEY));
            fm.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
        } else {
            LOG.info("Task fragment exists - reusing (device rotated)");
        }


        outerMediaPlayer = new MediaPlayer();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.outerVideoMerge);
        outerVideoHolder = surfaceView.getHolder();
        outerVideoHolder.addCallback(this);
        innerMediaPlayer = new MediaPlayer();
        innerSurfaceView = (SurfaceView) findViewById(R.id.innerVideoMerge);
        innerVideoHolder = innerSurfaceView.getHolder();
        innerVideoHolder.addCallback(this);
    }

    private void printArchInfo() {
        String sysArch = System.getProperty("os.arch");
        LOG.info("System architecture is {}", sysArch);
    }

    private void runFfmpeg() {
    }

    private void printDir(String dir) {
        File dirFile = new File(dir);
        LOG.info("Dir exists? {} isDirectory? {}", dirFile.exists(), dirFile.isDirectory());
        File[] list = dirFile.listFiles();

        for (File f : list) {
            LOG.info("File in directory: {}", f.toString());
        }
    }

    @Override
    public void onProgress(double progress, double outOf) {
        int progressPerc = (int) Math.ceil(100.0 * progress / outOf);
        ProgressBar bar = (ProgressBar) findViewById(R.id.ffmpeg_progress_bar);
        bar.setProgress(progressPerc);

        LOG.info("Now {}% done", String.format("%.2f", progress / outOf * 100.0));

    }

    @Override
    public void onCompleted() {
        LOG.info("FFMPEG Completed! Allowing user to share");

        // Make share button available
        ImageButton shareButton = (ImageButton) findViewById(R.id.share_button);
        shareButton.setVisibility(View.VISIBLE);

        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LOG.info("User wants to share the video");
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                File mergedFile = new File("/storage/emulated/0/DCIM/Camera/Merged.mp4");
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(mergedFile));
                shareIntent.setType("image/jpeg");
                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.share_via)));
            }
        });
    }


    int created = 0;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

        if (holder == outerVideoHolder) {
            if (outerMediaPlayer.isPlaying()) {
                outerMediaPlayer.reset();
            }

            outerMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            outerMediaPlayer.setDisplay(outerVideoHolder);

            try {
                outerMediaPlayer.setDataSource(localRecordingMetadata.getAbsoluteFilePath());
                outerMediaPlayer.prepare();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            outerMediaPlayer.start();
            innerSurfaceView.setZOrderOnTop(true);
        }


        if (holder == innerVideoHolder) {
            innerSurfaceView.setZOrderOnTop(true);
            LOG.info("Starting inner video holder");
            if (innerMediaPlayer.isPlaying()) {
                innerMediaPlayer.reset();
            }

            innerMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            innerMediaPlayer.setDisplay(innerVideoHolder);

            try {
                innerMediaPlayer.setDataSource(remoteRecordingMetadata.getAbsoluteFilePath());
                innerMediaPlayer.prepare();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            innerMediaPlayer.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Slf4j
    public static class FfmpegTaskFragment extends Fragment implements ProgressUpdatable {
        private Context currentContext;
        private FfmpegVideoMerger videoMerger;
        private long startTime;
        private Notification.Builder notificationBuilder;
        private RecordingMetadata localRecordingMetadata;
        private RecordingMetadata remoteRecordingMetadata;
        private Gson gson = new Gson();

        public static FfmpegTaskFragment newInstance(
                final String serializedLocalRecordingMetadata,
                final String serializedRemoteRecordingMetadata) {
            FfmpegTaskFragment f = new FfmpegTaskFragment();
            Bundle args = new Bundle();
            args.putString(LOCAL_RECORDING_METADATA_KEY, serializedLocalRecordingMetadata);
            args.putString(REMOTE_RECORDING_METADATA_KEY, serializedRemoteRecordingMetadata);
            f.setArguments(args);
            return f;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            log.info("Activity is attached to ffmpeg task");
            if (videoMerger != null)
                videoMerger.setProgressUpdatable(this);
            currentContext = activity;
        }

        /**
         * This method will only be called once when the retained
         * Fragment is first created.
         */
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            log.info("OnCreate ffmpeg task");


            // Retain this fragment across configuration changes.
            setRetainInstance(true);

            Bundle args = getArguments();

            videoMerger = new FfmpegVideoMerger();
            localRecordingMetadata = gson.fromJson(
                    args.getString(LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
            remoteRecordingMetadata = gson.fromJson(
                    args.getString(REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);
            File vid1 = new File(localRecordingMetadata.getAbsoluteFilePath());
            File vid2 = new File(remoteRecordingMetadata.getAbsoluteFilePath());
            File output = new File("/storage/emulated/0/DCIM/Camera/Merged_" + System.currentTimeMillis() + ".mp4");

            videoMerger.setContext(currentContext);
            videoMerger.setProgressUpdatable(this);
            videoMerger.prepare();

            MergeConfigurationBuilder config = MergeConfiguration.builder();
            config.videoStartOffsetMilli(1000l);
            videoMerger.mergeVideos(vid1, vid2, output, config.build());
            startTime = System.currentTimeMillis();


            NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(NOTIFICATION_SERVICE);
            notificationBuilder = new Notification.Builder(currentContext)
                    .setContentTitle("Chameleon Video Merge")
                    .setContentText("Chameleon video merge is in progress")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setOngoing(true)
                    .setProgress(100, 0, false);
            notificationManager.notify(MERGING_NOTIFICATION_ID, notificationBuilder.build());
        }

        @Override
        public void onDetach() {
            currentContext = null;
            super.onDetach();
        }

        @Override
        public void onProgress(double progress, double outOf) {
            if (currentContext != null)
                ((ProgressUpdatable) currentContext).onProgress(progress, outOf);

            int progressPerc = (int) Math.ceil(100.0 * progress / outOf);
            int remaining = 100 - progressPerc;
            long elapsed = System.currentTimeMillis() - startTime;
            Double timeRemainingMilli;
            if (remaining == 100)
                timeRemainingMilli = (double) TimeUnit.MINUTES.convert(2, TimeUnit.MILLISECONDS);
            else
                timeRemainingMilli = (100 * elapsed) / (100.0 - remaining);

            NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(NOTIFICATION_SERVICE);
            notificationBuilder.setProgress(100, progressPerc, false);
            String remainingTime = getMinutesAndSeconds(timeRemainingMilli / 1000.0);
            notificationBuilder.setContentText("Chameleon video merge is in progress (" + String.format("%d%%", progressPerc) + ", " + remainingTime + " remaining)");
            notificationManager.notify(MERGING_NOTIFICATION_ID, notificationBuilder.build());
        }

        private String getMinutesAndSeconds(double time) {
            int timeSec = (int) Math.round(time);
            String res = "";
            if (timeSec >= 60) {
                res += (int) Math.floor(timeSec / 60) + ":";
            }
            res += String.format("%02d", timeSec % 60);

            if (timeSec < 60)
                res += "s";

            return res;
        }

        @Override
        public void onCompleted() {
            if (currentContext != null)
                ((ProgressUpdatable) currentContext).onCompleted();

            NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.cancel(MERGING_NOTIFICATION_ID);
            Notification n = new Notification.Builder(currentContext)
                    .setContentTitle("Chameleon Merge Complete")
                    .setContentText("Chameleon video merge has completed")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .build();
            notificationManager.notify(COMPLETED_NOTIFICATION_ID, n);
        }
    }
}
