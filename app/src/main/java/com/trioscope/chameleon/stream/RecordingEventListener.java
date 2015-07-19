package com.trioscope.chameleon.stream;

/**
 * Created by dhinesh.dharman on 7/11/15.
 */
public interface RecordingEventListener {
    void onStartRecording(long recordingStartTimeMillis);
    void onStopRecording();
}
