package com.trioscope.chameleon.types.factory;

import android.hardware.Camera;

import com.trioscope.chameleon.types.CameraInfo;

import static com.trioscope.chameleon.types.CameraInfo.CameraInfoBuilder;

/**
 * Created by phand on 6/26/15.
 */
public class CameraInfoFactory {

    public static CameraInfo createCameraInfo(Camera.Parameters params) {
        CameraInfoBuilder builder = CameraInfo.builder();
        Camera.Size previewSize = params.getPreviewSize();

        builder.height(previewSize.height).width(previewSize.width);

        return builder.build();
    }
}
