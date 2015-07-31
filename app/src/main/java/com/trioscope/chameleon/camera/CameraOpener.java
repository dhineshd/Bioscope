package com.trioscope.chameleon.camera;

import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * CameraOpener to open the camera on a separate thread. Useful because callback methods happen on the thread that the camera was opened on
 * <p/>
 * Created by phand on 7/31/15.
 */
@Slf4j
public class CameraOpener {
    @Getter
    private Camera camera;
    private CameraHandlerThread thread = null;

    public void openCamera() {
        if (thread == null) {
            thread = new CameraHandlerThread();
        }

        synchronized (thread) {
            thread.openCamera();
            log.info("Camera opened {}", camera);
        }
    }

    public void release() {
        camera.release();
        camera = null;
    }


    private class CameraHandlerThread extends HandlerThread {
        Handler handler = null;

        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            handler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        camera = Camera.open();
                    } catch (RuntimeException e) {
                        log.error("failed to open camera", e);
                    }
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            } catch (InterruptedException e) {
                log.warn("wait was interrupted");
            }
        }
    }
}
