package com.trioscope.chameleon.handler;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 7/7/15.
 */
@Slf4j
@RequiredArgsConstructor
public class ImageViewHandler extends Handler {
    public static final int BITMAP_READY = 1;
    private final ImageView imageView;

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == BITMAP_READY) {
            log.info("Message received - setting bitmap");
            Bitmap bitmap = (Bitmap) msg.obj;
            imageView.setImageBitmap(bitmap);
        } else {
            super.handleMessage(msg);
        }
    }
}
