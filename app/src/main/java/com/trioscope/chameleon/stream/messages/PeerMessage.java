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
        STOP_RECORDING,
        REQUEST_RECORDED_VIDEO
    }

    @NonNull
    private Type type;
    private String contents;
}
