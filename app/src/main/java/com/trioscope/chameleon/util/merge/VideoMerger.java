package com.trioscope.chameleon.util.merge;

import java.io.File;
import java.util.Collection;

/**
 * Created by phand on 7/9/15.
 */
public interface VideoMerger {
    void mergeVideos(VideoConfiguration videoConfig1, VideoConfiguration videoConfig2, File outputDestination, MergeConfiguration configuration);

    Collection<File> getTemporaryFiles();

    void setProgressUpdatable(ProgressUpdatable progressUpdatable);
}
