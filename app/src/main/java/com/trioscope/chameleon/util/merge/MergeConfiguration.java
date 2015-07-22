package com.trioscope.chameleon.util.merge;

import lombok.Data;

/**
 * Created by phand on 7/22/15.
 */
@Data
public class MergeConfiguration {
    /*
     * Offset between video_1 and video_2 in milliseconds. Positive offset +x means video_2's start time is actually video_1 + xms
     */
    double videoStartOffsetMilli;


}
