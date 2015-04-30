package com.trioscope.chameleon;

import android.content.Intent;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.trioscope.chameleon.camera.CameraPreview;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.view.View.OnClickListener;


public class MainActivity extends ActionBarActivity {

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    public static final int MEDIA_TYPE_AUDIO = 3;
    private static final String TAG = "MainActivity";
    private Camera camera;
    private CameraPreview cameraPreview;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private File videoFile;

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


        Log.d(TAG, String.valueOf(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM)));

        if (!isExternalStorageWritable()) {
            Log.e(TAG, "External Storage is not mounted for Read-Write");
            return null;
        }


        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), this.getString(R.string.app_name));
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "failed to create directory");
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
            Log.d(TAG, "File name is : " + mediaFile.getAbsolutePath());
        }
        return mediaFile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "Creating a new Activity");

        // create an instance of the camera
        camera = getCameraInstance();

        // create preview view and set it to the UI layout
        cameraPreview = new CameraPreview(this, camera);

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(cameraPreview);


        final Button button = (Button) findViewById(R.id.capture);

        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

                if (isRecording) {

                    finishVideoRecording();

                    isRecording = false;
                    Log.d(TAG, "isRecording is:" + isRecording);
                    button.setText("Record!");
                } else {
                    // initialize video camera
                    if (prepareVideoRecorder()) {
                        mediaRecorder.start();
                        button.setText("Done!");
                        isRecording = true;
                        Log.d(TAG, "isRecording is" + isRecording);
                    } else {
                        // prepare didn't work, release the camera
                        releaseMediaRecorder();
                        // inform user
                        Toast.makeText(getApplicationContext(), "Could Not Record Video :(", Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Failed to initialize media recorder");
                    }
                }
            }
        });
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

        releaseMediaRecorder();

        if (camera != null) {
            camera.release();
            camera = null;
        }

        if(videoFile != null) {
            videoFile = null;
        }

        super.onPause();
    }

    /**
     * A safe way to get an instance of the Camera object.
     */
    private Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance

            if (c != null) {
                Log.d(TAG, "Successfully opened camera");
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not open camera: ", e);
        }
        return c; // returns null if camera is unavailable
    }

    private boolean prepareVideoRecorder() {

        //Create a file for storing the recorded video
        videoFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);

        mediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        camera.unlock();
        mediaRecorder.setCamera(camera);

        // Step 2: Set sources
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        // Step 4: Set output file
        mediaRecorder.setOutputFile(videoFile.getPath());

        Log.d(TAG, getOutputMediaFile(MEDIA_TYPE_VIDEO).getPath());

        // Step 5: Prepare configured MediaRecorder
        try {
            mediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.e(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: ", e);
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    private void finishVideoRecording() {
        mediaRecorder.stop();
        releaseMediaRecorder();
        camera.lock();         // take camera access back from MediaRecorder


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

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = null;
            camera.lock();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig){

        Log.d(TAG, "Configuration Changed");

        // Checks the orientation of the screen
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }

        super.onConfigurationChanged(newConfig);
    }
}
