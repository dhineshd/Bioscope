package com.trioscope.chameleon.service;

import android.app.Service;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.IBinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderService extends Service {
    private static final Logger LOG = LoggerFactory.getLogger(BackgroundRecorderService.class);
    private BackgroundRecorderBinder backgroundRecorderBinder = new BackgroundRecorderBinder(this);

    @Setter
    private MediaRecorder mediaRecorder;

    @Override
    public IBinder onBind(Intent intent) {
        LOG.info("Initial binding to the service");
        return backgroundRecorderBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        LOG.info("Last client has unbound from service - ending video recording");
        mediaRecorder.reset();   // clear recorder configuration
        mediaRecorder.release(); // release the recorder object
        return super.onUnbind(intent);
    }

    public void startRecording() {
        LOG.info("Using given media recorder to start recording");
        mediaRecorder.start();
    }
}
