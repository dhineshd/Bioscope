package com.trioscope.chameleon;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.ImageView;

import com.trioscope.chameleon.camera.VideoRecorder;
import com.trioscope.chameleon.libstreaming.ChameleonRtspServer;
import com.trioscope.chameleon.libstreaming.ChameleonSession;
import com.trioscope.chameleon.libstreaming.ChameleonSessionBuilder;
import com.trioscope.chameleon.libstreaming.H264StreamTest;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.listener.VideoStreamFrameListener;
import com.trioscope.chameleon.listener.impl.UpdateRateListener;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;

import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import fi.iki.elonen.HttpStreamingServer;
import lombok.Getter;
import lombok.Setter;

/**
 * Created by phand on 6/4/15.
 */
public class ChameleonApplication extends Application {
    private final static Logger LOG = LoggerFactory.getLogger(ChameleonApplication.class);

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
    private CameraFrameBuffer cameraFrameBuffer = new CameraFrameBuffer();

    private boolean previewStarted = false;

    private MediaCodec mediaCodec;
    private EncoderDebugger encoderDebugger;
    private VideoQuality streamQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
    private ChameleonSession mSession;
    private ChameleonRtspServer rtspServer;
    private SurfaceView mSurfaceView;
    private ParcelFileDescriptor writeParcelFd;
    private HttpStreamingServer httpStreamingServer;

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

        // Add listener to stream camera frames as video
        configureVideoStreamComponents();
        try {
            ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
            httpStreamingServer = new HttpStreamingServer(fds[0]);
            writeParcelFd = fds[1];
            httpStreamingServer.start();
            cameraFrameBuffer.addListener(
                    new VideoStreamFrameListener(
                            mediaCodec,
                            encoderDebugger,
                            fds[1]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void configureVideoStreamComponents() {
        SharedPreferences mSettings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        //streamQuality.resY = 10;
        //streamQuality.resX = 10;
        encoderDebugger = EncoderDebugger.debug(mSettings, streamQuality.resX, streamQuality.resY);
        try {
            mediaCodec = MediaCodec.createByCodecName(encoderDebugger.getEncoderName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", streamQuality.resX, streamQuality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, streamQuality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, streamQuality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, encoderDebugger.getEncoderColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();
        LOG.info("Configured MediaCodec for streaming..");

        //mSurfaceView = (net.majorkernelpanic.streaming.gl.SurfaceView) view.findViewById(R.id.surface);

        H264StreamTest h264Test = new H264StreamTest(0);
        //h264Test.setSurfaceView(mSurfaceView);
        h264Test.setPreferences(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        MP4Config mp4Config = h264Test.getConfig();

        mSession =
                ChameleonSessionBuilder.getInstance()
                        .setPreviewOrientation(0)
                        .setContext(getApplicationContext())
                        .setAudioEncoder(ChameleonSessionBuilder.AUDIO_NONE)
                        .setVideoEncoder(ChameleonSessionBuilder.VIDEO_H264)
                        .setVideoConfig(mp4Config)
                        .build();

        rtspServer = new ChameleonRtspServer(mSession);
        rtspServer.start();

    }

    @Override
    public void onTerminate() {
        rtspServer.stop();
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
                LOG.info("Camera params " + camera.getParameters().getPreviewSize().width +
                        " x " + camera.getParameters().getPreviewSize().height);
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
}
