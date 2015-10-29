package com.trioscope.chameleon.util.merge;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.DestroyPartialData;
import com.trioscope.chameleon.R;
import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.metrics.MetricNames;
import com.trioscope.chameleon.storage.BioscopeDBHelper;
import com.trioscope.chameleon.storage.VideoInfoType;
import com.trioscope.chameleon.types.NotificationIds;
import com.trioscope.chameleon.util.Asset;
import com.trioscope.chameleon.util.DepackageUtil;
import com.trioscope.chameleon.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/9/15.
 */
@RequiredArgsConstructor
@Slf4j
public class FfmpegVideoMerger implements VideoMerger {
    private static final String PACKAGED_FFMPEG_ARM = "ffmpeg/armeabi-v7a/bin/ffmpeg";
    private static final String PACKAGED_FFMPEG_ARM_WITH_NEON = "ffmpeg/armeabi-v7a-neon/bin/ffmpeg";
    private static final String PACKAGED_FFMPEG_X86 = "ffmpeg/x86/bin/ffmpeg";
    private static final String DEPACKAGED_CMD_NAME = "ffmpeg";

    private static final Asset DEPACKAGED_LIB_OPENH = Asset.builder()
            .url("http://ciscobinary.openh264.org/libopenh264-1.4.0-android19.so.bz2")
            .expectedZippedMd5("b94a0e5d421dd4acc8200ed0c4cd521e")
            .outputName("libopenh264.so.bz2")
            .expectedMd5("6555f3f12cb3be7aa684a497f2a2bbda")
            .build();

    private static final Pattern INPUT_DURATION_PATTERN = Pattern.compile("Duration: (\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d)");
    private static final Pattern STATUS_DURATION_PATTERN = Pattern.compile("time=(\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d)");

    private static final int MERGING_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS.getId();
    private static final int COMPLETED_NOTIFICATION_ID = NotificationIds.MERGING_VIDEOS_COMPLETE.getId();

    private Context context;
    private DepackageUtil depackageUtil;
    private boolean prepared = false;

    @Setter
    private ProgressUpdatable progressUpdatable;

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Future<?> currentRunningTask;
    private Notification.Builder notificationBuilder;
    private Set<File> tempFiles = new HashSet<>();

    public FfmpegVideoMerger(Context context) {
        setContext(context);
    }

    public void setContext(Context context) {
        this.context = context;
        this.depackageUtil = new DepackageUtil(context);
    }

    public void prepare() {
        // If no progressUpdatable is provided, perform the preparation synchronously
    }

    public void prepare(ProgressUpdatable progressUpdatable) {
        if (prepared) {
            log.info("FFMPEG Video Merger already prepared - skipping preparation");
            return;
        }

        log.info("Depackaging ffmpeg");
        // TODO: Pick x86 arch if necessary
        if (FileUtil.checkForNeonProcessorSupport()) {
            log.info("We have neon support in cpu");
            // TODO: Use NEON on supported architecture https://developer.android.com/ndk/guides/cpu-arm-neon.html#rd
            depackageUtil.depackageAsset(PACKAGED_FFMPEG_ARM_WITH_NEON, DEPACKAGED_CMD_NAME);
        } else {
            depackageUtil.depackageAsset(PACKAGED_FFMPEG_ARM, DEPACKAGED_CMD_NAME);
        }

        depackageUtil.downloadAsset(DEPACKAGED_LIB_OPENH, progressUpdatable);

        prepared = true;
    }


    @Override
    public void mergeVideos(
            final VideoConfiguration majorVideoConfig,
            final VideoConfiguration minorVideoConfig,
            File outputFile,
            final MergeConfiguration configuration) {

        if (!prepared)
            prepare();

        if (currentRunningTask != null && !currentRunningTask.isDone()) {
            log.warn("Not yet done merging previous running task. We only support one runnable right now");
            return;
        }

        VideoMergeTaskParams params = new VideoMergeTaskParams();
        params.setConfiguration(configuration);
        params.setMajorVideoConfig(majorVideoConfig);
        params.setMinorVideoConfig(minorVideoConfig);
        params.setOutputFile(outputFile);

        BioscopeDBHelper db = new BioscopeDBHelper(context);
        db.insertVideoInfo(outputFile.getName(), VideoInfoType.BEING_MERGED, "true");
        addThumbnailToDb(majorVideoConfig.getFile(), outputFile, db);
        db.close();

        tempFiles.add(majorVideoConfig.getFile());
        tempFiles.add(minorVideoConfig.getFile());
        Runnable task = new VideoMergeRunnable(params);

        currentRunningTask = executorService.submit(task);

        NotificationManager notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationBuilder = new Notification.Builder(this.context)
                .setContentTitle(VideoMerger.MERGE_NOTIFICATION_TITLE)
                .setContentText(VideoMerger.MERGE_IN_PROGRESS_NOTIFICATION_TEXT)
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setProgress(100, 0, false);
        notificationManager.notify(MERGING_NOTIFICATION_ID, notificationBuilder.build());
    }


    @Timed
    private void addThumbnailToDb(File majorVideo, File outputFile, BioscopeDBHelper db) {
        Bitmap bm = getThumbnail(majorVideo);
        if (bm != null) {
            db.insertThumbnail(outputFile.getName(), bm);
            log.info("Successfully inserted video thumbnail for {}", outputFile);
        } else {
            log.warn("Unable to create video thumbnail for {}", majorVideo);
        }
    }

    @Timed
    private Bitmap getThumbnail(File videoFile) {
        try {
            Bitmap bm = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);

            if (bm != null) {
                return bm;
            } else {
                log.warn("Failed to create thumbnail from ThumbnailUtils, using MMR instead");
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(videoFile.getAbsolutePath());
                    int timeInSeconds = 30;
                    bm = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

                    return bm;
                } catch (Exception ex) {
                    log.error("Exception getting thumbnail for file {}", videoFile, ex);
                }
            }
        } catch (Exception ex) {
            log.error("Exception getting thumbnail for file {}", videoFile, ex);
        }
        return null;
    }

    public Collection<File> getTemporaryFiles() {
        return Collections.unmodifiableCollection(tempFiles);
    }

    private List<String> constructPIPArguments(
            final String majorVidPath,
            final boolean shouldHorizontallyFlipMajorVideo,
            final String minorVidPath,
            final boolean shouldHorizontallyFlipMinorVideo,
            final String outputPath,
            final MergeConfiguration configuration) {
        List<String> params = new LinkedList<>();
        params.add("-y"); // Overwrite any output already existing
        params.add("-i");
        params.add(majorVidPath);
        if (configuration.getVideoStartOffsetMilli() != null) {
            params.add("-itsoffset");
            params.add(String.format("%.3f", configuration.getVideoStartOffsetMilli() / 1000.0));
        }
        params.add("-i");
        params.add(minorVidPath);

        params.add("-i");
        params.add(getWatermarkLogoAbsolutePath());

        params.add("-c:v");
        params.add("libopenh264");
        params.add("-b:a");
        params.add("256k");
        params.add("-b:v");
        params.add("5000k");
        params.add("-filter_complex");
        params.add("[0] " + (shouldHorizontallyFlipMajorVideo ? "hflip," : "") + "scale=1080:1920 [major]; " +
                "[1] " + (shouldHorizontallyFlipMinorVideo ? "hflip," : "") + "scale=412:732, " +
                "drawbox=c=white:t=8, trim=start_frame=2 [minor]; " +
                "[major][minor] overlay=54:main_h-overlay_h-54:eval=init [merged];" +
                "[merged][2] overlay=main_w-overlay_w-54:main_h-overlay_h-54");

        //OpenH264 doesnt support preset
        //params.add("-preset");
        //params.add("ultrafast");
        params.add("-threads");
        params.add("auto");
        params.add("-strict");
        params.add("experimental");
        params.add(outputPath);

        return params;
    }

    private String getWatermarkLogoAbsolutePath() {
        // Copy watermark logo to temp directory and then refer to it
        try {
            Bitmap bm = BitmapFactory.decodeResource(context.getResources(), R.drawable.watermark_logo);
            File file = new File(FileUtil.getOutputMediaDirectory(), "watermark_logo.png");
            FileOutputStream outStream = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 100, outStream);
            outStream.flush();
            outStream.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            log.error("Failed to copy watermark logo to temp directory", e);
        }
        return "";
    }

    public void printAvailableCodecs() {
        log.info("Printing available codecs for FFmpeg");
        prepare();

        List<String> cmdParams = new ArrayList<>();
        cmdParams.add("-codecs");
        runFFmpegCommandAndPrint(cmdParams);
    }

    public void printLicenseInfo() {
        log.info("Printing license info for FFmpeg");
        prepare();

        List<String> cmdParams = new ArrayList<>();
        cmdParams.add("-L");
        runFFmpegCommandAndPrint(cmdParams);
    }

    private void runFFmpegCommandAndPrint(List<String> cmdParams) {
        File ffmpeg = depackageUtil.getOutputFile(DEPACKAGED_CMD_NAME);

        String cmdLocation = ffmpeg.getAbsolutePath();
        cmdParams.add(0, cmdLocation); // Prepend the parameters with the command line location
        log.info("Ffmpeg parameters are {}", cmdParams);
        ProcessBuilder builder = new ProcessBuilder(cmdParams);
        builder.redirectErrorStream(true);

        // Append libopenh264.so to LD_LIBRARY_PATH
        String ldLibraryPath = depackageUtil.getOutputDirectory().getAbsolutePath();
        Map<String, String> env = builder.environment();
        env.put("LD_LIBRARY_PATH", ldLibraryPath + ":$LD_LIBRARY_PATH");
        try {
            Process p = builder.start();

            String line;
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = in.readLine()) != null) {
                line = line.trim();
                log.info("Output: {}", line);
            }
            in.close();

            log.info("Done running cmd, exitValue={}", p.waitFor());
        } catch (IOException | InterruptedException e) {
            log.error("Error printing license info", e);
        }
    }

    public boolean hasRequiredComponents() {
        return depackageUtil.hasDownloaded(DEPACKAGED_LIB_OPENH);
    }


    private class VideoMergeRunnable implements Runnable {
        private long start, end;
        private double maxInputTime = 0;
        private VideoConfiguration minorVideoConfig, majorVideoConfig;
        private File outputFile;
        private MergeConfiguration mergeConfiguration;

        public VideoMergeRunnable(VideoMergeTaskParams params) {
            start = System.currentTimeMillis();
            majorVideoConfig = params.getMajorVideoConfig();
            minorVideoConfig = params.getMinorVideoConfig();
            mergeConfiguration = params.getConfiguration();
            outputFile = params.getOutputFile();
        }

        @Override
        public void run() {
            try {
                merge();
                complete();
            } catch (Exception e) {
                log.error("Error merging videos {} and {} to {}", majorVideoConfig, minorVideoConfig, outputFile, e);

                // Clean up any partial data TODO: decide what to do with major and minor videos
                new DestroyPartialData(context).run();
            } finally {
                log.info("Removing {} from list of files being merged", outputFile);
            }
        }

        private void complete() {
            end = System.currentTimeMillis();

            long mergeTime = end - start;
            log.info("Finished running video merge. Took {}s", mergeTime / 1000.0);

            ChameleonApplication.getMetrics().sendTime(
                    MetricNames.Category.VIDEO.getName(),
                    MetricNames.Label.MERGE_TIME.getName(),
                    mergeTime);


            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(outputFile.getAbsolutePath());
                Long durationOfVideo = Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                if (durationOfVideo != null) {
                    log.info("Duration of merged video is {}ms", durationOfVideo);
                    //Publish merge time
                    ChameleonApplication.getMetrics().sendTime(
                            MetricNames.Category.VIDEO.getName(),
                            MetricNames.Label.MERGE_TIME.getName(),
                            durationOfVideo);

                    //Publish merge time to video length ratio
                    ChameleonApplication.getMetrics().sendMergeTimeToVideoDuration(mergeTime / durationOfVideo);

                }

            } catch (Exception e) {
                e.printStackTrace();
            }


            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(MERGING_NOTIFICATION_ID);

            // Delete input videos since we now have merged video
            File majorVideo = majorVideoConfig.getFile();
            File minorVideo = minorVideoConfig.getFile();
            if (majorVideo.exists()) {
                majorVideo.delete();
            }
            if (minorVideo.exists()) {
                minorVideo.delete();
            }

            //Send a broadcast about the newly added video file for Gallery Apps to recognize the video
            Intent addVideoIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            addVideoIntent.setData(Uri.fromFile(outputFile));
            context.sendBroadcast(addVideoIntent);

            BioscopeDBHelper db = new BioscopeDBHelper(context);
            db.deleteVideoInfo(outputFile.getName(), VideoInfoType.BEING_MERGED);
            db.close();

            if (progressUpdatable != null)
                progressUpdatable.onCompleted();
        }

        private void merge() {
            File majorVideo = majorVideoConfig.getFile();
            File minorVideo = minorVideoConfig.getFile();

            log.info("Running ffmpeg to merge {} and {} into {}", majorVideo, minorVideo, outputFile);
            File ffmpeg = depackageUtil.getOutputFile(DEPACKAGED_CMD_NAME);
            String cmdLocation = ffmpeg.getAbsolutePath();

            if (!majorVideo.exists() || !minorVideo.exists()) {
                log.info("One of {} or {} do not exist -- cannot merge", majorVideo, minorVideo);
                return;
            }

            try {
                if (outputFile.exists()) {
                    log.info("Deleting {} first", outputFile);
                    outputFile.delete();
                    log.info("Existing file at {} is deleted", outputFile);
                }

                List<String> cmdParams = constructPIPArguments(
                        majorVideo.getAbsolutePath(),
                        majorVideoConfig.isHorizontallyFlipped(),
                        minorVideo.getAbsolutePath(),
                        minorVideoConfig.isHorizontallyFlipped(),
                        outputFile.getAbsolutePath(),
                        mergeConfiguration);
                cmdParams.add(0, cmdLocation); // Prepend the parameters with the command line location
                log.info("Ffmpeg parameters are {}", cmdParams);
                ProcessBuilder builder = new ProcessBuilder(cmdParams);
                builder.redirectErrorStream(true);

                // Append libopenh264.so to LD_LIBRARY_PATH
                String ldLibraryPath = depackageUtil.getOutputDirectory().getAbsolutePath();
                Map<String, String> env = builder.environment();
                env.put("LD_LIBRARY_PATH", ldLibraryPath + ":$LD_LIBRARY_PATH");

                Process p = builder.start();

                String line;
                BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    log.debug("Output: {}", line);

                    Matcher m = INPUT_DURATION_PATTERN.matcher(line);
                    if (m.find()) {
                        double sec = getSecondsFromTimeFormat(m);
                        log.debug("Found a duration input of {}s", sec);
                        maxInputTime = Math.max(sec, maxInputTime);
                    } else {
                        m = STATUS_DURATION_PATTERN.matcher(line);
                        if (m.find()) {
                            double sec = getSecondsFromTimeFormat(m);
                            log.debug("Found a status update of {}s", sec);
                            if (maxInputTime > 0)
                                updateProgress(sec, maxInputTime);
                        } else {
                            log.info("Non-status line: {}", line);
                        }
                    }
                }
                in.close();

                log.info("Done running cmd, exitValue={}", p.waitFor());
            } catch (IOException e) {
                log.error("Error running ffmpeg", e);
            } catch (InterruptedException e) {
                log.error("Error running ffmpeg", e);
            } catch (Exception e) {
                log.error("Error running ffmpeg", e);
            }
        }

        private void updateProgress(double progress, double outOf) {
            int progressPerc = getPercent(progress, outOf);
            int remaining = 100 - progressPerc;
            long elapsed = System.currentTimeMillis() - start;
            Double timeRemainingMilli;
            if (remaining == 100)
                timeRemainingMilli = (double) TimeUnit.MINUTES.convert(2, TimeUnit.MILLISECONDS);
            else
                timeRemainingMilli = (100 * elapsed) / (100.0 - remaining);

            NotificationManager notificationManager = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationBuilder.setProgress(100, progressPerc, false);
            String remainingTime = getMinutesAndSeconds(timeRemainingMilli / 1000.0);
            notificationBuilder.setContentText("Merge progress (" +
                    String.format("%d%%", progressPerc) + ", " + remainingTime + " remaining)");
            notificationManager.notify(MERGING_NOTIFICATION_ID, notificationBuilder.build());

            if (progressUpdatable != null)
                progressUpdatable.onProgress(progress, outOf);
        }

    }

    private static String getMinutesAndSeconds(double time) {
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

    private static int getPercent(double progress, double outOf) {
        return (int) Math.min(100, Math.ceil(100.0 * progress / outOf));
    }

    private double getSecondsFromTimeFormat(Matcher m) {
        double sec = Double.valueOf(m.group(3));
        int min = Integer.valueOf(m.group(2));
        int hr = Integer.valueOf(m.group(1));

        min += hr * 60;
        sec += min * 60;
        return sec;
    }

    @Data
    private class VideoMergeTaskParams {
        VideoConfiguration majorVideoConfig, minorVideoConfig;
        File outputFile;
        MergeConfiguration configuration;
    }
}
