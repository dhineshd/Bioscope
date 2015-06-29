package com.trioscope.chameleon.camera;

import android.hardware.Camera;

import java.io.File;

/**
 * Created by phand on 4/29/15.
 */
public interface VideoRecorder {
    boolean startRecording();
    void stopRecording();
    boolean isRecording();

    //TODO: Move initialization parameters into factory class
    void setOutputFile(File outputFile);
    void setCamera(Camera c);
}
