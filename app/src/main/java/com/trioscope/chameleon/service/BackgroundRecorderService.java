package com.trioscope.chameleon.service;

import android.app.Service;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.IBinder;

import com.trioscope.chameleon.activity.MainActivity;
import com.trioscope.chameleon.RenderRequestFrameListener;
import com.trioscope.chameleon.SystemOverlayGLSurface;
import com.trioscope.chameleon.camera.ForwardedCameraPreview;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderService extends Service implements Camera.PreviewCallback {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorderService.class);
    private BackgroundRecorderBinder backgroundRecorderBinder = new BackgroundRecorderBinder(this);

    private MediaRecorder mediaRecorder;
    private SystemOverlayGLSurface surfaceView;
    private Camera camera = null;

    @Setter
    private ForwardedCameraPreview cameraPreview;

    @Setter
    private CameraPreviewTextureListener frameListener;

    @Setter
    private File outputFile;

    @Setter
    private MainActivity.MainThreadHandler mainThreadHandler;

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

        EGLContextAvailableMessage msg = new EGLContextAvailableMessage();
        msg.setSurfaceTexture(surfaceView.getSurfaceTexture());
        msg.setGlTextureId(surfaceView.getTextureId());
        msg.setEglContext(surfaceView.getEglContext());
        mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(MainActivity.MainThreadHandler.EGL_CONTEXT_AVAILABLE, msg));

        camera = Camera.open();
        try {
            // Race condition here - fix with surfaceHolderListener
            frameListener.addFrameListener(new RenderRequestFrameListener(surfaceView));
            surfaceView.getSurfaceTexture().setOnFrameAvailableListener(frameListener);
            camera.setPreviewTexture(surfaceView.getSurfaceTexture());
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

        LOG.info("Created mediaRecorder {} during surface creation, backgroundRecorderService is {}", mediaRecorder, this);
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        LOG.info("Preview Frame Received");
        cameraPreview.drawData(data, camera.getParameters());
    }

    public void attachFrameListener(FrameListener listener) {
        frameListener.addFrameListener(listener);
    }
}
