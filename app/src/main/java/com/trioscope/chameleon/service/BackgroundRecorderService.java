package com.trioscope.chameleon.service;

import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.IBinder;

import com.trioscope.chameleon.SystemOverlayGLSurface;
import com.trioscope.chameleon.stream.RecordingEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderService extends Service {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorderService.class);
    private BackgroundRecorderBinder backgroundRecorderBinder = new BackgroundRecorderBinder(this);

    private MediaRecorder mediaRecorder;
    private SystemOverlayGLSurface surfaceView;

    @Setter
    private Camera camera = null;

    @Setter
    private File outputFile;
    @Setter
    private RecordingEventListener recordingEventListener;

    @Override
    public IBinder onBind(Intent intent) {
        LOG.info("Initial binding to the service");

        return backgroundRecorderBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LOG.info("Last client has unbound from service - ending video recording for backgroundRecorderService {}", this);
        releaseMediaRecorder();
        return super.onUnbind(intent);
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            LOG.info("Releasing mediaRecorder {}", mediaRecorder);
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
        }
    }

    public void startRecording() {
        LOG.info("Starting recording");

        mediaRecorder = new MediaRecorder();

        // Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Set recording settings
        mediaRecorder.setOrientationHint(90); // portrait

        // Set output file
        LOG.info("Setting outputh path = {}", outputFile.getPath());
        mediaRecorder.setOutputFile(outputFile.getPath());

        // Step 5: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            LOG.error("IllegalStateException preparing MediaRecorder: ", e);
            releaseMediaRecorder();
            return;
        } catch (IOException e) {
            LOG.error("IOException preparing MediaRecorder: ", e);
            releaseMediaRecorder();
            return;
        }

        mediaRecorder.start();

        // Started recording
        recordingEventListener.onStartRecording(System.currentTimeMillis());


        LOG.info("Created mediaRecorder {} during surface creation, backgroundRecorderService is {}", mediaRecorder, this);
    }
}
