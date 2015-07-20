package com.trioscope.chameleon.types.factory;

import android.hardware.Camera;

import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;

import java.math.BigInteger;

/**
 * Created by phand on 6/26/15.
 */
public class CameraInfoFactory {

    private static final int TARGET_PIXEL_COUNT_FOR_CAMERA_CAPTURE = 129600;
    public static CameraInfo createCameraInfo(Camera.Parameters params) {

        Camera.Size previewSize = params.getPreviewSize();

        CameraInfo.CameraInfoBuilder builder = CameraInfo.builder();

        builder.cameraResolution(new Size(previewSize.width, previewSize.height));
        builder.captureResolution(generateCaptureResolution(previewSize));

        return builder.build();
    }

    private static Size generateCaptureResolution(final Camera.Size previewSize){
        // Shrink the preview size to fit within target pixel count
        // for camera capture (for streaming) while maintaining original aspect ratio
        int w = previewSize.width, h = previewSize.height;
        int gcd = getGcd(w, h);
        int widthReductionFactor = previewSize.width / gcd;
        int heightReductionFactor = previewSize.height / gcd;
        for (;(w * h) > TARGET_PIXEL_COUNT_FOR_CAMERA_CAPTURE;
             w -= widthReductionFactor, h-= heightReductionFactor);
        return new Size(w, h);
    }

    private static int getGcd(final int a, final int b){
        return BigInteger.valueOf(a).gcd(BigInteger.valueOf(b)).intValue();
    }
}
