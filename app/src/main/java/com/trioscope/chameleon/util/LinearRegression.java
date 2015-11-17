package com.trioscope.chameleon.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Compute regression line given fixed number of data points
 * <p/>
 * Created by phand on 11/17/15.
 */
@Slf4j
public class LinearRegression {
    private static final int NUMBER_DATA_POINTS_TO_CONSIDER = 128;
    private int numDataPts;
    private double[] xs;
    private double[] ys;
    private long startTime;
    private boolean allowIntercept;

    public LinearRegression(boolean allowIntercept) {
        this.allowIntercept = allowIntercept;
        numDataPts = 0;
        xs = new double[NUMBER_DATA_POINTS_TO_CONSIDER];
        ys = new double[NUMBER_DATA_POINTS_TO_CONSIDER];
    }

    public void addData(double x, double y) {
        // Circular data pts buffer
        int index = numDataPts % NUMBER_DATA_POINTS_TO_CONSIDER;
        xs[index] = x;
        ys[index] = y;
        log.info("Adding data pt {},{}", x, y);
        numDataPts++;
    }

    public double[] getSlopeAndIntercept() {
        if (!allowIntercept) {
            double sumxy = 0.0, sumxx = 0.0;
            int n = Math.min(NUMBER_DATA_POINTS_TO_CONSIDER, numDataPts);
            for (int i = 0; i < n; i++) {
                sumxy += xs[i] * ys[i];
                sumxx += xs[i] * xs[i];
            }

            double slope = sumxy / sumxx;
            return new double[]{slope, 0.0};
        } else {
            double sumxy = 0.0, sumxx = 0.0, sumx = 0.0, sumy = 0.0;
            int n = Math.min(NUMBER_DATA_POINTS_TO_CONSIDER, numDataPts);
            for (int i = 0; i < n; i++) {
                sumxy += xs[i] * ys[i];
                sumxx += xs[i] * xs[i];
                sumx += xs[i];
                sumy += ys[i];
            }

            double xmean = sumx / n;
            double ymean = sumy / n;
            double xymean = sumxy / n;
            double xxmean = sumxx / n;

            double slope = (xymean - xmean * ymean) / (xxmean - Math.pow(xmean, 2));
            double intercept = ymean - slope * xmean;
            return new double[]{slope, intercept};
        }
    }
}
