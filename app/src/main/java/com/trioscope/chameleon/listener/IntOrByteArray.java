package com.trioscope.chameleon.listener;

import lombok.Data;

/**
 * Created by phand on 7/31/15.
 */
@Data
public class IntOrByteArray {
    private final int[] ints;
    private final byte[] bytes;

    public IntOrByteArray(int[] ints) {
        this.ints = ints;
        this.bytes = null;
    }

    public IntOrByteArray(byte[] bytes) {
        this.ints = null;
        this.bytes = bytes;
    }
}
