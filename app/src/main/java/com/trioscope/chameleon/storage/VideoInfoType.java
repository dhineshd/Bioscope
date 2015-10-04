package com.trioscope.chameleon.storage;

import lombok.Getter;

/**
 * Created by phand on 9/30/15.
 */
public enum VideoInfoType {
    VIDEOGRAPHER(1), BEING_MERGED(2);

    @Getter
    private final int typeValue;

    VideoInfoType(int i) {
        this.typeValue = i;
    }
}
