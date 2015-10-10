package com.trioscope.chameleon.metrics;

import lombok.Getter;

/**
 * Created by rohitraghunathan on 7/13/15.
 */
public class MetricNames {

    @Getter
    public enum Category {

        WIFI("WIFI", "Metrics related to Wifi"),
        VIDEO_SYNC("VIDEO_SYNC", "Metrics related to syncing of videos"),
        VIDEO("VIDEO", "Metrics related to videos recorded");

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
        FRAME_DELAY_MILLIS("FRAME_DELAY_MILLIS"),
        DURATION("DURATION"),
        MERGE_TIME("MERGE_TIME"),
        CREW_ESTABLISH_CONNECTION_TIME("CREW_ESTABLISH_CONNECTION_TIME"),
        TIME_TO_TRANSFER_FILE("TIME_TO_TRANSFER_FILE");

        private String name;

        Label(String name) {

            this.name = name;
        }
    }

}
