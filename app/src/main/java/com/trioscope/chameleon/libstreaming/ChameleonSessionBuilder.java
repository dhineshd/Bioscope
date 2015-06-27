/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.trioscope.chameleon.libstreaming;

import android.content.Context;
import android.hardware.Camera.CameraInfo;
import android.preference.PreferenceManager;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.audio.AACStream;
import net.majorkernelpanic.streaming.audio.AMRNBStream;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.audio.AudioStream;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public class ChameleonSessionBuilder {

	public final static String TAG = "SessionBuilder";

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_NONE = 0;

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_H264 = 1;

	/** Can be used with {@link #setVideoEncoder}. */
	public final static int VIDEO_H263 = 2;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_NONE = 0;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_AMRNB = 3;

	/** Can be used with {@link #setAudioEncoder}. */
	public final static int AUDIO_AAC = 5;

	// Default configuration
	private VideoQuality mVideoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
	private AudioQuality mAudioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;
	private Context mContext;
	private int mVideoEncoder = VIDEO_H263;
	private int mAudioEncoder = AUDIO_AMRNB;
	private int mCamera = CameraInfo.CAMERA_FACING_BACK;
	private MP4Config mVideoConfig;
	private int mTimeToLive = 64;
	private int mOrientation = 0;
	private boolean mFlash = false;
	private SurfaceView mSurfaceView = null;
	private String mOrigin = null;
	private String mDestination = null;
	private ChameleonSession.Callback mCallback = null;

	// Removes the default public constructor
	private ChameleonSessionBuilder() {}

	// The SessionManager implements the singleton pattern
	private static volatile ChameleonSessionBuilder sInstance = null;

	/**
	 * Returns a reference to the {@link ChameleonSessionBuilder}.
	 * @return The reference to the {@link ChameleonSessionBuilder}
	 */
	public final static ChameleonSessionBuilder getInstance() {
		if (sInstance == null) {
			synchronized (ChameleonSessionBuilder.class) {
				if (sInstance == null) {
					ChameleonSessionBuilder.sInstance = new ChameleonSessionBuilder();
				}
			}
		}
		return sInstance;
	}	

	/**
	 * Creates a new {@link ChameleonSession}.
	 * @return The new Session
	 * @throws IOException 
	 */
	public ChameleonSession build() {
		ChameleonSession session;

		session = new ChameleonSession();
		session.setOrigin(mOrigin);
		session.setDestination(mDestination);
		session.setTimeToLive(mTimeToLive);
		session.setCallback(mCallback);

		switch (mAudioEncoder) {
		case AUDIO_AAC:
			AACStream stream = new AACStream();
			session.addAudioTrack(stream);
			if (mContext!=null) 
				stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(mContext));
			break;
		case AUDIO_AMRNB:
			session.addAudioTrack(new AMRNBStream());
			break;
		}

		switch (mVideoEncoder) {
		case VIDEO_H264:
			ChameleonH264Stream stream = new ChameleonH264Stream(mVideoConfig);
			if (mContext!=null) 
				stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(mContext));
			session.addVideoTrack(stream);
			break;
		}

		if (session.getVideoTrack()!=null) {
			ChameleonVideoStream video = session.getVideoTrack();
			video.setStreamingMethod(MediaStream.MODE_MEDIACODEC_API);
			video.setVideoQuality(mVideoQuality);
			video.setPreviewOrientation(mOrientation);
			video.setDestinationPorts(5006);
		}

		if (session.getAudioTrack()!=null) {
			AudioStream audio = session.getAudioTrack();
			audio.setAudioQuality(mAudioQuality);
			audio.setDestinationPorts(5004);
		}

		return session;

	}

	/** 
	 * Access to the context is needed for the H264Stream class to store some stuff in the SharedPreferences.
	 * Note that you should pass the Application context, not the context of an Activity.
	 **/
	public ChameleonSessionBuilder setContext(Context context) {
		mContext = context;
		return this;
	}

	/** Sets the destination of the session. */
	public ChameleonSessionBuilder setDestination(String destination) {
		mDestination = destination;
		return this; 
	}

	/** Sets the origin of the session. It appears in the SDP of the session. */
	public ChameleonSessionBuilder setOrigin(String origin) {
		mOrigin = origin;
		return this;
	}

	/** Sets the video stream quality. */
	public ChameleonSessionBuilder setVideoQuality(VideoQuality quality) {
		mVideoQuality = quality.clone();
		return this;
	}
	
	/** Sets the audio encoder. */
	public ChameleonSessionBuilder setAudioEncoder(int encoder) {
		mAudioEncoder = encoder;
		return this;
	}
	
	/** Sets the audio quality. */
	public ChameleonSessionBuilder setAudioQuality(AudioQuality quality) {
		mAudioQuality = quality.clone();
		return this;
	}

	/** Sets the default video encoder. */
	public ChameleonSessionBuilder setVideoEncoder(int encoder) {
		mVideoEncoder = encoder;
		return this;
	}

	public ChameleonSessionBuilder setFlashEnabled(boolean enabled) {
		mFlash = enabled;
		return this;
	}

	public ChameleonSessionBuilder setCamera(int camera) {
		mCamera = camera;
		return this;
	}

	public ChameleonSessionBuilder setTimeToLive(int ttl) {
		mTimeToLive = ttl;
		return this;
	}

	/** 
	 * Sets the SurfaceView required to preview the video stream. 
	 **/
	public ChameleonSessionBuilder setSurfaceView(SurfaceView surfaceView) {
		mSurfaceView = surfaceView;
		return this;
	}
	
	/** 
	 * Sets the orientation of the preview.
	 * @param orientation The orientation of the preview
	 */
	public ChameleonSessionBuilder setPreviewOrientation(int orientation) {
		mOrientation = orientation;
		return this;
	}	
	
	public ChameleonSessionBuilder setCallback(ChameleonSession.Callback callback) {
		mCallback = callback;
		return this;
	}

    public ChameleonSessionBuilder setVideoConfig(MP4Config videoConfig) {
        mVideoConfig = videoConfig;
        return this;
    }
	
	/** Returns the context set with {@link #setContext(Context)}*/
	public Context getContext() {
		return mContext;	
	}

	/** Returns the destination ip address set with {@link #setDestination(String)}. */
	public String getDestination() {
		return mDestination;
	}

	/** Returns the origin ip address set with {@link #setOrigin(String)}. */
	public String getOrigin() {
		return mOrigin;
	}

	/** Returns the audio encoder set with {@link #setAudioEncoder(int)}. */
	public int getAudioEncoder() {
		return mAudioEncoder;
	}

	/** Returns the id of the {@link android.hardware.Camera} set with {@link #setCamera(int)}. */
	public int getCamera() {
		return mCamera;
	}

	/** Returns the video encoder set with {@link #setVideoEncoder(int)}. */
	public int getVideoEncoder() {
		return mVideoEncoder;
	}

	/** Returns the VideoQuality set with {@link #setVideoQuality(VideoQuality)}. */
	public VideoQuality getVideoQuality() {
		return mVideoQuality;
	}
	
	/** Returns the AudioQuality set with {@link #setAudioQuality(AudioQuality)}. */
	public AudioQuality getAudioQuality() {
		return mAudioQuality;
	}

	/** Returns the flash state set with {@link #setFlashEnabled(boolean)}. */
	public boolean getFlashState() {
		return mFlash;
	}

	/** Returns the SurfaceView set with {@link #setSurfaceView(SurfaceView)}. */
	public SurfaceView getSurfaceView() {
		return mSurfaceView;
	}
	
	
	/** Returns the time to live set with {@link #setTimeToLive(int)}. */
	public int getTimeToLive() {
		return mTimeToLive;
	}

	/** Returns a new {@link ChameleonSessionBuilder} with the same configuration. */
	public ChameleonSessionBuilder clone() {
		return new ChameleonSessionBuilder()
		.setDestination(mDestination)
		.setOrigin(mOrigin)
		.setSurfaceView(mSurfaceView)
		.setPreviewOrientation(mOrientation)
		.setVideoQuality(mVideoQuality)
		.setVideoEncoder(mVideoEncoder)
		.setFlashEnabled(mFlash)
		.setCamera(mCamera)
		.setTimeToLive(mTimeToLive)
		.setAudioEncoder(mAudioEncoder)
		.setAudioQuality(mAudioQuality)
		.setContext(mContext)
		.setCallback(mCallback);
	}

}