package com.trioscope.chameleon.camera;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by rohitraghunathan on 4/7/15.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final Logger LOG = LoggerFactory.getLogger(CameraPreview.class);

    private SurfaceHolder surfaceHolder;

    private Camera camera;

    public CameraPreview(Context context, Camera camera) {
        super(context);

        this.camera = camera;
        this.surfaceHolder = getHolder();
        this.surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // Do nothing
        LOG.info("Surface Created");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        LOG.info("Surface changed");

        if (surfaceHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
            LOG.info("Preview Display Stopped");
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // TODO: Handle landscape mode here?
        // set preview size and make any resize, rotate or
        // reformatting changes here

        // Print preview and surface size for debugging
        Camera.Parameters params = camera.getParameters();
        LOG.info("Camera preview size width, height: {}, {}", params.getPreviewSize().width, params.getPreviewSize().height);

        LOG.info("Surface view size width, height: {}, {}", width, height);

        // start preview with new settings
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            LOG.info("Preview Display Started");

        } catch (Exception e) {
            LOG.error("Error setting camera preview: ", e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
        LOG.info("Surface destroyed");
    }

    public SurfaceHolder getSurfaceHolder() {
        return this.surfaceHolder;
    }
}
