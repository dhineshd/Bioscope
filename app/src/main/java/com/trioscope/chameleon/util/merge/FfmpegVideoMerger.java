package com.trioscope.chameleon.util.merge;

import android.content.Context;
import android.os.AsyncTask;

import com.trioscope.chameleon.util.DepackageUtil;
import com.trioscope.chameleon.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
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


    private static final Pattern INPUT_DURATION_PATTERN = Pattern.compile("Duration: (\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d)");
    private static final Pattern STATUS_DURATION_PATTERN = Pattern.compile("time=(\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d)");

    @Setter
    private Context context;
    private DepackageUtil depackageUtil;
    private boolean prepared = false;

    @Setter
    private ProgressUpdatable progressUpdatable;

    public void setContext(Context context) {
        this.context = context;
        this.depackageUtil = new DepackageUtil(context);
    }

    public void prepare() {
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

        prepared = true;
    }

    @Override
    public void mergeVideos(File video1, File video2, File outputFile, MergeConfiguration configuration) {
        if (!prepared)
            prepare();

        VideoMergeTaskParams params = new VideoMergeTaskParams();
        params.setConfiguration(configuration);
        params.setFile1(video1);
        params.setFile2(video2);
        params.setOutputFile(outputFile);
        AsyncTask<VideoMergeTaskParams, Double, Boolean> task = new AsyncVideoMergeTask().execute(params);
    }

    private List<String> constructPIPArguments(String majorVidPath, String minorVidPath, String outputPath, MergeConfiguration configuration) {
        List<String> params = new LinkedList<>();
        params.add("-i");
        params.add(majorVidPath);
        if (configuration.getVideoStartOffsetMilli() != null) {
            params.add("-itsoffset");
            params.add(String.format("%.3f", configuration.getVideoStartOffsetMilli() / 1000.0));
        }
        params.add("-i");
        params.add(minorVidPath);
        params.add("-filter_complex");
        params.add("[0] transpose=1,scale=iw:-1 [0new]; [1] transpose=1,scale=iw:-1 [1new]; [1new]scale=iw/3:ih/3 [pip]; [0new][pip] overlay=0:main_h-overlay_h");
        params.add("-preset");
        params.add("ultrafast");
        params.add("-threads");
        params.add("auto");
        params.add("-strict");
        params.add("experimental");
        params.add(outputPath);

        return params;
    }

    private class AsyncVideoMergeTask extends AsyncTask<VideoMergeTaskParams, Double, Boolean> {
        private long start, end;
        private double maxInputTime = 0;

        @Override
        protected Boolean doInBackground(VideoMergeTaskParams... params) {
            start = System.currentTimeMillis();
            File majorVideo = params[0].getFile1();
            File minorVideo = params[0].getFile2();
            File outputFile = params[0].getOutputFile();

            log.info("Running ffmpeg to merge {} and {} into {}", majorVideo, minorVideo, outputFile);
            File ffmpeg = depackageUtil.getOutputFile(DEPACKAGED_CMD_NAME);
            String cmdLocation = ffmpeg.getAbsolutePath();

            if (!majorVideo.exists() || !minorVideo.exists()) {
                log.info("One of {} or {} do not exist -- cannot merge", majorVideo, minorVideo);
                return false;
            }

            try {
                if (outputFile.exists()) {
                    log.info("Deleting {} first", outputFile);
                    outputFile.delete();
                    log.info("Existing file at {} is deleted", outputFile);
                }
                List<String> cmdParams = constructPIPArguments(majorVideo.getAbsolutePath(), minorVideo.getAbsolutePath(), outputFile.getAbsolutePath(), params[0].getConfiguration());
                cmdParams.add(0, cmdLocation); // Prepend the parameters with the command line location
                log.info("Ffmpeg parameters are {}", cmdParams);
                ProcessBuilder builder = new ProcessBuilder(cmdParams);
                builder.redirectErrorStream(true);
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
                            publishProgress(sec, maxInputTime);
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
            return null;
        }

        private double getSecondsFromTimeFormat(Matcher m) {
            double sec = Double.valueOf(m.group(3));
            int min = Integer.valueOf(m.group(2));
            int hr = Integer.valueOf(m.group(1));

            min += hr * 60;
            sec += min * 60;
            return sec;
        }

        @Override
        protected void onPreExecute() {
            log.info("About to run video merge as an async task");
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            if (values.length == 2 && values[1] > 0 && progressUpdatable != null)
                progressUpdatable.onProgress(values[0], values[1]);
        }

        @Override
        protected void onPostExecute(Boolean aBoolean) {
            end = System.currentTimeMillis();

            log.info("Finished running video merge. Took {}s", (end - start) / 1000.0);

            progressUpdatable.onCompleted();
        }

        @Override
        protected void onCancelled() {
            log.info("Task unexpectedly cancelled!");
        }
    }

    @Data
    private class VideoMergeTaskParams {
        File file1, file2, outputFile;
        MergeConfiguration configuration;
    }
}
