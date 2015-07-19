package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/19/15.
 */
@Builder
@Getter
public class StartRecordingResponse {
    @NonNull
    private Long currentTimeMillis;
}

