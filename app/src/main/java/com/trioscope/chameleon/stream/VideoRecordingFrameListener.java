package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.types.CameraInfo;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 7/25/15.
 */
@Slf4j
public class VideoRecordingFrameListener implements CameraFrameAvailableListener, RecordingEventListener {
    private static final long TIMEOUT_MICROSECONDS = 1000;
    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    private volatile boolean isRecording;

    private volatile MediaCodec mediaCodec;
    private volatile MediaMuxer mediaMuxer;
    private volatile boolean muxerStarted;
    private int mTrackIndex = -1;

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
                    480,
                    270);
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

        // Setup MediaMuxer to save MediaCodec output to file
        try {
            File outputFile = chameleonApplication.getOutputMediaFile("muxer.mp4");
            if (outputFile.exists()) {
                outputFile.delete();
            }
            chameleonApplication.setSecondaryVideoFile(outputFile);
            mediaMuxer = new MediaMuxer(
                    chameleonApplication.getSecondaryVideoFile().getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            log.error("MediaMuxer creation failed", e);
        }
    }


    @Override
    public void onFrameAvailable(CameraInfo cameraInfo, int[] data) {
        if (isRecording) {
            int width = cameraInfo.getCaptureResolution().getWidth();
            int height = cameraInfo.getCaptureResolution().getHeight();
            byte [] yuv = new byte[width * height * 3/2];
            int[] argb = new int[width * height];
            convertToBmp(data, width, height).getPixels(argb, 0, width, 0, 0, width, height);
            //encodeYUV420SP(yuv, argb, width, height);
            //processFrame(argb);
        }
    }

    private Bitmap convertToBmp(final int[] pixelsBuffer, final int width, final int height) {
        int screenshotSize = width * height;
        for (int i = 0; i < screenshotSize; ++i) {
            // The alpha and green channels' positions are preserved while the red and blue are swapped
            // since the received frame is in RGB but Bitmap expects BGR. May need to revisit the
            // efficiency of this approach and see if we can get the frame directly in BGR.
            // Refer: https://www.khronos.org/registry/gles/extensions/EXT/EXT_read_format_bgra.txt
            pixelsBuffer[i] =
                    ((pixelsBuffer[i] & 0xff00ff00))
                            | ((pixelsBuffer[i] & 0x000000ff) << 16)
                            | ((pixelsBuffer[i] & 0x00ff0000) >> 16);
        }
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bmp.setPixels(pixelsBuffer, screenshotSize - width, -width, 0, 0, width, height);
        return bmp;
    }

    private void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;
        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
                U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
                V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte)((V<0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte)((U<0) ? 0 : ((U > 255) ? 255 : U));
                }

                index ++;
            }
        }
    }

    private void processFrame(final byte data[]) {
        long currentTimeMicros = System.nanoTime() / 1000;
        log.info("Recording started.. process frames");

        int inputBufferIndex = mediaCodec.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (inputBufferIndex >= 0) {
            // if API level >= 21, get input buffer here
            ByteBuffer inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex);
            inputBuffer.clear();
            log.info("bytebuffer size = {}, frame size = {}", inputBuffer.capacity(), data.length * 4);
            // fill inputBuffers[inputBufferIndex] with valid data
            //inputBuffer = ByteBuffer.allocateDirect(data.length * 4);
            inputBuffer.put(data);
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputBuffer.limit(), currentTimeMicros, 0);
        }

        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_MICROSECONDS);
        if (outputBufferIndex >= 0) {
            // if API level >= 21, get output buffer here
            ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
            log.info("Encoded frame available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.

            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                log.info("ignoring BUFFER_FLAG_CODEC_CONFIG");
                bufferInfo.size = 0;
            }

            if (bufferInfo.size != 0) {
                if (!muxerStarted) {
                    throw new RuntimeException("muxer hasn't started");
                }

                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outputBuffer.position(bufferInfo.offset);
                outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                mediaMuxer.writeSampleData(mTrackIndex, outputBuffer, bufferInfo);
                log.info("sent " + bufferInfo.size + " bytes to muxer");
            }

            mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
            // should happen before receiving buffers, and should only happen once
            if (muxerStarted) {
                throw new RuntimeException("format changed twice");
            }
            MediaFormat newFormat = mediaCodec.getOutputFormat();
            log.info("encoder output format changed: " + newFormat);

            // now that we have the Magic Goodies, start the muxer
            mTrackIndex = mediaMuxer.addTrack(newFormat);
            mediaMuxer.start();
            muxerStarted = true;
        } else {
            log.warn("unexpected result from encoder.dequeueOutputBuffer: " + outputBufferIndex);
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
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();;
            mediaMuxer = null;
        }
    }
}
