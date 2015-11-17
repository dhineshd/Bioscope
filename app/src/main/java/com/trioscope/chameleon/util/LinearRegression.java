package com.trioscope.chameleon.util;

/**
 * Compute regression line given fixed number of data points
 * <p/>
 * Created by phand on 11/17/15.
 */
public class LinearRegression {
    private static final int NUMBER_DATA_POINTS_TO_CONSIDER = 50;
    private int numDataPts;
    private double[] xs;
    private double[] ys;
    private long startTime;

    public LinearRegression() {
        numDataPts = 0;
        xs = new double[NUMBER_DATA_POINTS_TO_CONSIDER];
        ys = new double[NUMBER_DATA_POINTS_TO_CONSIDER];
    }

    public void addData(double x, double y) {
        // Circular data pts buffer
        int index = numDataPts % NUMBER_DATA_POINTS_TO_CONSIDER;
        xs[index] = x;
        ys[index] = y;

        numDataPts++;
    }

    public double getSlope() {
        double sumxy = 0.0, sumxx = 0.0;
        int n = Math.min(NUMBER_DATA_POINTS_TO_CONSIDER, numDataPts);
        for (int i = 0; i < n; i++) {
            sumxy += xs[i] * ys[i];
            sumxx += xs[i] * xs[i];
        }

        double slope = sumxy / sumxx;
        return slope;
    }
}
