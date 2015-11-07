package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;

/**
 * Created by dhinesh.dharman on 7/16/15.
 */
@Builder
@Getter
public class SendRecordedVideoResponse {
    // Version will be used for handling backward incompatible
    // changes to message format
    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private long fileSizeBytes = -1;
    private long recordingStartTimeMillis = -1;
}
