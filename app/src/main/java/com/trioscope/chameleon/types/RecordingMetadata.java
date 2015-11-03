package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Getter;

/**
 * Created by dhinesh.dharman on 7/16/15.
 */
@Builder
@Getter
public class RecordingMetadata {
    private String absoluteFilePath;
    private long startTimeMillis;
    private boolean horizontallyFlipped;
    private String videographer;
}
