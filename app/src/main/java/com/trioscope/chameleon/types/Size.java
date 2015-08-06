package com.trioscope.chameleon.types;

import lombok.Data;

/**
 * Created by phand on 7/7/15.
 */
@Data
public class Size {
    private final int width, height;

    public Size(android.util.Size size) {
        this.width = size.getWidth();
        this.height = size.getHeight();
    }

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }
}
