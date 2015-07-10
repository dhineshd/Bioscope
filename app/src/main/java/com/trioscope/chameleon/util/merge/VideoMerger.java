package com.trioscope.chameleon.util.merge;

import java.io.File;

/**
 * Created by phand on 7/9/15.
 */
public interface VideoMerger {
    void mergeVideos(File video1, File video2, File outputDestination);
}
