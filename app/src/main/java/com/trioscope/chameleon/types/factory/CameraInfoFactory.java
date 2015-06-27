package com.trioscope.chameleon.types.factory;

import android.hardware.Camera;

import com.trioscope.chameleon.types.CameraInfo;

/**
 * Created by phand on 6/26/15.
 */
public class CameraInfoFactory {

    public static CameraInfo createCameraInfo(Camera.Parameters params) {

        Camera.Size previewSize = params.getPreviewSize();

        return CameraInfo.builder().size(new CameraInfo.Size(previewSize.width, previewSize.height)).build();
    }
}
