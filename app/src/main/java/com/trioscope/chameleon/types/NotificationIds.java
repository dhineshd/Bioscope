package com.trioscope.chameleon.types;

import lombok.Getter;

/**
 * Created by phand on 7/13/15.
 */
public enum NotificationIds {
    MERGING_VIDEOS(1), MERGING_VIDEOS_COMPLETE(2);

    @Getter
    private int id;

    NotificationIds(int id) {
        this.id = id;
    }
}
