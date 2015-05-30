package com.trioscope.chameleon.service;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by phand on 5/29/15.
 */
public class ThreadLoggingHandler extends Handler {
    private static final Logger LOG = LoggerFactory.getLogger(ThreadLoggingHandler.class);

    public ThreadLoggingHandler() {
        super();
    }

    public ThreadLoggingHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
        LOG.info("Currently in thread {}", Thread.currentThread());
        super.handleMessage(msg);
    }
}
