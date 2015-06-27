package com.trioscope.chameleon.libstreaming;

import android.graphics.ImageFormat;
import android.util.Log;

import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.video.VideoStream;

/**
 * Created by dhinesh.dharman on 6/8/15.
 */
public class H264StreamTest extends VideoStream {
    private static final String TAG = "H264StreamTest";
    public H264StreamTest(int cameraId){
        super(cameraId);
        mCameraImageFormat = ImageFormat.NV21;
    }

    @Override
    public String getSessionDescription() throws IllegalStateException {
        return null;
    }

    public MP4Config getConfig() {
        //createCamera();
        //updateCamera();
        Log.d(TAG, "X = " + mQuality.resX + " Y = " + mQuality.resY);
        EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
        //destroyCamera();
        return new MP4Config(debugger.getB64SPS(), debugger.getB64PPS());
    }
}
