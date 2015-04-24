package com.trioscope.chameleon.camera;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by rohitraghunathan on 4/7/15.
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = "CameraPreview";

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
        Log.d(TAG, "surfaceCreated called");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        Log.d(TAG, "surfaceChanged called");

        if (surfaceHolder.getSurface() == null) {
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            camera.stopPreview();
            Log.d(TAG, "Preview Display Stopped");
        } catch (Exception e) {
            // ignore: tried to stop a non-existent preview
        }

        // TODO: Handle landscape mode here?
        // set preview size and make any resize, rotate or
        // reformatting changes here

        // Print preview and surface size for debugging
        Camera.Parameters params = camera.getParameters();
        Log.d(TAG, "Camera preview size width, height: " + params.getPreviewSize().width + ", " + params.getPreviewSize().height);

        Log.d(TAG, "Surface view size width, height: " + width + ", " + height);

        // start preview with new settings
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            Log.d(TAG, "Preview Display Started");

        } catch (Exception e) {
            Log.e(TAG, "Error setting camera preview: ", e);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
        Log.d(TAG, "surfaceDestroyed called");
    }

    public SurfaceHolder getSurfaceHolder() {
        return this.surfaceHolder;
    }
}
