package com.trioscope.chameleon;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.trioscope.chameleon.camera.BackgroundRecorder;
import com.trioscope.chameleon.camera.ForwardedCameraPreview;
import com.trioscope.chameleon.listener.CameraPreviewTextureListener;
import com.trioscope.chameleon.camera.stream.RtspVideoStreamer;
import com.trioscope.chameleon.camera.stream.VideoStreamer;
import com.trioscope.chameleon.service.ThreadLoggingHandler;
import com.trioscope.chameleon.types.EGLContextAvailableMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import static android.view.View.OnClickListener;

public class MainActivity extends ActionBarActivity {
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_AUDIO = 3;
    private static final Logger LOG = LoggerFactory.getLogger(MainActivity.class);
    //private Camera camera;
    //private CameraPreview cameraPreview;
    private boolean isRecording = false;
    private File videoFile;
    private BackgroundRecorder videoRecorder;
    private ForwardedCameraPreview cameraPreview;
    private VideoStreamer videoStreamer;

    public ThreadLoggingHandler logHandler;
    public MainThreadHandler mainThreadHandler;
    private SurfaceTextureDisplay previewDisplay;

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
     * Create a File for saving an image or video
     */
    private File getOutputMediaFile(int type) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainThreadHandler = new MainThreadHandler(Looper.getMainLooper());

        setContentView(R.layout.activity_main);

        RelativeLayout mainLayout = (RelativeLayout) findViewById(R.id.main_layout);
        LOG.info("Found main layout {}", mainLayout);
        LOG.info("Layout has {} children", mainLayout.getChildCount());

        LOG.info("Created main activity");
        videoRecorder = createBackgroundRecorder();
        //videoStreamer = new RtspVideoStreamer();

        LOG.info("Current thread is {}", Thread.currentThread());

        // create an instance of the camera
        //camera = getCameraInstance();

        LOG.info("Adding camera preview to list of preview surfaces");
        CameraPreviewTextureListener frameListener = new CameraPreviewTextureListener();
        videoRecorder.setFrameListener(frameListener);

        final Button button = (Button) findViewById(R.id.capture);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                LOG.info("Capture video button clicked");
                if (isRecording) {

                    finishVideoRecording();

                    isRecording = false;
                    LOG.info("isRecording is {}", isRecording);
                    button.setText("Record!");
                } else {
                    // initialize video camera
                    /*if (prepareVideoRecorder()) {
                        videoRecorder.startRecording();
                        button.setText("Done!");
                        isRecording = true;
                        LOG.info("isRecording is {}", isRecording);
                    } else {
                        // inform user
                        Toast.makeText(getApplicationContext(), "Could Not Record Video :(", Toast.LENGTH_LONG).show();
                        LOG.error("Failed to initialize media recorder");
                    }*/
                }
            }
        });

        // Tell the application we're ready to show preview whenever
        ChameleonApplication application = (ChameleonApplication) getApplication();
        application.setEglContextCallback(this);
    }

    private BackgroundRecorder createBackgroundRecorder() {
        BackgroundRecorder recorder = new BackgroundRecorder(this);
        recorder.setMainThreadHandler(mainThreadHandler);

        return recorder;
    }

    public void startCameraPreview(SurfaceTexture texture) {
        videoRecorder.setOutputFile(getOutputMediaFile(MEDIA_TYPE_VIDEO));
        videoRecorder.startRecording();
        videoStreamer.startStreaming();
        LOG.info("Starting camera preview");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        LOG.info("onPause: App is no longer in foreground");
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        LOG.info("onDestroy: App is no longer used by user");
        videoRecorder.stopRecording();
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        LOG.info("onStop: App is no longer visible to user");
        super.onStop();
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance

            if (c != null) {
                LOG.info("Successfully opened camera");
            }

        } catch (Exception e) {
            LOG.error("Could not open camera: ", e);
        }
        return c; // returns null if camera is unavailable
    }

    private boolean prepareVideoRecorder() {
        //Create a file for storing the recorded video
        videoFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);
        videoRecorder.setOutputFile(videoFile);
        videoRecorder.setCameraPreview(cameraPreview);
        return true;
    }

    private void finishVideoRecording() {
        videoRecorder.stopRecording();
        //camera.lock();         // take camera access back from video recorder

        if (videoFile != null) {
            //Send a broadcast about the newly added video file for Gallery Apps to recognize the video
            Intent addVideoIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            addVideoIntent.setData(Uri.fromFile(videoFile));

            sendBroadcast(addVideoIntent);
        }

        //Video file is successfully saved and a broadcast has been sent to add it to the Gallery Apps
        // We can now remove reference to it
        videoFile = null;
    }


    private void createSurfaceTextureWithSharedEglContext(final EGLContextAvailableMessage contextMessage) {
        if (previewDisplay != null) {
            LOG.info("Destroying previous previewDisplay first");
            ((ViewGroup) previewDisplay.getParent()).removeView(previewDisplay);
        }

        LOG.info("Creating surface texture with shared EGL Context on thread {}", Thread.currentThread());

        previewDisplay = new SurfaceTextureDisplay(this);
        previewDisplay.setEGLContextFactory(new GLSurfaceView.EGLContextFactory() {
            @Override
            public javax.microedition.khronos.egl.EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
                LOG.info("Creating shared EGLContext");
                EGLConfig config = getConfig(FLAG_RECORDABLE, 2, display);
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };

                EGLContext newContext = ((EGL10) EGLContext.getEGL()).eglCreateContext(display, eglConfig, contextMessage.getEglContext(), attrib2_list);

                LOG.info("Created a shared EGL context {}", newContext);
                return newContext;
            }

            @Override
            public void destroyContext(EGL10 egl, EGLDisplay display, javax.microedition.khronos.egl.EGLContext context) {

            }
        });

        previewDisplay.setTextureId(contextMessage.getGlTextureId());
        previewDisplay.setToDisplay(contextMessage.getSurfaceTexture());
        previewDisplay.setRenderer(previewDisplay.new SurfaceTextureRenderer());
        previewDisplay.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        ((ChameleonApplication) getApplication()).getCameraPreviewFrameListener().addFrameListener(new RenderRequestFrameListener(previewDisplay));

        RelativeLayout layout = (RelativeLayout) findViewById(R.id.main_layout);
        layout.addView(previewDisplay);
        LOG.info("Added child - now has {} children", layout.getChildCount());
        previewDisplay.requestRender();
    }

    // See https://github.com/google/grafika/blob/master/src/com/android/grafika/gles/EglCore.java
    // Android-specific extension.
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    /**
     * Constructor flag: surface must be recordable.  This discourages EGL from using a
     * pixel format that cannot be converted efficiently to something usable by the video
     * encoder.
     */
    public static final int FLAG_RECORDABLE = 0x01;


    private EGLConfig getConfig(int flags, int version, EGLDisplay mEGLDisplay) {
        int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
        if (version >= 3) {
            renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
        }

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                //EGL14.EGL_DEPTH_SIZE, 16,
                //EGL14.EGL_STENCIL_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, renderableType,
                EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                EGL14.EGL_NONE
        };
        if ((flags & FLAG_RECORDABLE) != 0) {
            attribList[attribList.length - 3] = EGL_RECORDABLE_ANDROID;
            attribList[attribList.length - 2] = 1;
        }
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!((EGL10) EGLContext.getEGL()).eglChooseConfig(mEGLDisplay, attribList, configs, 0,
                numConfigs)) {
            LOG.warn("unable to find RGB8888 / " + version + " EGLConfig");
            return null;
        }
        return configs[0];
    }

    public void eglContextAvailable(EGLContextAvailableMessage eglContextMsg) {
        LOG.info("EGLContext is now available, going to display preview, thread {}", Thread.currentThread());
        createSurfaceTextureWithSharedEglContext(eglContextMsg);
    }

    public class MainThreadHandler extends Handler {
        public static final int EGL_CONTEXT_AVAILABLE = 1;

        public MainThreadHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EGL_CONTEXT_AVAILABLE:
                    LOG.info("EGL Context is available, parameters {}", msg.obj);
                    createSurfaceTextureWithSharedEglContext((EGLContextAvailableMessage) msg.obj);
                    break;
                default:
                    super.handleMessage(msg);
            }
            super.handleMessage(msg);
        }
    }
}
