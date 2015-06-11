package com.trioscope.chameleon.listener.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

/**
 * A util for calculating the number of updates received per second
 * <p/>
 * Created by phand on 6/11/15.
 */
public class UpdateRateCalculator {
    private final Logger LOG = LoggerFactory.getLogger(UpdateRateCalculator.class);
    private static final int DEFAULT_INTERVAL = 5 * 1000;

    private final long milliPerLog;
    private final double secondsPerLog;
    private long totalUpdatesReceived = 0;
    private LinkedList<Long> frameTimings = new LinkedList<>();
    private long lastLog = 0;

    public UpdateRateCalculator() {
        this(DEFAULT_INTERVAL);
    }

    public UpdateRateCalculator(int loggingFreqMilli) {
        milliPerLog = loggingFreqMilli;
        secondsPerLog = milliPerLog / 1000;
    }

    public void updateReceived() {
        long curTime = System.currentTimeMillis();

        while (!frameTimings.isEmpty() && frameTimings.peekLast() + milliPerLog < curTime) {
            frameTimings.pollLast();
        }
        frameTimings.push(curTime);

        if (lastLog + milliPerLog < curTime) {
            lastLog = curTime;
            // Need to log here, but skip the first update
            if (totalUpdatesReceived > 0) {
                LOG.info("During last {}s, received {} updates per second", secondsPerLog, frameTimings.size() / secondsPerLog);
            }
        }
        totalUpdatesReceived++;
    }
}
