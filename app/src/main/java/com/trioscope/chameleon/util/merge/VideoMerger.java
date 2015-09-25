package com.trioscope.chameleon.util.merge;

import java.io.File;

/**
 * Created by phand on 7/9/15.
 */
public interface VideoMerger {
    void mergeVideos(VideoConfiguration videoConfig1, VideoConfiguration videoConfig2, File outputDestination, MergeConfiguration configuration);
}
