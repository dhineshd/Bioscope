package com.trioscope.chameleon;

import android.app.Application;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.wifi.p2p.WifiP2pManager;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;
import com.trioscope.chameleon.activity.MainActivity;
import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.stream.ConnectionServer;
import com.trioscope.chameleon.stream.VideoStreamFrameListener;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.SessionStatus;
import com.trioscope.chameleon.types.factory.CameraInfoFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 6/4/15.
 */
public class ChameleonApplication extends Application {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);
    public final static int SERVER_PORT = 7080;
    public static final int DISPATCH_PERIOD_IN_SECONDS = 1800;
    private VideoRecorder videoRecorder;

    @Getter
    private RotationState rotationState = new RotationState();

    @Getter
    @Setter
    private EGLContextAvailableMessage globalEglContextInfo;
    @Getter
    @Setter
    private GLSurfaceView.EGLContextFactory eglContextFactory;

    private MainActivity eglCallback;
    private final Object eglCallbackLock = new Object();

    // For background image recording
    private WindowManager windowManager;
    private SystemOverlayGLSurface surfaceView;
    @Getter
    private Camera camera;
    @Getter
    private CameraInfo cameraInfo;
    @Getter
    private CameraPreviewTextureListener cameraPreviewFrameListener = new CameraPreviewTextureListener();
    @Getter
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;
    private EGLContextAvailableHandler eglContextAvailHandler;
    @Getter
    @Setter
    private EGLConfig eglConfig;


    @Getter
    private WifiP2pManager wifiP2pManager;
    @Getter
    private WifiP2pManager.Channel wifiP2pChannel;

    @Getter
    @Setter
    private PeerInfo peerInfo;

    @Getter
    private volatile VideoStreamFrameListener streamListener;
    @Setter
    private ConnectionServer connectionServer;
    @Getter
    @Setter
    private volatile SessionStatus sessionStatus = SessionStatus.DISCONNECTED;

    @Setter
    @Getter
    private boolean isDirector;

    public static final String GOOGLE_ANALYTICS_CHAMELEON_TRACKING_ID = "UA-65062909-1";

    @Getter
    private static Tracker metricsTracker;

    @Override
    public void onCreate() {
        super.onCreate();
        LOG.info("Starting application");

        setupMetrics();


        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        eglContextAvailHandler = new EGLContextAvailableHandler();
        surfaceView = new SystemOverlayGLSurface(this, eglContextAvailHandler);
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
        LOG.info("Terminating chameleon..");
        super.onTerminate();
    }

    public void startConnectionServerIfNotRunning(){
        SSLContext sslContext = null; // JSSE and OpenSSL providers behave the same way
        try {
            sslContext = SSLContext.getInstance("TLS");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            KeyStore ks = KeyStore.getInstance("BKS");
            char[] password = "poiuyt".toCharArray(); // TODO: Move to build config
            // we assume the keystore is in the app assets
            InputStream sslKeyStore =  getApplicationContext().getResources().openRawResource(R.raw.chameleon_keystore);
            ks.load(sslKeyStore, null);
            sslKeyStore.close();
            kmf.init(ks, password);
            sslContext.init( kmf.getKeyManagers(), null, new SecureRandom() );
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Setup connection server to receive connections from client
        if (connectionServer == null){
            connectionServer = new ConnectionServer(
                    ChameleonApplication.SERVER_PORT, streamListener, sslContext);
            connectionServer.start();
        }
    }

    public void setEglContextCallback(MainActivity mainActivity) {
        synchronized (eglCallbackLock) {
            LOG.info("Adding EGLContextCallback for when EGLContext is available");
            if (globalEglContextInfo != null) {
                LOG.info("EGLContext immediately available, calling now");
                mainActivity.eglContextAvailable(globalEglContextInfo);
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

            Camera.Parameters params = camera.getParameters();

            cameraInfo = CameraInfoFactory.createCameraInfo(params);
            surfaceView.setCameraInfo(cameraInfo);
            LOG.info("CameraInfo for opened camera is {}", cameraInfo);

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

    public synchronized void updateOrientation() {
        LOG.info("Updating current device orientation");

        int orientation = getResources().getConfiguration().orientation;

        boolean isLandscape = getResources().getConfiguration().ORIENTATION_LANDSCAPE == orientation;
        LOG.info("Device is in {} mode", isLandscape ? "landscape" : "portrait");
        rotationState.setLandscape(isLandscape);
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
        }
    }

    public SurfaceTextureDisplay generatePreviewDisplay(final EGLContextAvailableMessage contextMessage){
        SurfaceTextureDisplay previewDisplay = new SurfaceTextureDisplay(this);
        previewDisplay.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
            @Override
            public javax.microedition.khronos.egl.EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                LOG.info("Creating shared EGLContext");
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };

                EGLContext newContext = ((EGL10) EGLContext.getEGL()).eglCreateContext(
                        display, eglConfig, contextMessage.getEglContext(), attrib2_list);
                LOG.info("Created a shared EGL context: {}", newContext);
                return newContext;
            }

            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display, javax.microedition.khronos.egl.EGLContext context) {
                LOG.info("EGLContext is being destroyed");
                egl.eglDestroyContext(display, context);
            }
        });

        previewDisplay.setTextureId(contextMessage.getGlTextureId());
        previewDisplay.setToDisplay(contextMessage.getSurfaceTexture());
        //previewDisplay.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        previewDisplay.setRenderer(previewDisplay.new SurfaceTextureRenderer(rotationState));
        return previewDisplay;
    }

    public void tearDownNetworkComponents(){
        LOG.info("Tearing down application resources..");

        // TODO : Any particular order in which we need to do the following?

        //  Tear down server
        if (connectionServer != null){
            connectionServer.stop();
            connectionServer = null;
        }

        LOG.debug("Tearing down Wifi hotspot..");

        // Tear down Wifi p2p hotspot
        if (wifiP2pManager != null && wifiP2pChannel != null){
            LOG.info("Invoking removeGroup..");
            wifiP2pManager.removeGroup(wifiP2pChannel, null);
        }
        wifiP2pManager = null;
        wifiP2pChannel = null;

        // Reset session flags
        sessionStatus = SessionStatus.DISCONNECTED;
        streamListener.setStreamingStarted(false);
    }

    private void setupMetrics() {
        GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
        analytics.setLocalDispatchPeriod(DISPATCH_PERIOD_IN_SECONDS);

        metricsTracker = analytics.newTracker(GOOGLE_ANALYTICS_CHAMELEON_TRACKING_ID);

        // Provide unhandled exceptions reports. Do that first after creating the tracker
        metricsTracker.enableExceptionReporting(true);

        // Enable Remarketing, Demographics & Interests reports
        // https://developers.google.com/analytics/devguides/collection/android/display-features
        metricsTracker.enableAdvertisingIdCollection(true);

        // Enable automatic activity tracking for your app
        metricsTracker.enableAutoActivityTracking(true);
    }
}
