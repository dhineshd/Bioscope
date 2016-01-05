package com.trioscope.chameleon;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Environment;
import android.util.Range;
import android.view.SurfaceView;

import com.trioscope.chameleon.broadcastreceiver.IncomingPhoneCallBroadcastReceiver;
import com.trioscope.chameleon.camera.CameraOpener;
import com.trioscope.chameleon.camera.CameraParams;
import com.trioscope.chameleon.camera.PreviewDisplayer;
import com.trioscope.chameleon.camera.impl.Camera2PreviewDisplayer;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.metrics.MetricsHelper;
import com.trioscope.chameleon.state.RotationState;
import com.trioscope.chameleon.stream.ConnectionServer;
import com.trioscope.chameleon.stream.ServerEventListenerManager;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;
import com.trioscope.chameleon.types.ThreadWithHandler;
import com.trioscope.chameleon.util.FileUtil;
import com.trioscope.chameleon.util.merge.FfmpegVideoMerger;
import com.trioscope.chameleon.util.merge.VideoMerger;
import com.trioscope.chameleon.util.security.SSLUtil;

import java.io.File;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by phand on 6/4/15.
 */
@Slf4j
public class ChameleonApplication extends Application {
    // Stream image buffer size is set to be the same as minimum socket buffer size
    // which ensures that each image can be transferred in a single send eliminating
    // the need to maintain sequence numbers when sending data. So, we
    // need to ensure that the compressed stream image size is less than this value.
    public static final int STREAM_IMAGE_BUFFER_SIZE_BYTES = 16 * 1024;
    public static final int SEND_RECEIVE_BUFFER_SIZE_BYTES = 64 * 1024;
    public static final int CERTIFICATE_BUFFER_SIZE = 3 * 1024;
    public static final Size DEFAULT_ASPECT_RATIO = new Size(16, 9);

    // Sizes beyond 1280 x 720 are not reliable on all devices (may change in future)
    private static final Size DEFAULT_CAMERA_FRAME_SIZE = new Size(1280, 720);
    private static final Size DEFAULT_LEGACY_CAMERA_FRAME_SIZE = new Size(768, 432);

    private static final long MAX_USER_INTERACTION_USER_LEAVING_DELAY_MS = 10;

    public static final String APP_REGULAR_FONT_LOCATION = "fonts/roboto-slab/RobotoSlab-Regular.ttf";
    public static final String APP_BOLD_FONT_LOCATION = "fonts/roboto-slab/RobotoSlab-Bold.ttf";

    public static final int SERVER_PORT = 7080;

    public static final String TUTORIAL_SHOWN_PREFERENCE_KEY = "TUTORIAL_SHOWN";

    public static final String TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP_KEY = "TUTORIAL_INVOKED_FROM_BEGINNING_OF_APP";

    public static final String KONOTOR_APP_ID = "4a550f3b-6391-4854-8f36-8aacaa2928c2";

    public static final String KONOTOR_APP_KEY = "76bad5cf-d107-4ad6-8f82-7f153b556b87";

    private static final Integer REASONABLE_FPS_BOUNDARY = 1000; // Above this FPS we assume the FPS not reported as frames per second, but as some other standard. This is used to detect older/lower quality camera2 implementations.


    @Getter
    private RotationState rotationState = new RotationState();

    @Getter
    private CameraOpener cameraOpener;

    @Getter
    private CameraInfo cameraInfo;

    @Getter
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;

    @Getter
    private PreviewDisplayer previewDisplayer;

    @Getter
    private WifiP2pManager wifiP2pManager;
    @Getter
    private WifiP2pManager.Channel wifiP2pChannel;

    @Getter
    private volatile ServerEventListenerManager serverEventListenerManager =
            new ServerEventListenerManager();

    @Setter
    private volatile ConnectionServer connectionServer;

    @Getter
    private static MetricsHelper metrics;

    @Getter
    private VideoMerger videoMerger = new FfmpegVideoMerger(this);

    // Receivers
    private BroadcastReceiver enableWifiBroadcastReceiver;
    private IncomingPhoneCallBroadcastReceiver incomingPhoneCallBroadcastReceiver;

    private Boolean isWifiEnabledInitially;

    private Executor offThreadExecutor = Executors.newSingleThreadExecutor();

    @Getter
    private KeyPair connectionServerKeyPair;

    @Override
    public void onCreate() {
        super.onCreate();
        log.info("Starting application");

        metrics = new MetricsHelper(this);

        isWifiEnabledInitially = ((WifiManager) getSystemService(Context.WIFI_SERVICE)).isWifiEnabled();

        log.info("Wifi initial enabled state = {}", isWifiEnabledInitially);

        // Enable Wifi to save time later and to avoid constantly turning on/off
        // which affects battery performance
        if (!isWifiEnabledInitially) {
            enableWifiAndPerformActionWhenEnabled(null);
        }

        // Add FPS listener to CameraBuffer
        cameraFrameBuffer.addListener(new UpdateRateListener());

        offThreadExecutor.execute(new DestroyPartialData(this));

        startup();
    }

    public X509Certificate stopAndStartConnectionServer(PublicKey trustedPublicKey) {
        log.info("Stopping and starting connection server");

        // Setup connection server to receive connections from client
        if (connectionServer != null) {
            connectionServer.stop();
            connectionServer = null;
        }
        // Generate new keypair and certificate
        connectionServerKeyPair = SSLUtil.createKeypair();
        X509Certificate certificate = SSLUtil.generateCertificate(connectionServerKeyPair);

        log.info("public key = {}; class {}", connectionServerKeyPair.getPublic(), connectionServerKeyPair.getPublic().getClass());

        connectionServer = new ConnectionServer(
                ChameleonApplication.SERVER_PORT,
                serverEventListenerManager,
                SSLUtil.createSSLServerSocketFactory(connectionServerKeyPair.getPrivate(), certificate));
        connectionServer.start();
        return certificate;
    }

    public X509Certificate stopAndStartConnectionServer() {
        return stopAndStartConnectionServer(null);
    }

    public void stopConnectionServer() {
        if (connectionServer != null) {
            connectionServer.stop();
            connectionServer = null;
        }
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

    public void startPreview(final CameraParams cameraParams) {
        if (!previewStarted) {
            previewDisplayer.addOnPreparedCallback(new Runnable() {
                @Override
                public void run() {
                    previewDisplayer.startPreview(cameraParams);
                }
            });
            previewStarted = true;
        } else {
            log.info("Preview already started");
        }
    }

    public void stopPreview() {
        log.info("stop preview invoked!");
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

        //Code for phone
        // Commenting this out until we implement call handling logic. Need to register
        // in every onResume() and unregister in every onPause()

//        IntentFilter phoneStateChangedIntentFilter = new IntentFilter();
//        phoneStateChangedIntentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
//        incomingPhoneCallBroadcastReceiver = new IncomingPhoneCallBroadcastReceiver(this);
//        registerReceiver(incomingPhoneCallBroadcastReceiver, phoneStateChangedIntentFilter);
//        log.info("Registered IncomingPhoneCallBroadcastReceiver");

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

    /**
     * Create a file for saving given media type.
     *
     * @param isTempLocation flag that determines whether this should be in a tmp location
     * @return created File
     */
    public File createVideoFile(final boolean isTempLocation) {

        File mediaStorageDir = isTempLocation ? FileUtil.getTempDirectory()
                : FileUtil.getOutputMediaDirectory();

        if (!isExternalStorageWritable()) {
            log.error("External Storage is not mounted for Read-Write");
            return null;
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "BIOSCOPE_" + timeStamp + ".mp4");

        // Remove existing file with same name (if any)
        if (mediaFile.exists()) {
            mediaFile.delete();
        }
        log.info("File name is {}", mediaFile.getAbsolutePath());
        return mediaFile;
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

    public static Size getDefaultCameraFrameSize() {
        log.info("Build.MODEL = {}", Build.MODEL);
        log.info("Build.MANUFACTURER = {}", Build.MANUFACTURER);
        return DEFAULT_CAMERA_FRAME_SIZE;
    }

    public static Size getDeviceSpecificCameraFrameSize(CameraCharacteristics cc) {
        log.info("Build.MODEL = {}", Build.MODEL);
        log.info("Build.MANUFACTURER = {}", Build.MANUFACTURER);

        Range<Integer>[] fpsRanges = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        log.info("Supported FPS ranges are {}", fpsRanges);

        if (fpsRanges.length > 0 && fpsRanges[0].getLower() > REASONABLE_FPS_BOUNDARY) {
            // This is an lower quality device, use a lower frame size so we keep the frame rate high
            log.info("Determined camera2 is lower quality, returning {}", DEFAULT_LEGACY_CAMERA_FRAME_SIZE);
            return DEFAULT_LEGACY_CAMERA_FRAME_SIZE;
        }

        return DEFAULT_CAMERA_FRAME_SIZE;
    }

    public static boolean isUserLeavingOnLeaveHintTriggered(final long latestUserInteractionTimeMillis) {
        return (Math.abs(System.currentTimeMillis() - latestUserInteractionTimeMillis) <
                MAX_USER_INTERACTION_USER_LEAVING_DELAY_MS);
    }
}
