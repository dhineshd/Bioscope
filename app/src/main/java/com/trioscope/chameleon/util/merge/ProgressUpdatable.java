package com.trioscope.chameleon.util.merge;

/**
 * Created by phand on 7/10/15.
 */
public interface ProgressUpdatable {
    public void onProgress(double progress, double outOf);

    public void onCompleted();
}
