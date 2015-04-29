package com.trioscope.chameleon.service;

import android.os.Binder;

import lombok.Getter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderBinder extends Binder {
    @Getter
    private final BackgroundRecorderService service;

    public BackgroundRecorderBinder(BackgroundRecorderService backgroundRecorder) {
        this.service = backgroundRecorder;
    }
}
