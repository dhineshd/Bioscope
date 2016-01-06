package com.trioscope.chameleon.listener.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * A util for calculating the number of updates received per second
 * <p/>
 * Created by phand on 6/11/15.
 */
public class UpdateRateCalculator {
    private final Logger LOG = LoggerFactory.getLogger(UpdateRateCalculator.class);
    private static final int DEFAULT_INTERVAL = 5 * 1000;

    private final long updateMemoryTime;
    private final double secondsPerLog;
    private long totalUpdatesReceived = 0;
    private LinkedList<Long> frameTimings = new LinkedList<>();
    private long lastLog = 0;

    @Setter
    private boolean shouldLog = true;

    public UpdateRateCalculator() {
        this(DEFAULT_INTERVAL);
    }

    public UpdateRateCalculator(int loggingFreqMilli) {
        updateMemoryTime = loggingFreqMilli;
        secondsPerLog = updateMemoryTime / 1000;
    }

    @Builder
    private UpdateRateCalculator(int updateMemoryTime, boolean shouldLog) {
        this.updateMemoryTime = updateMemoryTime;
        secondsPerLog = updateMemoryTime / 1000;
        this.shouldLog = shouldLog;
    }

    public void updateReceived() {
        long curTime = System.currentTimeMillis();

        while (!frameTimings.isEmpty() && frameTimings.peekLast() + updateMemoryTime < curTime) {
            frameTimings.pollLast();
        }
        frameTimings.push(curTime);

        if (shouldLog && (lastLog + updateMemoryTime < curTime)) {
            lastLog = curTime;
            // Need to log here, but skip the first update
            if (totalUpdatesReceived > 0) {
                LOG.info("During last {}s, received {} updates per second", secondsPerLog, frameTimings.size() / secondsPerLog);
            }
        }
        totalUpdatesReceived++;
    }

    public double getRecentUpdateRate() {
        long curTime = System.currentTimeMillis();
        long lastTime = frameTimings.peekLast();
        long elapsed = curTime - lastTime;

        return (double) frameTimings.size() / (elapsed / 1000.0);
    }
}
