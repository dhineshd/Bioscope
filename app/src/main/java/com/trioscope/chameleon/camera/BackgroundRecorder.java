package com.trioscope.chameleon.camera;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.IBinder;

import com.trioscope.chameleon.service.BackgroundRecorderBinder;
import com.trioscope.chameleon.service.BackgroundRecorderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorder implements VideoRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorder.class);

    private final Context context;

    @Getter
    @Setter
    private Camera camera;

    @Getter
    @Setter
    private boolean recording;

    @Setter
    private File outputFile;

    /* Background recording */
    private BackgroundRecorderService backgroundRecorderService;
    private boolean serviceBound;
    private MediaRecorder mediaRecorder;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LOG.info("Service {} is bound from BackgroundRecorder", name);
            serviceBound = true;
            backgroundRecorderService = ((BackgroundRecorderBinder) service).getService();
            backgroundRecorderService.setMediaRecorder(mediaRecorder);
            backgroundRecorderService.startRecording();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            LOG.info("Service {} is unbound from BackgroundRecorder", name);
            serviceBound = false;
            backgroundRecorderService = null;
        }
    };

    public BackgroundRecorder(Context context) {
        this.context = context;
    }

    @Override
    public boolean startRecording() {
        mediaRecorder = new MediaRecorder();

        // Prepare the media recorder in 5 steps, then pass it off to the background recording service
        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mediaRecorder.setOutputFile(outputFile.getPath());

        // Step 5: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException preparing MediaRecorder", e);
            releaseMediaRecorder(mediaRecorder);
            return false;
        } catch (IOException e) {
            LOG.error("IOException preparing MediaRecorder", e);
            releaseMediaRecorder(mediaRecorder);
            return false;
        }

        // Bind the service, and when it becomes available, give it the mediaRecorder
        Intent bindServiceIntent = new Intent(context, BackgroundRecorderService.class);
        context.bindService(bindServiceIntent, connection, Context.BIND_AUTO_CREATE);


        recording = true;
        return true;
    }

    private void releaseMediaRecorder(MediaRecorder mediaRecorder) {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
        }
    }

    @Override
    public void stopRecording() {
        if(serviceBound) {
            context.unbindService(connection);
            serviceBound = false;
        }
        recording = false;
    }
}
