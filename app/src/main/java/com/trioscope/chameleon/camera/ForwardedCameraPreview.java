package com.trioscope.chameleon.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

/**
 * Created by phand on 5/13/15.
 */
public class ForwardedCameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private static final Logger LOG = LoggerFactory.getLogger(ForwardedCameraPreview.class);

    public ForwardedCameraPreview(Context context) {
        super(context);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void drawData(byte[] data, Camera.Parameters parameters) {
        LOG.info("Drawing bytes length {}", data.length);
        int width = parameters.getPreviewSize().width;
        int height = parameters.getPreviewSize().height;
        LOG.info("Camera width and height is {}x{}", width, height);

        YuvImage yuv = new YuvImage(data, parameters.getPreviewFormat(), width, height, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

        byte[] bytes = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        Canvas canvas = getHolder().lockCanvas();

        if (canvas != null) {
            canvas.drawBitmap(bitmap, 0, 0, null);
        }

        getHolder().unlockCanvasAndPost(canvas); //finalize
    }
}