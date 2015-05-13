package com.trioscope.chameleon.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import com.trioscope.chameleon.camera.ForwardedCameraPreview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderService extends Service implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorderService.class);
    private BackgroundRecorderBinder backgroundRecorderBinder = new BackgroundRecorderBinder(this);

    private MediaRecorder mediaRecorder;
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private Camera camera = null;

    @Setter
    private ForwardedCameraPreview cameraPreview;

    @Setter
    private File outputFile;

    private SurfaceHolder lastCreatedSurfaceHolder;

    @Override
    public IBinder onBind(Intent intent) {
        LOG.info("Initial binding to the service");

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SurfaceView(this);
        LayoutParams layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        surfaceView.getHolder().addCallback(this);

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

        if (lastCreatedSurfaceHolder == null) {
            LOG.warn("Surface not yet created");
            return;
        }

        camera = Camera.open();
        camera.setPreviewCallback(this);
        try {
            camera.setPreviewDisplay(lastCreatedSurfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
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
        // This seems to be a hack
        camera.setPreviewCallback(this);

        LOG.info("Created mediaRecorder {} during surface creation, backgroundRecorderService is {}", mediaRecorder, this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LOG.info("Surface created");
        lastCreatedSurfaceHolder = holder;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        LOG.info("Surface changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        LOG.info("Surface destroyed");
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        LOG.info("Preview Frame Received");
        cameraPreview.drawData(data, camera.getParameters());
    }

}
