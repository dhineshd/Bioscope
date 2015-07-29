package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/11/15.
 */
@Builder
@Getter
public class PeerMessage {
    public enum Type{
        START_SESSION,

        SESSION_HEARTBEAT, // Exchanged by devices periodically to act as connectivity health check

        TERMINATE_SESSION,

        START_RECORDING,

        START_RECORDING_RESPONSE,
        STOP_RECORDING,

        SEND_RECORDED_VIDEO,
        SEND_RECORDED_VIDEO_RESPONSE
    }

    @NonNull
    private Type type;
    private String contents;
}
