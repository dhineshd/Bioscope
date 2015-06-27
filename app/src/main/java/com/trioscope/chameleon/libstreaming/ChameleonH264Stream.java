package com.trioscope.chameleon.libstreaming;

import android.graphics.ImageFormat;
import android.media.MediaRecorder;
import android.util.Base64;

import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by dhinesh.dharman on 6/8/15.
 */
public class ChameleonH264Stream extends ChameleonVideoStream {
    public final static String TAG = "ChameleonH264Stream";

    private Semaphore mLock = new Semaphore(0);
    private MP4Config mConfig;

    /**
     * Constructs the H.264 stream using given config containing sps/pps info.
     */
    public ChameleonH264Stream(MP4Config mp4Config) {
        mConfig = mp4Config;
        mMimeType = "video/avc";
        mCameraImageFormat = ImageFormat.NV21;
        mVideoEncoder = MediaRecorder.VideoEncoder.H264;
        mPacketizer = new H264Packetizer();
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     */
    public synchronized String getSessionDescription() throws IllegalStateException {
        if (mConfig == null) throw new IllegalStateException("You need to call configure() first !");
        return "m=video "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id="+mConfig.getProfileLevel()+";sprop-parameter-sets="+mConfig.getB64SPS()+","+mConfig.getB64PPS()+";\r\n";
    }

    /**
     * Starts the stream.
     * This will also open the camera and display the preview if {@link #startPreview()} has not already been called.
     */
    public synchronized void start() throws IllegalStateException, IOException {
        if (!mStreaming) {
            configure();
            byte[] pps = Base64.decode(mConfig.getB64PPS(), Base64.NO_WRAP);
            byte[] sps = Base64.decode(mConfig.getB64SPS(), Base64.NO_WRAP);
            ((H264Packetizer)mPacketizer).setStreamParameters(pps, sps);
            super.start();
        }
    }

    @Override
    protected void encodeWithMediaRecorder() throws IOException {

    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()} to apply
     * your configuration of the stream.
     */
    public synchronized void configure(MP4Config mp4Config) throws IllegalStateException, IOException {
        super.configure();
        mMode = mRequestedMode;
        mQuality = mRequestedQuality.clone();
        mConfig = mp4Config;
    }
}
