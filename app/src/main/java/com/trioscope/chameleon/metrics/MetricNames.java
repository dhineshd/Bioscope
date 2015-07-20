package com.trioscope.chameleon.metrics;

import lombok.Getter;

/**
 * Created by rohitraghunathan on 7/13/15.
 */
public class MetricNames {

    @Getter
    public enum Category {

        WIFI("WIFI", "Metrics related to Wifi");

        private String description;
        private String name;

        Category(String name, String description) {

            this.name = name;
            this.description = description;
        }
    }

    @Getter
    public enum Label {

        ENABLE("ENABLE");

        private String name;

        Label(String name) {

            this.name = name;
        }
    }

}
