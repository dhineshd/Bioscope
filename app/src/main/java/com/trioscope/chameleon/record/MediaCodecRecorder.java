package com.trioscope.chameleon.record;

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
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.RecordingMetadata;
import com.trioscope.chameleon.types.Size;
import com.trioscope.chameleon.util.ColorConversionUtil;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Record Audio + Video using MediaCodec and MediaMuxer.
 *
 * Created by dhinesh.dharman on 10/1/15.
 */

@Slf4j
@RequiredArgsConstructor
public class MediaCodecRecorder implements VideoRecorder, CameraFrameAvailableListener {

    private static final long TIMEOUT_MICROSECONDS = 5000;
    private static final String MIME_TYPE_AUDIO = "audio/mp4a-latm";
    private static final String MIME_TYPE_VIDEO = "video/avc";
    private static final int AUDIO_SAMPLE_RATE = 48000;
    private static final int CHANNEL_COUNT = 2;
    private static final int CHANNEL_CONFIG = CHANNEL_COUNT == 1 ?
            AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_BIT_RATE = 256 * 1024;
    private static final int AUDIO_SAMPLES_PER_FRAME = 2 * 1024; // AAC
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int VIDEO_BIT_RATE = 5000000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.CAMCORDER;
    private static final int VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
    private static final int CAMERA_FRAMES_TO_SKIP_BEFORE_STARTING_AUDIO_COUNT = 30;

    @NonNull
    private volatile ChameleonApplication chameleonApplication;
    @NonNull
    private volatile CameraFrameBuffer cameraFrameBuffer;

    private volatile boolean isRecording;
    private volatile MediaCodec videoEncoder;
    private volatile AsyncTask audioRecordTask;

    private volatile MediaMuxer mediaMuxer;
    private volatile boolean muxerStarted;
    private volatile int audioTrackIndex = -1;
    private volatile int videoTrackIndex = -1;
    private volatile Long firstFrameReceivedForRecordingTimeMillis;

    private Size cameraFrameSize = ChameleonApplication.getDefaultCameraPreviewSize();
    private byte[] finalFrameData;
    private File outputFile;
    private RecordingMetadata recordingMetadata;
    private volatile int processedCameraFrameCount = 0;
    private ByteBuffer inputByteBuffer;
    private ByteBuffer outputByteBuffer;

    @Override
    public boolean startRecording() {
        firstFrameReceivedForRecordingTimeMillis = null;
        videoTrackIndex = -1;
        audioTrackIndex = -1;
        processedCameraFrameCount = 0;

        // Start listening for camera frames
        // Note : This should happen before video encoder setup
        // so that we setup video encoder with correct frame size
        cameraFrameBuffer.addListener(this);

        setupVideoEncoder();

        //Setup MediaMuxer to save MediaCodec output to given file
        try {
            recordingMetadata = null;
            outputFile = chameleonApplication.createVideoFile(true);
            mediaMuxer = new MediaMuxer(
                    outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Start audio recording task
            startAudioRecordingTask();

            isRecording = true;

        } catch (Exception e) {
            log.error("Failed to start recording!", e);
            mediaMuxer = null;
            cameraFrameBuffer.removeListener(this);
            isRecording = false;
        }
        return isRecording;
    }

    @Override
    public RecordingMetadata stopRecording() {
        isRecording = false;
        muxerStarted = false;

        // Stop listening for camera frames
        cameraFrameBuffer.removeListener(this);

        // Stop audio recording task
        audioRecordTask.cancel(true);

        try {
            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
                videoEncoder = null;
            }
        } catch (Exception e) {
            log.error("Failed to stop videoEncoder", e);
        }

        try {
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
                mediaMuxer = null;
            }
        } catch (Exception e) {
            log.error("Failed to stop mediaMuxer", e);
        }

        return recordingMetadata;
    }

    @Override
    public boolean isRecording() {
        return isRecording;
    }

    private void setupVideoEncoder() {

        // Setup video encoder
        try {
            videoEncoder = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO);
            log.debug("Chosen encoder for {} : {}", MIME_TYPE_VIDEO, videoEncoder.getCodecInfo().getName());
            for (int colorFormat : videoEncoder.getCodecInfo().getCapabilitiesForType(MIME_TYPE_VIDEO).colorFormats) {
                log.debug("Supported color format = {}", colorFormat);
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
            log.debug("MediaFormat = {}", mediaFormat);
            videoEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            videoEncoder.start();
        } catch (IOException e) {
            log.error("Failed to create video encoder", e);
        }
    }

    private MediaCodec createAudioEncoder() {
        try {
            MediaCodec audioEncoder = MediaCodec.createEncoderByType(MIME_TYPE_AUDIO);
            MediaFormat mediaFormat  = MediaFormat.createAudioFormat(MIME_TYPE_AUDIO, AUDIO_SAMPLE_RATE, CHANNEL_COUNT);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
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
    public void onFrameAvailable(CameraInfo cameraInfo, CameraFrameData data, FrameInfo frameInfo) {
        cameraFrameSize = cameraInfo.getCameraResolution();
        long frameReceiveTimeMillis = System.currentTimeMillis();
        long frameReceiveTimeNanos = System.nanoTime();
        long framePresentationTimeMicros = frameInfo.getTimestampNanos() / 1000;
        long frameReceiveDelayMillis = (frameReceiveTimeNanos / 1000000)  - (framePresentationTimeMicros / 1000);

        log.debug("SystemClock.elapsedRealtimeNanos = {} ns, frame time = {} ns",
                System.nanoTime(), frameInfo.getTimestampNanos());
        log.debug("Frame delay = {} ms, presentation time = {} us",
                frameReceiveDelayMillis, framePresentationTimeMicros);

        if (cameraInfo.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
            try {
                long adjustedFrameReceiveTimeMillis = frameReceiveTimeMillis - frameReceiveDelayMillis;
                processFrame(data, frameInfo, framePresentationTimeMicros, adjustedFrameReceiveTimeMillis);
            } catch (Exception e) {
                log.error("Failed to record frame", e);
            }
        }
    }

    private void processFrame(
            final CameraFrameData frameData,
            final FrameInfo frameInfo,
            final long presentationTimeMicros,
            final long adjustedFrameReceiveTimeMillis) {

        // Start MediaMuxer when both audio and video tracks have been initialized
        if (!muxerStarted &&
                videoTrackIndex != -1 &&
                audioTrackIndex != -1) {

            // set video orientation hint based on camera orientation
            mediaMuxer.setOrientationHint(frameInfo.getOrientationDegrees());

            mediaMuxer.start();
            muxerStarted = true;
        }

        processVideo(
                frameData,
                frameInfo,
                presentationTimeMicros,
                adjustedFrameReceiveTimeMillis);
    }

    private void initializeBuffers() {
        if (inputByteBuffer == null) {
            inputByteBuffer = ByteBuffer.allocateDirect(
                    cameraFrameSize.getWidth() * cameraFrameSize.getHeight() * 3/2);
        }
        if (outputByteBuffer == null) {
            outputByteBuffer = ByteBuffer.allocateDirect(
                    cameraFrameSize.getWidth() * cameraFrameSize.getHeight() * 3/2);
        }
        inputByteBuffer.clear();
        outputByteBuffer.clear();
    }

    private void processVideo(
            final CameraFrameData frameData,
            final FrameInfo frameInfo,
            final long presentationTimeMicros,
            final long frameReceiveTimeMillis) {

        if (videoEncoder == null) {
            log.debug("Video encoder not setup.. not processing video");
            return;
        }
        log.debug("Processing video..");

        // Since Qualcomm video encoder (default encoder on Nexus 5, LG G4)
        // doesn't support COLOR_FormatYUV420Planar, we need to convert
        // the frame data to COLOR_FormatYUV420SemiPlanar before handing it to MediaCodec.
        // TODO : Find color format used by encoder and use that to determine if conversion is necessary
        if (frameData.getBytes() != null) {
            if (videoEncoder.getCodecInfo().getName().contains("OMX.qcom")) {
                log.debug("Performing color conversion.. w = {}, h = {}",
                        cameraFrameSize.getWidth(), cameraFrameSize.getHeight());

                initializeBuffers();
                inputByteBuffer.put(frameData.getBytes());
                ColorConversionUtil.convertI420ByteBufferToNV12ByteBuffer(
                        inputByteBuffer, outputByteBuffer,
                        cameraFrameSize.getWidth(), cameraFrameSize.getHeight(),
                        frameInfo.isHorizontallyFlipped());
                finalFrameData = outputByteBuffer.array();
            } else {
                if (frameInfo.isHorizontallyFlipped()) {
                    log.info("Transforming horizontally flipped.. w = {}, h = {}",
                            cameraFrameSize.getWidth(), cameraFrameSize.getHeight());

                    initializeBuffers();
                    inputByteBuffer.put(frameData.getBytes());
                    ColorConversionUtil.transformI420ByteBuffer(
                            inputByteBuffer, outputByteBuffer,
                            cameraFrameSize.getWidth(), cameraFrameSize.getHeight(),
                            frameInfo.isHorizontallyFlipped());
                    finalFrameData = outputByteBuffer.array();
                } else {
                    finalFrameData = frameData.getBytes();
                }
            }
        }

        // Process video
        int videoInputBufferIndex = videoEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (videoInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = videoEncoder.getInputBuffer(videoInputBufferIndex);
            int length = Math.min(finalFrameData.length, inputBuffer.capacity());
            log.debug("video bytebuffer size = {}, frame size = {}", inputBuffer.capacity(), finalFrameData.length);
            inputBuffer.put(finalFrameData, 0, length);
            videoEncoder.queueInputBuffer(videoInputBufferIndex, 0, length, presentationTimeMicros, 0);
        }

        MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
        int videoOutputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_MICROSECONDS);
        if (videoOutputBufferIndex >= 0) {
            ByteBuffer outputBuffer = videoEncoder.getOutputBuffer(videoOutputBufferIndex);
            log.debug("Encoded frame available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.

            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                log.debug("ignoring BUFFER_FLAG_CODEC_CONFIG");
                videoBufferInfo.size = 0;
            }

            if (videoBufferInfo.size != 0 && muxerStarted) {
                mediaMuxer.writeSampleData(videoTrackIndex, outputBuffer, videoBufferInfo);
                processedCameraFrameCount++;

                if (firstFrameReceivedForRecordingTimeMillis == null) {
                    firstFrameReceivedForRecordingTimeMillis = frameReceiveTimeMillis;
                    recordingMetadata = RecordingMetadata.builder()
                            .absoluteFilePath(outputFile.getAbsolutePath())
                            .startTimeMillis(frameReceiveTimeMillis)
                            .build();
                    log.debug("First video presentation time = {}", videoBufferInfo.presentationTimeUs);
                }

                log.debug("sent " + videoBufferInfo.size + " video bytes to muxer. " +
                                "pts input = {} ms, output = {} ms, difference = {} ms",
                        presentationTimeMicros / 1000, videoBufferInfo.presentationTimeUs / 1000,
                        (presentationTimeMicros - videoBufferInfo.presentationTimeUs) / 1000);
            }

            videoEncoder.releaseOutputBuffer(videoOutputBufferIndex, false);
        } else if (videoOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = videoEncoder.getOutputFormat();
            log.debug("video encoder output format changed: " + newFormat);

            // now that we have the Magic Goodies, start the muxer
            videoTrackIndex = mediaMuxer.addTrack(newFormat);

        } else {
            log.warn("unexpected result from video encoder.dequeueOutputBuffer: " + videoOutputBufferIndex);
        }

    }

    private void processAudio(
            final MediaCodec audioEncoder,
            final AudioRecord audioRecorder,
            final long presentationTimeMicros) {
        log.debug("Processing audio..");

        // Process audio
        int audioInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_MICROSECONDS);
        if (audioInputBufferIndex >= 0) {
            ByteBuffer inputBuffer = audioEncoder.getInputBuffer(audioInputBufferIndex);
            // PCM 16-bit samples
            int audioBytesRead = audioRecorder.read(inputBuffer, AUDIO_SAMPLES_PER_FRAME * 2);
            log.debug("audio bytebuffer size = {}, bytes read = {}",
                    inputBuffer.capacity(), audioBytesRead);
            audioEncoder.queueInputBuffer(audioInputBufferIndex, 0, audioBytesRead, presentationTimeMicros, 0);
        }
        MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
        int audioOutputBufferIndex = audioEncoder.dequeueOutputBuffer(audioBufferInfo, TIMEOUT_MICROSECONDS);
        if (audioOutputBufferIndex >= 0) {
            ByteBuffer outputBuffer = audioEncoder.getOutputBuffer(audioOutputBufferIndex);
            log.debug("Encoded audio data available! size = {}", outputBuffer.limit());
            // outputBuffer is ready to be processed or rendered.

            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                // The codec config data was pulled out and fed to the muxer when we got
                // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                log.debug("ignoring BUFFER_FLAG_CODEC_CONFIG");
                audioBufferInfo.size = 0;
            }

            // Skipping some frames so that audio track starts after some video frames
            // have been processed already. This ensures that distorted frames do not
            // show up at the beginning of the video due to audio starting before video.
            if (audioBufferInfo.size != 0 &&
                    muxerStarted &&
                    processedCameraFrameCount > CAMERA_FRAMES_TO_SKIP_BEFORE_STARTING_AUDIO_COUNT) {

                mediaMuxer.writeSampleData(audioTrackIndex, outputBuffer, audioBufferInfo);

                log.debug("sent " + audioBufferInfo.size + " audio bytes to muxer " +
                                "pts input = {} ms, output = {} ms, difference = {} ms",
                        presentationTimeMicros / 1000, audioBufferInfo.presentationTimeUs / 1000,
                        (presentationTimeMicros - audioBufferInfo.presentationTimeUs) / 1000);
            }
            audioEncoder.releaseOutputBuffer(audioOutputBufferIndex, false);
        } else if (audioOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = audioEncoder.getOutputFormat();
            log.debug("audio encoder output format changed: " + newFormat);

            // now that we have the Magic Goodies, start the muxer
            audioTrackIndex = mediaMuxer.addTrack(newFormat);
        } else {
            log.warn("unexpected result from audio encoder.dequeueOutputBuffer: " + audioOutputBufferIndex);
        }

    }

    private void startAudioRecordingTask() {

        // Process audio in a separate thread
        audioRecordTask = new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... voids) {

                MediaCodec audioEncoder = createAudioEncoder();

                int iMinBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

                log.debug("AudioRecord min buffer size = {}", iMinBufferSize);

                AudioRecord audioRecorder = new AudioRecord(
                        AUDIO_SOURCE, // source
                        AUDIO_SAMPLE_RATE, // sample rate, hz
                        CHANNEL_CONFIG, // channels
                        AUDIO_FORMAT, // audio format
                        iMinBufferSize * 2 // buffer size (bytes)
                );

                log.debug("AudioRecord state = {}", audioRecorder.getState());

                log.debug("Async audio task started on thread = {}", Thread.currentThread());

                audioRecorder.startRecording();

                while (isRecording) {
                    try {
                        processAudio(audioEncoder, audioRecorder, System.nanoTime() / 1000);
                        //Thread.sleep(20); // Avoid hogging CPU
                    } catch (Exception e) {
                        log.error("Failed to process audio!", e);
                    }
                }
                // Stop recoder
                audioRecorder.stop();
                audioRecorder.release();

                // Stop encoder
                audioEncoder.stop();
                audioEncoder.release();
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
