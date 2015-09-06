package com.trioscope.chameleon.types;

import android.os.Handler;
import android.os.HandlerThread;

import lombok.Getter;

/**
 * Created by phand on 8/4/15.
 */
public class ThreadWithHandler extends HandlerThread {
    @Getter
    private Handler handler;

    public ThreadWithHandler() {
        super("ThreadWithHandler");
        start();
        handler = new Handler(getLooper());
    }
}
