package com.trioscope.chameleon.fragment;


import android.app.Activity;
import android.app.Fragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;

import com.google.gson.Gson;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.types.NotificationIds;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.MergeConfiguration;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import java.io.File;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FfmpegTaskFragment extends Fragment implements ProgressUpdatable {
    public static final String MAJOR_VIDEO_METADATA_KEY = "MAJOR_VIDEO_METADATA";
    public static final String MINOR_VIDEO_METADATA_KEY = "MINOR_VIDEO_METADATA";
    private static final String MAJOR_BEFORE_MINOR_VIDEO_START_OFFSET_MILLIS_KEY =
            "MAJOR_BEFORE_MINOR_VIDEO_START_OFFSET_MILLIS";
    private static final String OUTPUT_FILENAME_KEY = "OUTPUT_FILENAME";
    private static final int MERGING_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS.getId();
    private static final int COMPLETED_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS_COMPLETE.getId();

    private Context currentContext;
    private FfmpegVideoMerger videoMerger;
    private long startTime;
    private Notification.Builder notificationBuilder;
    private RecordingMetadata localRecordingMetadata;
    private RecordingMetadata remoteRecordingMetadata;
    private long localVideoStartedBeforeRemoteVideoOffsetMillis;
    private String outputFilename;

    private Gson gson = new Gson();

    public static FfmpegTaskFragment newInstance(
            final String serializedLocalRecordingMetadata,
            final String serializedRemoteRecordingMetadata,
            final long localVideoStartedBeforeRemoteVideoOffsetMillis,
            final String outputFilename) {
        FfmpegTaskFragment f = new FfmpegTaskFragment();
        Bundle args = new Bundle();
        args.putString(MAJOR_VIDEO_METADATA_KEY, serializedLocalRecordingMetadata);
        args.putString(MINOR_VIDEO_METADATA_KEY, serializedRemoteRecordingMetadata);
        args.putLong(MAJOR_BEFORE_MINOR_VIDEO_START_OFFSET_MILLIS_KEY,
                localVideoStartedBeforeRemoteVideoOffsetMillis);
        args.putString(OUTPUT_FILENAME_KEY, outputFilename);
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
                args.getString(MAJOR_VIDEO_METADATA_KEY), RecordingMetadata.class);
        remoteRecordingMetadata = gson.fromJson(
                args.getString(MINOR_VIDEO_METADATA_KEY), RecordingMetadata.class);
        localVideoStartedBeforeRemoteVideoOffsetMillis =
                args.getLong(MAJOR_BEFORE_MINOR_VIDEO_START_OFFSET_MILLIS_KEY);
        outputFilename = args.getString(OUTPUT_FILENAME_KEY);
        log.info("Local video started before remote video offset millis = {}",
                localVideoStartedBeforeRemoteVideoOffsetMillis);
        File vid1 = new File(localRecordingMetadata.getAbsoluteFilePath());
        File vid2 = new File(remoteRecordingMetadata.getAbsoluteFilePath());
        File output = new File(outputFilename);

        videoMerger.setContext(currentContext);
        videoMerger.setProgressUpdatable(this);
        videoMerger.prepare();

        MergeConfiguration.MergeConfigurationBuilder config = MergeConfiguration.builder();
        config.videoStartOffsetMilli(localVideoStartedBeforeRemoteVideoOffsetMillis);
        videoMerger.mergeVideos(vid1, vid2, output, config.build());
        startTime = System.currentTimeMillis();


        NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(Context.NOTIFICATION_SERVICE);
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

        int progressPerc = getPercent(progress, outOf);
        int remaining = 100 - progressPerc;
        long elapsed = System.currentTimeMillis() - startTime;
        Double timeRemainingMilli;
        if (remaining == 100)
            timeRemainingMilli = (double) TimeUnit.MINUTES.convert(2, TimeUnit.MILLISECONDS);
        else
            timeRemainingMilli = (100 * elapsed) / (100.0 - remaining);

        NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder.setProgress(100, progressPerc, false);
        String remainingTime = getMinutesAndSeconds(timeRemainingMilli / 1000.0);
        notificationBuilder.setContentText("Chameleon video merge is in progress (" + String.format("%d%%", progressPerc) + ", " + remainingTime + " remaining)");
        notificationManager.notify(MERGING_NOTIFICATION_ID, notificationBuilder.build());
    }

    private static int getPercent(double progress, double outOf) {
        return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
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

        NotificationManager notificationManager = (NotificationManager) currentContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(MERGING_NOTIFICATION_ID);
        Notification n = new Notification.Builder(currentContext)
                .setContentTitle("Chameleon Merge Complete")
                .setContentText("Chameleon video merge has completed")
                .setSmallIcon(R.drawable.ic_launcher)
                .build();
        notificationManager.notify(COMPLETED_NOTIFICATION_ID, n);
    }
}
