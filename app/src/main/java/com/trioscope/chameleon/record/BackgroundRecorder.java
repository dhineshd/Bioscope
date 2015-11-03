package com.trioscope.chameleon.record;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.IBinder;

import com.trioscope.chameleon.service.BackgroundRecorderBinder;
import com.trioscope.chameleon.service.BackgroundRecorderService;
import com.trioscope.chameleon.types.RecordingMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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
    private boolean recording;

    @Setter
    private File outputFile;

    @Setter
    private Camera camera;


    /* Background recording */
    private volatile BackgroundRecorderService backgroundRecorderService;
    private volatile boolean serviceBound;
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LOG.info("Service {} is bound from BackgroundRecorder ", name);
            serviceBound = true;
            backgroundRecorderService = ((BackgroundRecorderBinder<BackgroundRecorderService>) service).getService();
            backgroundRecorderService.setOutputFile(outputFile);
            backgroundRecorderService.setCamera(camera);
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
        LOG.info("Binding service with intent");
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
    public RecordingMetadata stopRecording() {
        if (serviceBound) {
            context.unbindService(connection);
            serviceBound = false;
        }
        recording = false;
        return null;
    }

}
