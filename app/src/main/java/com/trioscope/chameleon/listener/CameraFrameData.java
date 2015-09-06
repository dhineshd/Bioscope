package com.trioscope.chameleon.listener;

import android.media.Image;

import lombok.Data;

/**
 * Created by phand on 7/31/15.
 */
@Data
public class CameraFrameData {
    private final int[] ints;
    private final byte[] bytes;
    private final Image image;

    public CameraFrameData(int[] ints) {
        this.ints = ints;
        this.bytes = null;
        this.image = null;
    }

    public CameraFrameData(byte[] bytes) {
        this.ints = null;
        this.bytes = bytes;
        this.image = null;
    }

    public CameraFrameData(Image image) {
        this.ints = null;
        this.bytes = null;
        this.image = image;
    }
}
