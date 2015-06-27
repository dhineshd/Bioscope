/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder.Callback;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 
 * Don't use this class directly.
 */
public abstract class ChameleonVideoStream extends MediaStream {

	protected final static String TAG = ChameleonVideoStream.class.getSimpleName();

	protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected VideoQuality mQuality = mRequestedQuality.clone();
	protected Callback mSurfaceHolderCallback = null;
	protected SurfaceView mSurfaceView = null;
	protected SharedPreferences mSettings = null;
	protected int mVideoEncoder, mCameraId = 0;
	protected int mRequestedOrientation = 0, mOrientation = 0;
	protected Camera mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;

	protected boolean mCameraOpenedManually = true;
	protected boolean mFlashEnabled = false;
	protected boolean mSurfaceReady = false;
	protected boolean mUnlocked = false;
	protected boolean mPreviewStarted = false;
	protected boolean mUpdated = false;

	protected String mMimeType;
	protected String mEncoderName;
	protected int mEncoderColorFormat;
	protected int mCameraImageFormat;
	protected int mMaxFps = 0;

	/**
	 * Don't use this class directly.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public ChameleonVideoStream() {
		this(CameraInfo.CAMERA_FACING_BACK);
	}

	/**
	 * Don't use this class directly
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	@SuppressLint("InlinedApi")
	public ChameleonVideoStream(int camera) {
		super();
	}

	/**
	 * Returns the id of the camera currently selected. 
	 * Can be either {@link CameraInfo#CAMERA_FACING_BACK} or 
	 * {@link CameraInfo#CAMERA_FACING_FRONT}.
	 */
	public int getCamera() {
		return mCameraId;
	}

	/** Indicates whether or not the flash of the phone is on. */
	public boolean getFlashState() {
		return mFlashEnabled;
	}

	/** 
	 * Sets the orientation of the preview.
	 * @param orientation The orientation of the preview
	 */
	public void setPreviewOrientation(int orientation) {
		mRequestedOrientation = orientation;
		mUpdated = false;
	}
	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mRequestedQuality.equals(videoQuality)) {
			mRequestedQuality = videoQuality.clone();
			mUpdated = false;
		}
	}

	/** 
	 * Returns the quality of the stream.  
	 */
	public VideoQuality getVideoQuality() {
		return mRequestedQuality;
	}

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()} 
	 * to apply your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mOrientation = mRequestedOrientation;
	}	
	
	/**
	 * Starts the stream.
	 * This will also open the camera and display the preview 
	 * if {@link #startPreview()} has not already been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		super.start();
		Log.d(TAG,"Stream configuration: FPS: "+mQuality.framerate+" Width: "+mQuality.resX+" Height: "+mQuality.resY);
	}

	/** Stops the stream. */
	public synchronized void stop() {

	}

	/**
	 * Video encoding is done by a MediaCodec.
	 */
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {
		// Uses dequeueInputBuffer to feed the encoder
		//encodeWithMediaCodecMethod1();
	}	

	/**
	 * Video encoding is done by a MediaCodec.
	 */
	@SuppressLint("NewApi")
	protected void encodeWithMediaCodecMethod1() throws RuntimeException, IOException {

		Log.d(TAG,"Video encoded using the MediaCodec API with a buffer");

		EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
		final NV21Convertor convertor = debugger.getNV21Convertor();

		mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,debugger.getEncoderColorFormat());
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();

		Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			long now = System.nanoTime()/1000, oldnow = now, i=0;
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				oldnow = now;
				now = System.nanoTime()/1000;
				if (i++>3) {
					i = 0;
					//Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
				}
				try {
					int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
					if (bufferIndex>=0) {
						inputBuffers[bufferIndex].clear();
						if (data == null) Log.e(TAG,"Symptom of the \"Callback buffer was to small\" problem...");
						else convertor.convert(data, inputBuffers[bufferIndex]);
						mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
					} else {
						Log.e(TAG,"No buffer available !");
					}
				} finally {
					mCamera.addCallbackBuffer(data);
				}
			}
		};

		for (int i=0;i<10;i++) mCamera.addCallbackBuffer(new byte[convertor.getBufferSize()]);
		mCamera.setPreviewCallbackWithBuffer(callback);

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mPacketizer.start();

		mStreaming = true;

	}

	/**
	 * Returns a description of the stream using SDP. 
	 * This method can only be called after {@link Stream#configure()}.
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */	
	public abstract String getSessionDescription() throws IllegalStateException;


	/**
	 * Computes the average frame rate at which the preview callback is called.
	 * We will then use this average frame rate with the MediaCodec.  
	 * Blocks the thread in which this function is called.
	 */
	private void measureFramerate() {
		final Semaphore lock = new Semaphore(0);

		final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			int i = 0, t = 0;
			long now, oldnow, count = 0;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				i++;
				now = System.nanoTime()/1000;
				if (i>3) {
					t += now - oldnow;
					count++;
				}
				if (i>20) {
					mQuality.framerate = (int) (1000000/(t/count)+1);
					lock.release();
				}
				oldnow = now;
			}
		};

		mCamera.setPreviewCallback(callback);

		try {
			lock.tryAcquire(2,TimeUnit.SECONDS);
			Log.d(TAG,"Actual framerate: "+mQuality.framerate);
			if (mSettings != null) {
				Editor editor = mSettings.edit();
				editor.putInt(PREF_PREFIX+"fps"+mRequestedQuality.framerate+","+mCameraImageFormat+","+mRequestedQuality.resX+mRequestedQuality.resY, mQuality.framerate);
				editor.commit();
			}
		} catch (InterruptedException e) {}

		mCamera.setPreviewCallback(null);

	}	

}
