package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/16/15.
 */
@Builder
@Getter
public class SendRecordedVideoResponse {
    @NonNull
    private Long fileSizeBytes;
}
