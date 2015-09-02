package com.trioscope.chameleon.stream;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;

import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.IntOrByteArray;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;
import com.trioscope.chameleon.util.ColorConversionUtil;

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
    private static final long TIMEOUT_MICROSECONDS = 5000;
    private static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    private static final String MIME_TYPE_VIDEO = "video/avc";
    private static final int AUDIO_SAMPLE_RATE = 22050;
    private static final int CHANNEL_COUNT = 1;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_BIT_RATE = 128000;
    private static final int AUDIO_SAMPLES_PER_FRAME = 1024; // AAC
    private static final int AUDIO_FRAMES_PER_BUFFER = 24;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int VIDEO_BIT_RATE = 6000000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.DEFAULT;
    private static final int VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    private volatile boolean isRecording;

    private volatile MediaCodec videoEncoder;
    private volatile MediaMuxer mediaMuxer;
    private AsyncTask audioRecordTask;
    private volatile boolean muxerStarted;
    private int audioTrackIndex = -1;
    private int videoTrackIndex = -1;
    private volatile Long firstFrameReceivedForRecordingTimeMillis;
    private Size cameraFrameSize;
    private volatile byte[] finalFrameData =
            new byte[ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE.getWidth() *
                    ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE.getHeight() * 3 / 2];

    public VideoRecordingFrameListener(ChameleonApplication chameleonApplication) {
        this.chameleonApplication = chameleonApplication;
        setupAudioEncoder();
        setupVideoEncoder();
    }

    private void setupVideoEncoder() {
        cameraFrameSize = ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE;
        //frameDataCopy = new byte[cameraFrameSize.getHeight() * cameraFrameSize.getWidth() * 3/2];

        // Setup video encoder
        try {
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO);
            for (int colorFormat : videoEncoder.getCodecInfo().getCapabilitiesForType(MIME_TYPE_VIDEO).colorFormats ) {
                log.info("Supported color format = {}", colorFormat);
            }
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO,
                    cameraFrameSize.getWidth(), cameraFrameSize.getHeight());
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FRAME_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            // Special case for API 21 and Exynos encoder
            if (videoEncoder.getCodecInfo().getName().contains("OMX.Exynos") &&
                    Build.VERSION.SDK_INT == 21) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
            } else {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, VIDEO_COLOR_FORMAT);
            }

            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();
        } catch (IOException e) {
            log.error("Failed to create video encoder", e);
        }
    }

    private MediaCodec setupAudioEncoder() {
        try {
            MediaCodec audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            MediaFormat mediaFormat  = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, CHANNEL_COUNT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_OUT_MONO);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            audioEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();
            return audioEncoder;
        } catch (IOException e) {
            log.error("Failed to create audio encoder", e);
        }
        return null;
    }


    @Override
    @Timed
    public void onFrameAvailable(CameraInfo cameraInfo, IntOrByteArray data, FrameInfo frameInfo) {
        if (isRecording) {

            long frameReceiveTimeMillis = System.currentTimeMillis();
            long frameReceiveTimeNanos = System.nanoTime();
            long framePresentationTimeMicros = frameInfo.getTimestampNanos() / 1000;

            log.debug("SystemClock.elapsedRealtimeNanos = {} ns, frame time = {} ns", System.nanoTime(), frameInfo.getTimestampNanos());
            //log.info("Frame delay = {} ms, presentation time = {} us", frameReceiveDelayMillis, framePresentationTimeMicros);

            if (cameraInfo.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
                if (shouldRecordCurrentFrame()) {

                    try {
                        long frameDelayMillis = (frameReceiveTimeNanos / 1000000)  - (framePresentationTimeMicros / 1000);
                        long adjustedFrameReceiveTimeMillis = frameReceiveTimeMillis - frameDelayMillis;
                        processFrame(data.getBytes(), framePresentationTimeMicros, adjustedFrameReceiveTimeMillis);
                    } catch (Exception e) {
                        log.error("Failed to record frame", e);
                    }
                }
            }
        }
    }

    private boolean shouldRecordCurrentFrame() {
//        return ((System.currentTimeMillis() - previousFrameSendTimeMs) >=
//                (1000 / RECORDING_FRAMES_PER_SEC));
        return true;
    }

    private void processFrame(
            final byte[] videoData,
            final long presentationTimeMicros,
            final long adjustedFrameReceiveTimeMillis) {

        processVideo(
                videoData,
                presentationTimeMicros,
                adjustedFrameReceiveTimeMillis);

        // Start MediaMuxer when both audio and video tracks have been initialized
        if (!muxerStarted
                && videoTrackIndex != -1
                && audioTrackIndex != -1
                ) {
            mediaMuxer.start();
            muxerStarted = true;
        }
    }

    private long processVideo(
            final byte[] frameData,
            final long presentationTimeMicros,
            final long frameReceiveTimeMillis) {

        log.info("Processing video..");

        // Since Qualcomm video encoder (default encoder on Nexus 5, LG G4)
        // doesn't support COLOR_FormatYUV420Planar, we need to convert
        // the frame data to COLOR_FormatYUV420SemiPlanar before handing it to MediaCodec.
        // TODO : Find color format used by encoder and use that to determine if conversion is necessary
        if (videoEncoder.getCodecInfo().getName().contains("OMX.qcom")) {
            log.info("Converting color format from YUV420Planar to YUV420SemiPlanar");
            ColorConversionUtil.convertI420ToNV12(frameData, finalFrameData, cameraFrameSize.getWidth(), cameraFrameSize.getHeight());
        } else {
            finalFrameData = frameData;
        }

        long actualPresentationTimeMicros = -1;

        // Process video
        int videoInputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (videoInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = videoEncoder.getInputBuffer(videoInputBufferIndex);
            log.info("video bytebuffer size = {}, frame size = {}", inputBuffer.capacity(), finalFrameData.length);
            inputBuffer.put(finalFrameData);
            videoEncoder.queueInputBuffer(videoInputBufferIndex, 0, finalFrameData.length, presentationTimeMicros, 0);
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
                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo);

                actualPresentationTimeMicros = videoBufferInfo.presentationTimeUs;

                if (firstFrameReceivedForRecordingTimeMillis == null) {
                    firstFrameReceivedForRecordingTimeMillis = frameReceiveTimeMillis;
                    chameleonApplication.setRecordingStartTimeMillis(frameReceiveTimeMillis);
                }

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

    private long processAudio(
            final MediaCodec audioEncoder,
            final AudioRecord audioRecorder,
            final long presentationTimeMicros) {
        long actualPresentationTimeMicros = -1;
        log.info("Processing audio..");

        // Process audio
        int audioInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (audioInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(audioInputBufferIndex);
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

    @Override
    public void onStartRecording(long recordingStartTimeMillis) {
        //Setup MediaMuxer to save MediaCodec output to file
        try {
            File outputFile = chameleonApplication.getOutputMediaFile("LocalVideo.mp4");
            if (outputFile.exists()) {
                outputFile.delete();
            }
            chameleonApplication.setVideoFile(outputFile);
            mediaMuxer = new MediaMuxer(
                    chameleonApplication.getVideoFile().getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            isRecording = true;

            // Process audio in a separate thread
            audioRecordTask = new AsyncTask<Void, Void, Void>(){

                @Override
                protected Void doInBackground(Void... voids) {

                    MediaCodec audioEncoder = setupAudioEncoder();

                    int iMinBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

                    log.info("AudioRecord min buffer size = {}", iMinBufferSize);

                    AudioRecord audioRecorder = new AudioRecord(
                            AUDIO_SOURCE, // source
                            AUDIO_SAMPLE_RATE, // sample rate, hz
                            CHANNEL_CONFIG, // channels
                            AUDIO_FORMAT, // audio format
                            iMinBufferSize * 2 // buffer size (bytes)
                    );

                    log.info("AudioRecord state = {}", audioRecorder.getState());

                    log.info("Async audio task started on thread = {}", Thread.currentThread());

                    audioRecorder.startRecording();

                    while (!isCancelled()) {
                        try {
                            processAudio(audioEncoder, audioRecorder, System.nanoTime() / 1000);
                            Thread.sleep(20);
                        } catch (Exception e) {
                            log.error("Failed to process audio!", e);
                        }
                    }
                    if (audioRecorder != null) {
                        audioRecorder.stop();;
                        audioRecorder.release();;
                    }
                    if (audioEncoder != null) {
                        audioEncoder.stop();
                        audioEncoder.release();
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        if (audioRecordTask != null) {
            audioRecordTask.cancel(true);
        }
        if (mediaMuxer != null) {
            mediaMuxer.stop();
            mediaMuxer.release();;
            mediaMuxer = null;
        }
        muxerStarted = false;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
    }
}
