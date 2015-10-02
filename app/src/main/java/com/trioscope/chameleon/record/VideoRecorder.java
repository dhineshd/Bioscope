package com.trioscope.chameleon.record;

import com.trioscope.chameleon.types.RecordingMetadata;

/**
 * Created by phand on 4/29/15.
 */
public interface VideoRecorder {
    boolean startRecording();
    RecordingMetadata stopRecording();
    boolean isRecording();
}
