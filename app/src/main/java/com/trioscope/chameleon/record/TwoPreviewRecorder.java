package com.trioscope.chameleon.record;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.trioscope.chameleon.types.RecordingMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by phand on 5/13/15.
 */
public class TwoPreviewRecorder extends SurfaceView implements VideoRecorder, SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final Logger LOG = LoggerFactory.getLogger(TwoPreviewRecorder.class);

    private SurfaceHolder holder;
    private Camera camera;
    private boolean beenCreated;

    public TwoPreviewRecorder(Context context) {
        super(context);
        holder = getHolder();
        holder.addCallback(this);
    }

    @Override
    public boolean startRecording() {
        LOG.info("Starting recording");
        camera = Camera.open();
        camera.setPreviewCallback(this);
        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            LOG.error("Error setting preview display", e);
        }
        camera.startPreview();
        return true;
    }

    @Override
    public RecordingMetadata stopRecording() {
        return null;
    }

    @Override
    public boolean isRecording() {
        return false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        LOG.info("Surface created");
        //Some callback buffers to increase performance
        //initBuffer();
        //start preview once the surface is created.
        beenCreated = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    long lastTime = -1;
    int frames = 0;
    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        long curTime = System.currentTimeMillis();
        if(lastTime == -1 || (curTime - lastTime) >= 1000) {
            double dur = (curTime - lastTime) / 1000.0;
            double fps = frames / dur;

            LOG.info("FPS: {}", fps);
            lastTime = curTime;
            frames = 0;
        }
        frames++;
    }
}
