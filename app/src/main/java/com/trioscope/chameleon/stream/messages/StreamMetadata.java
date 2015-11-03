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
    // Version will be used for handling backward incompatible
    // changes to message format
    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    // mirror image
    private boolean horizontallyFlipped;
    private int orientationDegrees;
}
