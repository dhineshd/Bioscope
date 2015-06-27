package com.trioscope.chameleon.types;


import lombok.Builder;
import lombok.Data;

/**
 * Created by phand on 6/26/15.
 */
@Data
@Builder
public class CameraInfo {
    private final Size size;

    @Data
    public static class Size {
        private final int width, height;
    }
}
