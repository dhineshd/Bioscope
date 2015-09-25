package com.trioscope.chameleon.stream.messages;

import lombok.Builder;
import lombok.Getter;

/**
 * Created by dhinesh.dharman on 9/23/15.
 */
@Builder
@Getter
public class StreamMetadata {
    // mirror image
    private boolean horizontallyFlipped;
}
