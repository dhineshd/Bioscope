package com.trioscope.chameleon.activity;


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ProgressBar;

import com.trioscope.chameleon.R;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.ProgressUpdatable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/19/15.
 */
public class FfmpegTest extends AppCompatActivity implements ProgressUpdatable {
    private static final Logger LOG = LoggerFactory.getLogger(FfmpegTest.class);
    private static final String TASK_FRAGMENT_TAG = "ASYNC_TASK_FRAGMENT_TAG";
    private FfmpegTaskFragment taskFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ffmpeg_test);
        LOG.info("Activity is created");

        printArchInfo();
        runFfmpeg();

        FragmentManager fm = getFragmentManager();
        taskFragment = (FfmpegTaskFragment) fm.findFragmentByTag(TASK_FRAGMENT_TAG);

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change.
        if (taskFragment == null) {
            taskFragment = new FfmpegTaskFragment();
            fm.beginTransaction().add(taskFragment, TASK_FRAGMENT_TAG).commit();
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
    public static class FfmpegTaskFragment extends Fragment {
        private Context currentContext;
        private FfmpegVideoMerger videoMerger;

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            log.info("Activity is attached to ffmpeg task");
            if (videoMerger != null)
                videoMerger.setProgressUpdatable((ProgressUpdatable) activity);
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

            videoMerger = new FfmpegVideoMerger();
            File vid1 = new File("/storage/emulated/0/DCIM/Camera/VID_20150709_175834.mp4");
            File vid2 = new File("/storage/emulated/0/DCIM/Camera/VID_20150709_175945.mp4");
            File output = new File("/storage/emulated/0/DCIM/Camera/Merged.mp4");
            videoMerger.setContext(currentContext);
            videoMerger.setProgressUpdatable((ProgressUpdatable) currentContext);
            videoMerger.prepare();
            videoMerger.mergeVideos(vid1, vid2, output);
        }

        /**
         * Set the callback to null so we don't accidentally leak the
         * Activity instance.
         */
        @Override
        public void onDetach() {
            super.onDetach();
        }
    }
}
