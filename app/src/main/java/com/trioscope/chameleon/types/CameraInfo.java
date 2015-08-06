package com.trioscope.chameleon.types;


import android.graphics.ImageFormat;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

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
        NV21(ImageFormat.NV21), YUV_420_888(ImageFormat.YUV_420_888), RGBA_8888(null);

        @Getter
        private Integer imageFormat;

        ImageEncoding(Integer imageFormat) {
            this.imageFormat = imageFormat;
        }
    }
}
