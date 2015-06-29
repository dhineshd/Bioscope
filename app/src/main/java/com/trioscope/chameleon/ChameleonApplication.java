package com.trioscope.chameleon;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;

import com.trioscope.chameleon.broadcastreceiver.WiFiDirectBroadcastReceiver;
import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.stream.VideoStreamFrameListener;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.WiFiNetworkConnectionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 6/4/15.
 */
public class ChameleonApplication extends Application {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);
    private static ChameleonApplication instance;

    private VideoRecorder videoRecorder;

    @Getter
    @Setter
    private EGLContextAvailableMessage globalEglContextInfo;
    private MainActivity eglCallback;
    private final Object eglCallbackLock = new Object();

    // For background image recording
    private WindowManager windowManager;
    private SystemOverlayGLSurface surfaceView;
    private Camera camera;
    @Getter
    private CameraPreviewTextureListener cameraPreviewFrameListener = new CameraPreviewTextureListener();
    @Getter
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;

    @Getter
    private WifiP2pManager wifiP2pManager;
    @Getter
    private WifiP2pManager.Channel wifiP2pChannel;

    @Getter
    @Setter
    private WiFiNetworkConnectionInfo wiFiNetworkConnectionInfo;

    private WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;

    private IntentFilter wifiIntentFilter;

    @Getter
    private VideoStreamFrameListener streamListener;

    @Override
    public void onCreate() {
        super.onCreate();
        LOG.info("Starting application");

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        surfaceView = new SystemOverlayGLSurface(this, new EGLContextAvailableHandler());
        surfaceView.setCameraFrameBuffer(cameraFrameBuffer);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);
        LOG.info("Created system overlay SurfaceView {}", surfaceView);

        // Add FPS listener to CameraBuffer
        cameraFrameBuffer.addListener(new UpdateRateListener());
        streamListener = new VideoStreamFrameListener();
        cameraFrameBuffer.addListener(streamListener);
    }

    @Override
    public void onTerminate() {

        if(wiFiDirectBroadcastReceiver!=null) {
            unregisterReceiver(wiFiDirectBroadcastReceiver);
        }

        super.onTerminate();
        LOG.info("Terminating application");
    }

    public void setEglContextCallback(MainActivity mainActivity) {
        synchronized (eglCallbackLock) {
            LOG.info("Adding EGLContextCallback for when EGLContext is available");
            if (globalEglContextInfo != null) {
                LOG.info("EGLContext immediately available, calling now");
                eglCallback.eglContextAvailable(globalEglContextInfo);
                startPreview();
            } else {
                LOG.info("EGLContext not immediately available, going to call later");
                eglCallback = mainActivity;
            }
        }
    }

    private void startPreview() {
        if (!previewStarted) {
            LOG.info("Grabbing camera and starting preview");
            camera = Camera.open();
            try {
                cameraPreviewFrameListener.addFrameListener(new RenderRequestFrameListener(surfaceView));
                globalEglContextInfo.getSurfaceTexture().setOnFrameAvailableListener(cameraPreviewFrameListener);
                camera.setPreviewTexture(globalEglContextInfo.getSurfaceTexture());
                camera.startPreview();
            } catch (IOException e) {
                LOG.error("Error starting camera preview", e);
            }
            previewStarted = true;
        } else {
            LOG.info("Preview already started");
        }
    }

    public class EGLContextAvailableHandler extends Handler {
        public static final int EGL_CONTEXT_AVAILABLE = 1;

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EGL_CONTEXT_AVAILABLE) {
                synchronized (eglCallbackLock) {
                    LOG.info("EGL context is created and available");
                    globalEglContextInfo = (EGLContextAvailableMessage) msg.obj;
                    startPreview();

                    if (eglCallback != null) {
                        LOG.info("Now calling eglCallback since EGLcontext is available");
                        eglCallback.eglContextAvailable(globalEglContextInfo);
                    }
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }



    public void initializeWifi() {

        if(wifiP2pManager == null) {
            LOG.debug("Acquiring WifiP2pManager");
            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);

            LOG.debug("Acquiring WifiChannel");
            wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);

            wiFiDirectBroadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager, wifiP2pChannel);

            wifiIntentFilter = new IntentFilter();
            wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            wifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

            registerReceiver(wiFiDirectBroadcastReceiver, wifiIntentFilter);

        }
    }


}
