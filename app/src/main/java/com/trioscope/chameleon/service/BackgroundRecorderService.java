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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderService extends Service implements SurfaceHolder.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorderService.class);
    private BackgroundRecorderBinder backgroundRecorderBinder = new BackgroundRecorderBinder(this);

    private MediaRecorder mediaRecorder;
    private WindowManager windowManager;
    private SurfaceView surfaceView;
    private Camera camera = null;

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
        if (mediaRecorder != null) {
            LOG.info("Releasing mediaRecorder {}", mediaRecorder);
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
        }
        return super.onUnbind(intent);
    }

    public void startRecording() {
        LOG.info("Starting recording");

        if (lastCreatedSurfaceHolder == null) {
            LOG.warn("Surface not yet created");
            return;
        }

        camera = Camera.open();
        mediaRecorder = new MediaRecorder();
        camera.unlock();

        mediaRecorder.setPreviewDisplay(lastCreatedSurfaceHolder.getSurface());
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
        mediaRecorder.setOutputFile(outputFile.getPath());

        try {
            mediaRecorder.prepare();
        } catch (Exception e) {
            LOG.error("Error preparing media recorder", e);
        }
        mediaRecorder.start();

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
}
