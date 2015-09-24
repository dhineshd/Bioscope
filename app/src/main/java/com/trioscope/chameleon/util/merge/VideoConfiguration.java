package com.trioscope.chameleon.util.merge;

import java.io.File;

import lombok.Getter;
import lombok.Builder;

/**
 * Created by dhinesh.dharman on 9/23/15.
 */
@Getter
@Builder
public class VideoConfiguration {
    private final File file;
    private final boolean horizontallyFlipped;

}
