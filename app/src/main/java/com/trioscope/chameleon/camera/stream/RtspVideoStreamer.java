package com.trioscope.chameleon.camera.stream;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.preference.PreferenceManager;
import android.util.Log;

import com.trioscope.chameleon.network.RtspClientConnection;
import com.trioscope.chameleon.service.FrameListener;

import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by dhinesh.dharman on 6/4/15.
 */
public class RtspVideoStreamer implements FrameListener{
    private static final String TAG = RtspVideoStreamer.class.getSimpleName();
    private MediaCodec mMediaCodec;
    private AbstractPacketizer mPacketizer;
    private EncoderDebugger mEncoderDebugger;
    private boolean isStreaming;
    private long now = System.nanoTime()/1000, oldnow = now, i=0;

    public RtspVideoStreamer(
            final VideoQuality streamQuality,
            final Context applicationContext,
            final AbstractPacketizer packetizer){
        mPacketizer = packetizer;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(applicationContext);
        mEncoderDebugger = EncoderDebugger.debug(settings, streamQuality.resX, streamQuality.resY);
        try {
            mMediaCodec = MediaCodec.createByCodecName(mEncoderDebugger.getEncoderName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", streamQuality.resX, streamQuality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, streamQuality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, streamQuality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mEncoderDebugger.getEncoderColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
    }

    public void start(RtspClientConnection connection) {
        // The packetizer encapsulates the bit stream in an RTP stream and sends it over the network
        mPacketizer.setDestination(connection.getAddress(), connection.getRtpPort(), connection.getRtcpPort());
        mPacketizer.start();
        isStreaming = true;
    }

    public void stop() {
        mPacketizer.stop();
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    @Override
    public void frameAvailable() {

    }

    @Override
    public void onFrameReceived(byte[] data, Camera.Parameters cameraParams) {
        if (isStreaming) {
            oldnow = now;
            now = System.nanoTime()/1000;
            if (i++>3) {
                i = 0;
                //Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
            }
            int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
            if (bufferIndex >= 0) {
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
}
