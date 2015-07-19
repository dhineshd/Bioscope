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
        CONNECTION_HANDSHAKE,
        START_RECORDING,
        START_RECORDING_RESPONSE,
        STOP_RECORDING,
        SEND_RECORDED_VIDEO_REQUEST,
        SEND_RECORDED_VIDEO_RESPONSE
    }

    @NonNull
    private Type type;
    private String contents;
}
