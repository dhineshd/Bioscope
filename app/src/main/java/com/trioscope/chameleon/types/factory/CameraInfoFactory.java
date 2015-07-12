package com.trioscope.chameleon.types.factory;

import android.hardware.Camera;

import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;

/**
 * Created by phand on 6/26/15.
 */
public class CameraInfoFactory {

    private static final int CAP_WIDTH = 640;
    private static final int CAP_HEIGHT = 360;

    public static CameraInfo createCameraInfo(Camera.Parameters params) {

        Camera.Size previewSize = params.getPreviewSize();

        return CameraInfo.builder().cameraResolution(new Size(previewSize.width, previewSize.height)).captureResolution(new Size(CAP_WIDTH, CAP_HEIGHT)).build();
    }
}
