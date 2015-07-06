package com.trioscope.chameleon.service;

import android.app.Service;
import android.os.Binder;

import lombok.Getter;

/**
 * Created by phand on 4/29/15.
 */
public class BackgroundRecorderBinder<T extends Service> extends Binder {
    @Getter
    private final T service;

    public BackgroundRecorderBinder(T backgroundRecorder) {
        this.service = backgroundRecorder;
    }
}
