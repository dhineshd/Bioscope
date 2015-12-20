package com.trioscope.chameleon.util.merge;

import java.io.File;
import java.util.Collection;

/**
 * Created by phand on 7/9/15.
 */
public interface VideoMerger {
    String MERGE_NOTIFICATION_TITLE = "Bioscope";
    String MERGE_IN_PROGRESS_NOTIFICATION_TEXT = "Saving file...";
    String MERGE_COMPLETED_NOTIFICATION_TEXT = "Saved file successfully";

    int MERGE_LAYOUT_TYPE_SIDE_BY_SIDE = 1;
    int MERGE_LAYOUT_TYPE_PICTURE_IN_PICTURE = 2;

    void addProgressUpdateable(String fileName, ProgressUpdatable progressUpdatable);

    void mergeVideos(
            VideoConfiguration videoConfig1, VideoConfiguration videoConfig2,
            File outputDestination, MergeConfiguration configuration);

    Collection<File> getTemporaryFiles();

    VideoConfiguration getMajorVideo(String mergedOutputFilename);

    VideoConfiguration getMinorVideo(String mergedOutputFilename);

    MergeConfiguration getMergeConfiguration(String mergedOutputFilename);
}
