package com.trioscope.chameleon.camera.stream;

/**
 * Created by dhinesh.dharman on 6/4/15.
 */
public interface VideoStreamer {
    void startStreaming();
    void stopStreaming();
    boolean isStreaming();
}
