package com.trioscope.chameleon.metrics;

import lombok.Getter;

/**
 * Created by rohitraghunathan on 7/13/15.
 */
public class MetricNames {

    @Getter
    public enum Category {

        WIFI("WIFI", "Metrics related to Wifi"),
        VIDEO_SYNC("VIDEO_SYNC", "Metrics related to syncing of videos");

        private String description;
        private String name;

        Category(String name, String description) {

            this.name = name;
            this.description = description;
        }
    }

    @Getter
    public enum Label {

        ENABLE("ENABLE"),
        FRAME_DELAY_MILLIS("FRAME_DELAY_MILLIS");

        private String name;

        Label(String name) {

            this.name = name;
        }
    }

}
