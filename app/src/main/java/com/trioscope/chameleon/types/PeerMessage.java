package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Getter;

/**
 * Created by dhinesh.dharman on 7/11/15.
 */
@Builder
@Getter
public class PeerMessage {
    public enum Type {
        START_SESSION,
        SEND_STREAM,
        TERMINATE_SESSION,
        START_RECORDING,
        START_RECORDING_RESPONSE,
        STOP_RECORDING,
        STOP_RECORDING_RESPONSE,
        SEND_RECORDED_VIDEO,
        SEND_RECORDED_VIDEO_RESPONSE,
        RETAKE_SESSION
    }

    private Type type;
    private String senderUserName;
    private String contents;
    private long sendTimeMillis = -1;
}
