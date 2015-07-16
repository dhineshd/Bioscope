package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/11/15.
 */
@Builder
public class HandshakeMessage {
    @NonNull
    private String info;
}
