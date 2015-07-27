package com.trioscope.chameleon.stream;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;

import java.io.IOException;
import java.nio.ByteBuffer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 7/25/15.
 */
@Slf4j
public class VideoRecordingFrameListener implements CameraFrameAvailableListener, RecordingEventListener{
    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    private volatile boolean isRecording;

    private volatile MediaCodec mediaCodec;

    public VideoRecordingFrameListener(ChameleonApplication chameleonApplication) {
        this.chameleonApplication = chameleonApplication;
    }

    private void setupMediaCodec() {
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
            for (int colorFormat : mediaCodec.getCodecInfo().getCapabilitiesForType("video/avc").colorFormats){
                log.info("Supported color format = {}", colorFormat);
            }
//            AssetFileDescriptor sampleFD = chameleonApplication.getResources().openRawResourceFd(R.raw.test_video);
//            MediaExtractor mediaExtractor = new MediaExtractor();
//            mediaExtractor.setDataSource(sampleFD.getFileDescriptor(), sampleFD.getStartOffset(), sampleFD.getLength());

            // log.info("MediaExtractor track count = {}", mediaExtractor.getTrackCount());
            MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc",
                    960,
                    540);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mediaCodec.start();
        } catch (IOException e) {
            log.error("Failed to create encoder", e);
        }

    }


    @Override
    public void onFrameAvailable(CameraInfo cameraInfo, int[] data) {
        if (isRecording) {
            //processFrame(data);
        }
    }

    private void processFrame(final int data[]) {
        long currentTimeMicros = System.nanoTime() / 1000;
        log.info("Recording started.. process frames");

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(500000);
        if (inputBufferIndex >= 0) {
            // if API level >= 21, get input buffer here
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            log.info("bytebuffer size = {}, frame size = {}", inputBuffer.capacity(), data.length * 4);
            // fill inputBuffers[inputBufferIndex] with valid data
            //inputBuffer = ByteBuffer.allocateDirect(data.length * 4);
            inputBuffer.asIntBuffer().put(data);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), currentTimeMicros, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 500000);
        if (outputBufferIndex >= 0) {
            // if API level >= 21, get output buffer here
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            log.info("Encoded frame available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
        }
    }

    @Override
    public void onStartRecording(long recordingStartTimeMillis) {
        setupMediaCodec();
        isRecording = true;
    }

    @Override
    public void onStopRecording() {
        isRecording = false;
        mediaCodec.stop();
    }
}
