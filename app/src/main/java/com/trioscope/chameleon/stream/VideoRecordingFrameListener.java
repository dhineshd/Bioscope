package com.trioscope.chameleon.stream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.IntOrByteArray;
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
    private static final int RECORDING_FRAMES_PER_SEC = 20;
    private static final long TIMEOUT_MICROSECONDS = 5000;
    private static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    private static final String MIME_TYPE_VIDEO = "video/avc";
    private static final int AUDIO_SAMPLE_RATE = 22050;
    private static final int CHANNEL_COUNT = 1;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_BIT_RATE = 128000;
    private static final int AUDIO_SAMPLES_PER_FRAME = 1024; // AAC
    private static final int AUDIO_FRAMES_PER_BUFFER = 24;
    private static final int VIDEO_FRAME_RATE = 20;
    private static final int VIDEO_BIT_RATE = 6000000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.DEFAULT;
    private static final int VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    private volatile boolean isRecording;

    private volatile MediaCodec videoEncoder;
    private volatile MediaCodec audioEncoder;
    private volatile MediaMuxer mediaMuxer;
    private volatile AudioRecord audioRecorder;
    private volatile boolean muxerStarted;
    private volatile boolean audioRecorderStarted;
    private int audioTrackIndex = -1;
    private int videoTrackIndex = -1;
    private volatile Long firstFrameReceivedForRecordingTimeMillis;
    private long previousFrameSendTimeMs = 0;

    public VideoRecordingFrameListener(ChameleonApplication chameleonApplication) {
        this.chameleonApplication = chameleonApplication;
        setupAudioEncoder();
        setupVideoEncoder();
    }

    private void setupVideoEncoder() {
        int w = 1920, h = 1080;
        // Setup video encoder
        try {
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO);
            for (int colorFormat : videoEncoder.getCodecInfo().getCapabilitiesForType(MIME_TYPE_VIDEO).colorFormats ) {
                log.info("Supported color format = {}", colorFormat);
            }
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, w, h);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, VIDEO_COLOR_FORMAT);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();
        } catch (IOException e) {
            log.error("Failed to create video encoder", e);
        }
    }

    private void setupAudioEncoder() {
        try {
            audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            MediaFormat mediaFormat  = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, CHANNEL_COUNT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_OUT_MONO);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            int iMinBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int iBufferSize = AUDIO_SAMPLES_PER_FRAME * AUDIO_FRAMES_PER_BUFFER;

            // Ensure buffer is adequately sized for the AudioRecord
            // object to initialize
            if (iBufferSize < iMinBufferSize) {
                iBufferSize = ((iMinBufferSize / AUDIO_SAMPLES_PER_FRAME) + 1) * AUDIO_SAMPLES_PER_FRAME * 2;
            }

            log.info("AudioRecord min buffer size = {}, buffer size = {}", iMinBufferSize, iBufferSize);

            audioRecorder = new AudioRecord(
                    AUDIO_SOURCE, // source
                    AUDIO_SAMPLE_RATE, // sample rate, hz
                    CHANNEL_CONFIG, // channels
                    AUDIO_FORMAT, // audio format
                    iMinBufferSize); // buffer size (bytes)

            log.info("AudioRecord state = {}", audioRecorder.getState());

        } catch (IOException e) {
            log.error("Failed to create audio encoder", e);
        }
    }


    @Override
    public void onFrameAvailable(CameraInfo cameraInfo, IntOrByteArray data, FrameInfo frameInfo) {
        if (isRecording) {

            long frameReceiveTimeMillis = System.currentTimeMillis();
            long frameReceiveNanoTime = System.nanoTime();
            long framePresentationTimeMicros = frameInfo.getTimestampNanos() / 1000;

            log.debug("SystemClock.elapsedRealtimeNanos = {} ns, frame time = {} ns", System.nanoTime(), frameInfo.getTimestampNanos());
            //log.info("Frame delay = {} ms, presentation time = {} us", frameReceiveDelayMillis, framePresentationTimeMicros);

            if (cameraInfo.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
                if (shouldRecordCurrentFrame()) {
                    try {
                        long preFrameProcessingTime = System.currentTimeMillis();
                        //byte[] audioData = new byte[4096];
                        //audioRecorder.read(audioData, 0, 7168);
                        long actualPresentationTimeMicros = processFrame(data.getBytes(), framePresentationTimeMicros);
                        log.info("Frame processing time = {} ms", System.currentTimeMillis() - preFrameProcessingTime);
//                        chameleonApplication.getMetrics().
//                                sendTime(MetricNames.Category.VIDEO_SYNC.getName(),
//                                        MetricNames.Label.FRAME_DELAY_MILLIS.getName(), frameDelayMillis);
                        if (actualPresentationTimeMicros != -1
                                && firstFrameReceivedForRecordingTimeMillis == null) {
                            //long framePresentationDelayMillis = (framePresentationTimeMicros - actualPresentationTimeMicros) / 1000;
                            long frameDelayMillis = (frameReceiveNanoTime / 1000000)  - (framePresentationTimeMicros / 1000);
                            firstFrameReceivedForRecordingTimeMillis = frameReceiveTimeMillis - frameDelayMillis;
                            log.info("<first>Received first frame [{} x {}] with size = {} bytes after recording started at {}",
                                    cameraInfo.getCaptureResolution().getWidth(),
                                    cameraInfo.getCaptureResolution().getHeight(),
                                    data.getBytes().length,
                                    firstFrameReceivedForRecordingTimeMillis);
                            log.info("<first>Frame receive : uptime = {} ms, epoch time = {} ms", frameReceiveNanoTime / 1000000, frameReceiveTimeMillis);
                            log.info("<first>Frame presentation time : recorded = {} ms, actual = {} ms",
                                    framePresentationTimeMicros / 1000, actualPresentationTimeMicros / 1000);
                            log.info("<first>Frame delay = {} ms", frameDelayMillis);
                            chameleonApplication.setRecordingStartTimeMillis(firstFrameReceivedForRecordingTimeMillis);
                        }
                    } catch (Exception e) {
                        log.error("Failed to record frame", e);
                    }
                    previousFrameSendTimeMs = System.currentTimeMillis();
                }
            }
        }
    }

    private boolean shouldRecordCurrentFrame() {
//        return ((System.currentTimeMillis() - previousFrameSendTimeMs) >=
//                (1000 / RECORDING_FRAMES_PER_SEC));
        return true;
    }

    private long processFrame(final byte[] videoData, final long presentationTimeMicros) {

        long actualVideoPresentationTimeMicros = processVideo(videoData, presentationTimeMicros);

        long actualAudioPresentationTimeMicros = processAudio(presentationTimeMicros);

        // Start MediaMuxer when both audio and video tracks have been initialized
        if (!muxerStarted
                && videoTrackIndex != -1
                && audioTrackIndex != -1
                ) {
            mediaMuxer.start();
            muxerStarted = true;
        }

        return actualVideoPresentationTimeMicros;
        //return actualAudioPresentationTimeMicros;
    }

    private long processVideo(final byte[] videoData, final long presentationTimeMicros) {

        log.info("Processing video..");

        long actualPresentationTimeMicros = -1;

        // Process video
        int videoInputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (videoInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = videoEncoder.getInputBuffer(videoInputBufferIndex);
            inputBuffer.clear();
            log.info("video bytebuffer size = {}, frame size = {}", inputBuffer.capacity(), videoData.length);
            inputBuffer.put(videoData);
            videoEncoder.queueInputBuffer(videoInputBufferIndex, 0, videoData.length, presentationTimeMicros, 0);
        }

        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int videoOutputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_MICROSECONDS);
        if (videoOutputBufferIndex >= 0) {
            ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(videoOutputBufferIndex);
            log.info("Encoded frame available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.

            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                log.info("ignoring BUFFER_FLAG_CODEC_CONFIG");
                videoBufferInfo.size = 0;
            }

            if (videoBufferInfo.size != 0 && muxerStarted) {
                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outputBuffer.position(videoBufferInfo.offset);
                outputBuffer.limit(videoBufferInfo.offset + videoBufferInfo.size);

                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo);

                actualPresentationTimeMicros = videoBufferInfo.presentationTimeUs;

                log.info("sent " + videoBufferInfo.size + " video bytes to muxer. " +
                                "pts input = {} ms, output = {} ms, difference = {} ms",
                        presentationTimeMicros / 1000, videoBufferInfo.presentationTimeUs / 1000,
                        (presentationTimeMicros - videoBufferInfo.presentationTimeUs) / 1000);
            }

            videoEncoder.releaseOutputBuffer(videoOutputBufferIndex, false);
        } else if (videoOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = videoEncoder.getOutputFormat();
            log.info("video encoder output format changed: " + newFormat);

            // now that we have the Magic Goodies, start the muxer
            videoTrackIndex = mediaMuxer.addTrack(newFormat);
        } else {
            log.warn("unexpected result from video encoder.dequeueOutputBuffer: " + videoOutputBufferIndex);
        }
        return actualPresentationTimeMicros;
    }

    private long processAudio(final long presentationTimeMicros) {

        long actualPresentationTimeMicros = -1;
        log.info("Processing audio..");

        // Process audio
        int audioInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (audioInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(audioInputBufferIndex);
            inputBuffer.clear();
            int audioBytesRead = audioRecorder.read(inputBuffer, AUDIO_SAMPLES_PER_FRAME * 2);
            log.info("audio bytebuffer size = {}, bytes read = {}",
                    inputBuffer.capacity(), audioBytesRead);
            audioEncoder.queueInputBuffer(audioInputBufferIndex, 0, audioBytesRead, presentationTimeMicros, 0);
        }
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        int audioOutputBufferIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_MICROSECONDS);
        if (audioOutputBufferIndex >= 0) {
            ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(audioOutputBufferIndex);
            log.info("Encoded audio data available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.

            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                log.info("ignoring BUFFER_FLAG_CODEC_CONFIG");
                audioBufferInfo.size = 0;
            }

            if (audioBufferInfo.size != 0 && muxerStarted) {

                // adjust the ByteBuffer values to match BufferInfo (not needed?)
                outputBuffer.position(audioBufferInfo.offset);
                outputBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size);

                mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo);

                actualPresentationTimeMicros = audioBufferInfo.presentationTimeUs;

                log.info("sent " + audioBufferInfo.size + " audio bytes to muxer " +
                                "pts input = {} ms, output = {} ms, difference = {} ms",
                        presentationTimeMicros / 1000, audioBufferInfo.presentationTimeUs / 1000,
                        (presentationTimeMicros - audioBufferInfo.presentationTimeUs) / 1000);
            }
            audioEncoder.releaseOutputBuffer(audioOutputBufferIndex, false);

            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                log.warn("audio reached end of stream unexpectedly");
            }

        } else if (audioOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = audioEncoder.getOutputFormat();
            log.info("audio encoder output format changed: " + newFormat);

            // now that we have the Magic Goodies, start the muxer
            audioTrackIndex = mediaMuxer.addTrack(newFormat);
        } else {
            log.warn("unexpected result from audio encoder.dequeueOutputBuffer: " + audioOutputBufferIndex);
        }

        return actualPresentationTimeMicros;
    }

    private static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return model;
        }
        return manufacturer + "_" + model;
    }

    @Override
    public void onStartRecording(long recordingStartTimeMillis) {
        //Setup MediaMuxer to save MediaCodec output to file
        try {
            File outputFile = chameleonApplication.getOutputMediaFile(
                    getDeviceName()+ "_muxer.mp4");
            if (outputFile.exists()) {
                outputFile.delete();
            }
            chameleonApplication.setVideoFile(outputFile);
            mediaMuxer = new MediaMuxer(
                    chameleonApplication.getVideoFile().getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            isRecording = true;
            audioRecorder.startRecording();
        } catch (IOException e) {
            log.error("MediaMuxer creation failed", e);
        }
    }


    @Override
    public void onStopRecording() {
        isRecording = false;
        if (videoEncoder != null) {
            videoEncoder.stop();
            videoEncoder.release();
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder.release();
            audioEncoder = null;
        }
        if (audioRecorder != null) {
            audioRecorder.stop();;
            audioRecorder.release();;
            audioRecorder = null;
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();;
            mediaMuxer = null;
        }
        muxerStarted = false;
        audioRecorderStarted = false;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
    }
}
