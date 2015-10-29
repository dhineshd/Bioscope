package com.trioscope.chameleon.util.merge;

import java.io.File;
import java.util.Collection;

/**
 * Created by phand on 7/9/15.
 */
public interface VideoMerger {
    public static final String MERGE_NOTIFICATION_TITLE = "Bioscope video merge";
    public static final String MERGE_IN_PROGRESS_NOTIFICATION_TEXT = "Merge is in progress";
    public static final String MERGE_COMPLETED_NOTIFICATION_TEXT = "Merge completed";

    void mergeVideos(VideoConfiguration videoConfig1, VideoConfiguration videoConfig2, File outputDestination, MergeConfiguration configuration);

    Collection<File> getTemporaryFiles();

    void setProgressUpdatable(ProgressUpdatable progressUpdatable);
}
