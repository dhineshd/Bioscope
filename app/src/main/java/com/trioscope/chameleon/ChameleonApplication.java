package com.trioscope.chameleon;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.WindowManager;

import com.trioscope.chameleon.activity.MainActivity;
import com.trioscope.chameleon.camera.BackgroundRecorder;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.metrics.MetricsHelper;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.stream.ConnectionServer;
import com.trioscope.chameleon.stream.RecordingEventListener;
import com.trioscope.chameleon.stream.VideoStreamFrameListener;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;
import com.trioscope.chameleon.types.MetricNames;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.SessionStatus;
import com.trioscope.chameleon.types.factory.CameraInfoFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 6/4/15.
 */
public class ChameleonApplication extends Application {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_AUDIO = 3;
    public static final String START_RECORDING_ACTION = "START_RECORDING";
    public static final String STOP_RECORDING_ACTION = "STOP_RECORDING";
    public static final int STREAM_IMAGE_BUFFER_SIZE = 1024 * 20;

    public static final int SERVER_PORT = 7080;

    @Getter
    private BackgroundRecorder videoRecorder;
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
    @Getter
    @Setter
    private volatile File videoFile;
    @Getter
    @Setter
    private volatile Long recordingStartTimeMillis;

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

    @Getter
    private static MetricsHelper metrics;

    private BroadcastReceiver enableWifiBroadcastReceiver;

    private Boolean isWifiEnabledInitially;

    @Override
    public void onCreate() {
        super.onCreate();
        LOG.info("Starting application");

        metrics = new MetricsHelper(this);

        isWifiEnabledInitially = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).isWifiEnabled();

        LOG.info("Wifi initial enabled state = {}", isWifiEnabledInitially);

        // Enable Wifi to save time later and to avoid constantly turning on/off
        // which affects battery performance
        if (!isWifiEnabledInitially) {
            enableWifiAndPerformActionWhenEnabled(null);
        }

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

        streamListener = new VideoStreamFrameListener(this);
        cameraFrameBuffer.addListener(streamListener);
    }


    public void createBackgroundRecorder(final RecordingEventListener recordingEventListener) {
        videoRecorder = new BackgroundRecorder(this, recordingEventListener);
    }

    public void startConnectionServerIfNotRunning(){
        // Setup connection server to receive connections from client
        if (connectionServer == null){
            connectionServer = new ConnectionServer(
                    ChameleonApplication.SERVER_PORT,
                    streamListener,
                    getInitializedSSLServerSocketFactory());
            connectionServer.start();
        }
    }

    private SSLServerSocketFactory getInitializedSSLServerSocketFactory(){
        SSLServerSocketFactory sslServerSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore keyStore = KeyStore.getInstance("BKS");
            InputStream keyStoreInputStream =  getApplicationContext().getResources().openRawResource(R.raw.chameleon_keystore);
            char[] password = "poiuyt".toCharArray();
            keyStore.load(keyStoreInputStream, password);
            keyStoreInputStream.close();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
            sslServerSocketFactory = sslContext.getServerSocketFactory();
        } catch (IOException |
                NoSuchAlgorithmException |
                KeyStoreException |
                KeyManagementException |
                CertificateException |
                UnrecoverableKeyException e) {
            LOG.error("Failed to initialize SSL server socket factory", e);
        }
        return sslServerSocketFactory;
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

    public void cleanup(){
        LOG.info("Tearing down application resources..");

        //  Tear down server
        if (connectionServer != null){
            connectionServer.stop();
            connectionServer = null;
        }

        // Reset session flags
        sessionStatus = SessionStatus.DISCONNECTED;
        streamListener.setStreamingStarted(false);

        // Tear down Wifi hotspot
        tearDownWifiHotspot();

        // Tear down wifi if necessary
        tearDownWifiIfNecessary();
    }

    public void tearDownWifiHotspot() {

        LOG.debug("Tearing down Wifi components..");

        // Tear down Wifi p2p hotspot
        if (wifiP2pManager != null && wifiP2pChannel != null){
            LOG.info("Invoking removeGroup..");
            wifiP2pManager.removeGroup(wifiP2pChannel, null);
        }
        wifiP2pManager = null;
        wifiP2pChannel = null;
    }

    private void tearDownWifiIfNecessary() {

        LOG.debug("Tearing down Wifi components..");

        tearDownWifiHotspot();

        // Tear down Wifi receivers
        unregisterReceiverSafely(enableWifiBroadcastReceiver);
        enableWifiBroadcastReceiver = null;

        // Put Wifi back in original state

        if (isWifiEnabledInitially != null){
            LOG.info("Setting Wifi back to {}", isWifiEnabledInitially);
            final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(isWifiEnabledInitially);
            isWifiEnabledInitially = null;
        }
    }

    /**
     * Create a File for saving an image or video
     */
    public File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        LOG.info("DCIM directory is: {}", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM));

        if (!isExternalStorageWritable()) {
            LOG.error("External Storage is not mounted for Read-Write");
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), this.getString(R.string.app_name));
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                LOG.error("failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "CHAMELEON_" + timeStamp + ".mp4");
        } else if (type == MEDIA_TYPE_AUDIO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "AUD_" + timeStamp + ".3gp");
        } else {
            return null;
        }

        if (mediaFile != null) {
            LOG.info("File name is {}", mediaFile.getAbsolutePath());
        }
        return mediaFile;
    }

    /**
     * Create a file Uri for saving an image or video
     */
    private Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public boolean prepareVideoRecorder() {
        //Create a file for storing the recorded video
        videoFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);
        LOG.info("Setting video file = {}", videoFile.getAbsolutePath());
        videoRecorder.setOutputFile(videoFile);
        videoRecorder.setCamera(camera);
        return true;
    }

    public void finishVideoRecording() {
        videoRecorder.stopRecording();
        //camera.lock();         // take camera access back from video recorder

        if (videoFile != null) {
            //Send a broadcast about the newly added video file for Gallery Apps to recognize the video
            Intent addVideoIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            addVideoIntent.setData(Uri.fromFile(videoFile));

            sendBroadcast(addVideoIntent);
        }
    }

    /**
     * Enable wifi and optionally perform some action on wifi enabled (asynchronous).
     *
     * @param runnable (null if no action required)
     */
    public void enableWifiAndPerformActionWhenEnabled(final Runnable runnable){
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        final long startTime = System.currentTimeMillis();

        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        enableWifiBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LOG.info("onReceive intent = {}, wifi enabled = {}",
                        intent.getAction(), wifiManager.isWifiEnabled());

                if(wifiManager.isWifiEnabled()) {

                    //Publish time for wifi to be enabled
                    metrics.sendTime(MetricNames.Category.WIFI.getName(),
                            MetricNames.Label.ENABLE.getName(), System.currentTimeMillis() - startTime);

                    // Done with checking Wifi state
                    unregisterReceiverSafely(this);
                    LOG.info("Wifi enabled!!");

                    // Perform action
                    if (runnable != null){
                        runnable.run();
                    }

                }
            }
        };
        // register to listen for change in Wifi state
        registerReceiver(enableWifiBroadcastReceiver, filter);

        // Enable and wait for Wifi state change
        wifiManager.setWifiEnabled(true);
        LOG.info("SetWifiEnabled to true");
    }

    public void unregisterReceiverSafely(final BroadcastReceiver receiver){
        if (receiver != null) {
            try{
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                // ignoring this since this can happen due to some race conditions
            }
        }
    }

}
