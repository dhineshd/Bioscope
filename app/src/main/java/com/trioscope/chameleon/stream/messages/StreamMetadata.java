package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Created by dhinesh.dharman on 9/23/15.
 */
@Builder
@Getter
@ToString
public class StreamMetadata {
    // mirror image
    private boolean horizontallyFlipped;
    private int orientationDegrees;
}
