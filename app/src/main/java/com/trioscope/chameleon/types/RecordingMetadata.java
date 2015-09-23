package com.trioscope.chameleon.types;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 7/16/15.
 */
@Builder
@Getter
public class RecordingMetadata {
    @NonNull
    private String absoluteFilePath;
    @NonNull
    private Long startTimeMillis;
    @NonNull
    private Integer orientationDegrees;
}
