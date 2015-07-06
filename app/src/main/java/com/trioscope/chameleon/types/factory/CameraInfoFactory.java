package com.trioscope.chameleon.types.factory;

import android.hardware.Camera;

import com.trioscope.chameleon.types.CameraInfo;

/**
 * Created by phand on 6/26/15.
 */
public class CameraInfoFactory {

    private static final int CAP_WIDTH = 160;
    private static final int CAP_HEIGHT = 90;

    public static CameraInfo createCameraInfo(Camera.Parameters params) {

        Camera.Size previewSize = params.getPreviewSize();

        CameraInfo.CameraInfoBuilder builder = CameraInfo.builder();

        builder.cameraResolution(new CameraInfo.Size(previewSize.width, previewSize.height));
        builder.captureResolution(new CameraInfo.Size(CAP_WIDTH, CAP_HEIGHT));

        return builder.build();
    }
}
