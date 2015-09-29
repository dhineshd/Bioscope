package com.trioscope.chameleon.stream;

import java.io.OutputStream;

/**
 * Created by dhinesh.dharman on 9/28/15.
 */
public interface NetworkStreamer {
    void startStreaming(OutputStream destOutputStream);
    void stopStreaming();
}
