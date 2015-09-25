package com.trioscope.chameleon;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Environment;
import android.view.SurfaceView;

import com.trioscope.chameleon.broadcastreceiver.IncomingPhoneCallBroadcastReceiver;
import com.trioscope.chameleon.camera.CameraOpener;
import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.camera.impl.Camera2PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.metrics.MetricsHelper;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.stream.ConnectionServer;
import com.trioscope.chameleon.stream.VideoRecordingFrameListener;
import com.trioscope.chameleon.stream.VideoStreamFrameListener;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.PeerInfo;
import com.trioscope.chameleon.types.SessionStatus;
import com.trioscope.chameleon.types.Size;
import com.trioscope.chameleon.types.ThreadWithHandler;

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

import javax.microedition.khronos.egl.EGLConfig;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/4/15.
 */
@Slf4j
public class ChameleonApplication extends Application {
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_AUDIO = 3;
    public static final String START_RECORDING_ACTION = "START_RECORDING";
    public static final String STOP_RECORDING_ACTION = "STOP_RECORDING";

    public static final int SEND_VIDEO_TO_PEER_MESSAGE = 101;

    // Stream image buffer size is set to be the same as minimum socket buffer size
    // which ensures that each image can be transferred in a single send eliminating
    // the need to maintain sequence numbers when sending data. So, we
    // need to ensure that the compressed stream image size is less than this value.
    public static final int STREAM_IMAGE_BUFFER_SIZE_BYTES = 1024 * 16;
    public static final int SEND_RECEIVE_BUFFER_SIZE_BYTES = 64 * 1024;
    public static final double DEFAULT_ASPECT_WIDTH_RATIO = 16;
    public static final double DEFAULT_ASPECT_HEIGHT_RATIO = 9;
    public static final Size DEFAULT_CAMERA_PREVIEW_SIZE = new Size(1920, 1080);

    public static final String APP_FONT_LOCATION = "fonts/Idolwild/idolwild.ttf";


    public static final int SERVER_PORT = 7080;

    @Getter
    private RotationState rotationState = new RotationState();

    @Getter
    @Setter
    private volatile File videoFile;

    @Getter
    @Setter
    private volatile Long recordingStartTimeMillis;
    @Getter
    @Setter
    private volatile boolean recordingHorizontallyFlipped;

    @Getter
    private CameraOpener cameraOpener;

    @Getter
    private CameraInfo cameraInfo;

    @Getter
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;

    @Getter
    @Setter
    private EGLConfig eglConfig;

    @Getter
    private PreviewDisplayer previewDisplayer;

    @Getter
    private WifiP2pManager wifiP2pManager;
    @Getter
    private WifiP2pManager.Channel wifiP2pChannel;

    @Getter
    @Setter
    private PeerInfo peerInfo;

    @Getter
    private volatile VideoStreamFrameListener streamListener;
    @Getter
    private volatile VideoRecordingFrameListener recordingFrameListener;
    @Setter
    private ConnectionServer connectionServer;
    @Getter
    @Setter
    private volatile SessionStatus sessionStatus = SessionStatus.DISCONNECTED;

    @Getter
    private static MetricsHelper metrics;

    // Receivers
    private BroadcastReceiver enableWifiBroadcastReceiver;
    private IncomingPhoneCallBroadcastReceiver incomingPhoneCallBroadcastReceiver;

    private Boolean isWifiEnabledInitially;

    @Override
    public void onCreate() {
        super.onCreate();
        log.info("Starting application");

        // Disabling metrics for now
        //metrics = new MetricsHelper(this);

        isWifiEnabledInitially = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).isWifiEnabled();

        log.info("Wifi initial enabled state = {}", isWifiEnabledInitially);

        // Enable Wifi to save time later and to avoid constantly turning on/off
        // which affects battery performance
        if (!isWifiEnabledInitially) {
            enableWifiAndPerformActionWhenEnabled(null);
        }

        recordingFrameListener = new VideoRecordingFrameListener(this);

        streamListener = new VideoStreamFrameListener(this);
        //cameraFrameBuffer.addListener(streamListener);

        // Add FPS listener to CameraBuffer
        cameraFrameBuffer.addListener(new UpdateRateListener());

        startup();
    }

    public void startConnectionServerIfNotRunning() {
        // Setup connection server to receive connections from client
        if (connectionServer == null) {
            connectionServer = new ConnectionServer(
                    ChameleonApplication.SERVER_PORT,
                    streamListener,
                    getInitializedSSLServerSocketFactory());
            connectionServer.start();
        }
    }

    public void stopConnectionServer() {
        if (connectionServer != null) {
            connectionServer.stop();
            connectionServer = null;
        }
    }

    private SSLServerSocketFactory getInitializedSSLServerSocketFactory() {
        SSLServerSocketFactory sslServerSocketFactory = null;
        try {
            // Load the keyStore that includes self-signed cert as a "trusted" entry.
            KeyStore keyStore = KeyStore.getInstance("BKS");
            InputStream keyStoreInputStream = getApplicationContext().getResources().openRawResource(R.raw.chameleon_keystore);
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
            log.error("Failed to initialize SSL server socket factory", e);
        }
        return sslServerSocketFactory;
    }

    public PreviewDisplayer getPreviewDisplayer() {
        synchronized (this) {
            if (previewDisplayer == null) {
                log.info("Waiting on preview displayer to be available");
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    log.error("Unable to wait on application", e);
                }
            }

            log.info("Returning preview displayer");
            return previewDisplayer;
        }
    }

    public void preparePreview() {
        if (!previewStarted) {
            log.info("Grabbing camera and starting preview");

            final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                ThreadWithHandler handlerThread = new ThreadWithHandler();
                String[] cameras = manager.getCameraIdList();
                log.info("Camera ids are {}, going to open first in list", cameras);
                manager.openCamera(cameras[0], new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        log.info("Found camera device callback {}", camera);


                        previewDisplayer = new Camera2PreviewDisplayer(ChameleonApplication.this, camera, manager);
                        previewDisplayer.setCameraFrameBuffer(cameraFrameBuffer);

                        synchronized (ChameleonApplication.this) {
                            log.info("Notifying chameleon waiters");
                            ChameleonApplication.this.notifyAll();
                        }
                    }

                    @Override
                    public void onClosed(CameraDevice camera) {
                        log.info("Camera {} is closed", camera);
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {
                        log.info("Camera is disconnected");
                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {
                        log.info("CameraDevice errored on open, {} err = {}", camera, error);
                        synchronized (ChameleonApplication.this) {
                            log.info("Notifying chameleon waiters even though we errored");
                            ChameleonApplication.this.notifyAll();
                        }
                    }
                }, handlerThread.getHandler());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            log.info("Preview already started");
        }
    }

    public void startPreview() {
        if (!previewStarted) {
            previewDisplayer.addOnPreparedCallback(new Runnable() {
                @Override
                public void run() {
                    previewDisplayer.startPreview();
                }
            });
            previewStarted = true;
        } else {
            log.info("Preview already started");
        }
    }

    public void stopPreview() {
        if (previewDisplayer != null) {
            previewDisplayer.stopPreview();
            previewDisplayer = null;
        }
        previewStarted = false;
    }

    public synchronized void updateOrientation() {
        log.info("Updating current device orientation");

        int orientation = getResources().getConfiguration().orientation;

        boolean isLandscape = getResources().getConfiguration().ORIENTATION_LANDSCAPE == orientation;
        log.info("Device is in {} mode", isLandscape ? "landscape" : "portrait");
        rotationState.setLandscape(isLandscape);
    }

    public void initializeWifiP2p() {

        if (wifiP2pManager == null) {
            log.debug("Acquiring WifiP2pManager");
            wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);

            log.debug("Acquiring WifiChannel");
            wifiP2pChannel = wifiP2pManager.initialize(this, getMainLooper(), null);
        }
    }

    /*
        Convenience method for creationg a preview display through the PreviewDisplayer
     */
    public SurfaceView createPreviewDisplay() {
        log.info("Creating preview display and stopping preview first");
        //previewDisplayer.stopPreview();
        previewStarted = false;
        return previewDisplayer.createPreviewDisplay();
    }

    public void startup() {
        log.info("Starting up application resources..");

        startConnectionServerIfNotRunning();

        //Code for phone
        // Commenting this out until we implement call handling logic. Need to register
        // in every onResume() and unregister in every onPause()

//        IntentFilter phoneStateChangedIntentFilter = new IntentFilter();
//        phoneStateChangedIntentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
//        incomingPhoneCallBroadcastReceiver = new IncomingPhoneCallBroadcastReceiver(this);
//        registerReceiver(incomingPhoneCallBroadcastReceiver, phoneStateChangedIntentFilter);
//        log.info("Registered IncomingPhoneCallBroadcastReceiver");

        // Reset session flags
        sessionStatus = SessionStatus.DISCONNECTED;
        streamListener.setStreamingStarted(false);

    }

    public void cleanupAndExit() {
        log.info("Tearing down application resources..");

        //  Tear down server
        if (connectionServer != null) {
            connectionServer.stop();
            connectionServer = null;
        }

        // Reset session flags
        sessionStatus = SessionStatus.DISCONNECTED;
        streamListener.setStreamingStarted(false);

        // Tear down Wifi hotspot
        tearDownWifiHotspot();

        // Tear down wifi if it was disabled before app was started
        tearDownWifiIfNecessary();

        // Tear down phone call receiver
        //unregisterReceiverSafely(incomingPhoneCallBroadcastReceiver);

        System.exit(0);
    }

    public void tearDownWifiHotspot() {

        log.debug("Tearing down Wifi components..");

        // Initializing wifi p2p so that we can tear down hotspot hanging around
        // from previous sessions
        initializeWifiP2p();

        // Tear down Wifi p2p hotspot
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            log.info("Invoking removeGroup..");
            wifiP2pManager.removeGroup(wifiP2pChannel, null);
        }
        wifiP2pManager = null;
        wifiP2pChannel = null;
    }

    private void tearDownWifiIfNecessary() {

        log.debug("Tearing down Wifi components..");

        tearDownWifiHotspot();

        // Tear down Wifi receivers
        unregisterReceiverSafely(enableWifiBroadcastReceiver);
        enableWifiBroadcastReceiver = null;

        // Put Wifi back in original state

        if (isWifiEnabledInitially != null) {
            log.info("Setting Wifi back to {}", isWifiEnabledInitially);
            final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
            wifiManager.setWifiEnabled(isWifiEnabledInitially);
            isWifiEnabledInitially = null;
        }
    }

    public String getOutputMediaDirectory() {
        return getMediaStorageDir().getPath();
    }

    /**
     * Create a File using given file name.
     *
     * @param filename
     * @return created file
     */
    public File getOutputMediaFile(final String filename) {
        return new File(getOutputMediaDirectory()+ File.separator + filename);
    }

    /**
     * Create a file for saving given media type.
     *
     * @param type
     * @return created file
     */
    public File getOutputMediaFile(final int type) {

        File mediaStorageDir = getMediaStorageDir();

        // Create a media file name
        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String timeStamp = String.valueOf(System.currentTimeMillis());
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
            log.info("File name is {}", mediaFile.getAbsolutePath());
        }
        return mediaFile;
    }

    private File getMediaStorageDir() {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        log.info("DCIM directory is: {}", Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM));

        if (!isExternalStorageWritable()) {
            log.error("External Storage is not mounted for Read-Write");
            return null;
        }

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), this.getString(R.string.app_name));
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                log.error("failed to create directory");
                return null;
            }
        }
        return mediaStorageDir;
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

    /**
     * Enable wifi and optionally perform some action when wifi enabled asynchronously.
     *
     * @param runnable (null if no action required when wifi enabled)
     */
    public void enableWifiAndPerformActionWhenEnabled(final Runnable runnable) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

        final long startTime = System.currentTimeMillis();

        final WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

        enableWifiBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log.info("onReceive intent = {}, wifi enabled = {}",
                        intent.getAction(), wifiManager.isWifiEnabled());

                if (wifiManager.isWifiEnabled()) {

                    //Publish time for wifi to be enabled
                    //metrics.sendTime(MetricNames.Category.WIFI.getName(),
                     //       MetricNames.Label.ENABLE.getName(), System.currentTimeMillis() - startTime);

                    // Done with checking Wifi state
                    unregisterReceiverSafely(this);
                    log.info("Wifi enabled!!");

                    // Perform action
                    if (runnable != null) {
                        runnable.run();
                    }

                }
            }
        };
        // register to listen for change in Wifi state
        registerReceiver(enableWifiBroadcastReceiver, filter);

        // Enable and wait for Wifi state change
        wifiManager.setWifiEnabled(true);
        log.info("SetWifiEnabled to true");
    }

    public void unregisterReceiverSafely(final BroadcastReceiver receiver) {
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                // ignoring this since this can happen due to some race conditions
            }
        }
    }

}
