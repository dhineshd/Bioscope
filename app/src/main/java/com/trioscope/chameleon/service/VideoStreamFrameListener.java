package com.trioscope.chameleon.service;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.util.Log;

import net.majorkernelpanic.streaming.hw.EncoderDebugger;

import java.nio.ByteBuffer;

import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 6/4/15.
 */
public class VideoStreamFrameListener implements FrameListener {
    private static final String TAG = VideoStreamFrameListener.class.getSimpleName();

    @NonNull
    private final MediaCodec mMediaCodec;
    @NonNull
    private final EncoderDebugger mEncoderDebugger;

    private long now = System.nanoTime()/1000, oldnow = now, i=0;

    public VideoStreamFrameListener(
            final MediaCodec mediaCodec,
            final EncoderDebugger encoderDebugger){
        mMediaCodec = mediaCodec;
        mEncoderDebugger = encoderDebugger;//EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
    }

    @Override
    public void frameAvailable() {
    }

    @Override
    public void onFrameReceived(byte[] data, Camera.Parameters cameraParams) {
        oldnow = now;
        now = System.nanoTime()/1000;
        if (i++>3) {
            i = 0;
            //Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
        }
        int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
        if (bufferIndex>=0) {
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            inputBuffers[bufferIndex].clear();
            if (data == null) Log.e(TAG, "Symptom of the \"Callback buffer was to small\" problem...");
            else mEncoderDebugger.getNV21Convertor().convert(data, inputBuffers[bufferIndex]);
            mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
        } else {
            Log.e(TAG, "No buffer available !");
        }
    }
}
