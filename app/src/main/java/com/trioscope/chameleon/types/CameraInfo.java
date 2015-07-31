package com.trioscope.chameleon.types;


import lombok.Builder;
import lombok.Data;

/**
 * Created by phand on 6/26/15.
 */
@Data
@Builder
public class CameraInfo {
    private final Size cameraResolution;
    private final Size captureResolution;
    private final ImageEncoding encoding;

    public enum ImageEncoding {
        NV21, RGBA_8888
    }
}
