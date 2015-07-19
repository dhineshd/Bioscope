package com.trioscope.chameleon.activity;


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ProgressBar;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.NotificationIds;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/19/15.
 */
public class MergeVideosActivity extends AppCompatActivity implements ProgressUpdatable {
    private static final Logger LOG = LoggerFactory.getLogger(MergeVideosActivity.class);
    private static final String TASK_FRAGMENT_TAG = "ASYNC_TASK_FRAGMENT_TAG";
    private static final int MERGING_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS.getId();
    private static final int COMPLETED_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS_COMPLETE.getId();
    private FfmpegTaskFragment taskFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ffmpeg_test);
        LOG.info("Activity is created");

        Intent intent = getIntent();

        printArchInfo();
        runFfmpeg();

        FragmentManager fm = getFragmentManager();
        taskFragment = (FfmpegTaskFragment) fm.findFragmentByTag(TASK_FRAGMENT_TAG);

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change.
        if (taskFragment == null) {
            taskFragment = FfmpegTaskFragment.newInstance(
                    intent.getStringExtra(PreviewMergeActivity.LOCAL_RECORDING_METADATA_KEY),
                    intent.getStringExtra(PreviewMergeActivity.REMOTE_RECORDING_METADATA_KEY));
            fm.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
        } else {
            LOG.info("Task fragment exists - reusing (device rotated)");
        }
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
        LOG.info("FFMPEG Completed!");
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
            args.putString(PreviewMergeActivity.LOCAL_RECORDING_METADATA_KEY, serializedLocalRecordingMetadata);
            args.putString(PreviewMergeActivity.REMOTE_RECORDING_METADATA_KEY, serializedRemoteRecordingMetadata);
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
                    args.getString(PreviewMergeActivity.LOCAL_RECORDING_METADATA_KEY), RecordingMetadata.class);
            remoteRecordingMetadata = gson.fromJson(
                    args.getString(PreviewMergeActivity.REMOTE_RECORDING_METADATA_KEY), RecordingMetadata.class);
            File vid1 = new File(localRecordingMetadata.getAbsoluteFilePath());
            File vid2 = new File(remoteRecordingMetadata.getAbsoluteFilePath());
            File output = new File("/storage/emulated/0/DCIM/Camera/Merged_" + System.currentTimeMillis() + ".mp4");
            videoMerger.setContext(currentContext);
            videoMerger.setProgressUpdatable(this);
            videoMerger.prepare();
            videoMerger.mergeVideos(vid1, vid2, output);
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
