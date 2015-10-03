package com.trioscope.chameleon.stream;

import android.graphics.Bitmap;

import com.google.gson.Gson;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.stream.messages.StreamMetadata;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 9/28/15.
 */
@Slf4j
@RequiredArgsConstructor
public class PreviewStreamer implements NetworkStreamer, CameraFrameAvailableListener {
    private static final int STREAMING_FRAMES_PER_SEC = 15;
    private static final int STREAMING_COMPRESSION_QUALITY = 30; // 0 worst - 100 best
    private static final Size DEFAULT_STREAM_IMAGE_SIZE = new Size(480, 270); // 16 : 9

    @NonNull
    private volatile CameraFrameBuffer cameraFrameBuffer;
    private volatile OutputStream destOutputStream;

    private final ByteArrayOutputStream stream =
            new ByteArrayOutputStream(ChameleonApplication.STREAM_IMAGE_BUFFER_SIZE_BYTES);
    private final Gson gson = new Gson();
    private Size cameraFrameSize = ChameleonApplication.DEFAULT_CAMERA_PREVIEW_SIZE;
    private long previousFrameSendTimeMs = System.currentTimeMillis();
    private int streamPreviewWidth = DEFAULT_STREAM_IMAGE_SIZE.getWidth();
    private int streamPreviewHeight = DEFAULT_STREAM_IMAGE_SIZE.getHeight();

    @Override
    public void startStreaming(OutputStream destOs) {
        if (destOs != null) {
            destOutputStream = destOs;
            cameraFrameBuffer.addListener(this);
        }
    }

    @Override
    public void stopStreaming() {
        cameraFrameBuffer.removeListener(this);
        destOutputStream = null;
    }

    @Override
    public void onFrameAvailable(final CameraInfo cameraInfos, final CameraFrameData data, FrameInfo frameInfo) {
        int cameraWidth = cameraInfos.getCameraResolution().getWidth();
        int cameraHeight = cameraInfos.getCameraResolution().getHeight();

        if (shouldStreamCurrentFrame()) {
            log.debug("Decided to send current frame across stream");
            try {
                stream.reset();
                byte[] byteArray = null;

                if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
                    if (data.getBytes() != null) {
                        byteArray = CameraFrameUtil.convertYUV420888ByteArrayToJPEGByteArray(
                                data.getBytes(), stream, cameraWidth, cameraHeight, streamPreviewWidth,
                                streamPreviewHeight, frameInfo.isHorizontallyFlipped(),
                                STREAMING_COMPRESSION_QUALITY);
                    }
                } else if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.NV21) {
                    byteArray = CameraFrameUtil.convertNV21ToJPEGByteArray(data.getBytes(),
                            stream, cameraWidth, cameraHeight, streamPreviewWidth, streamPreviewHeight,
                            STREAMING_COMPRESSION_QUALITY);
                } else if (cameraInfos.getEncoding() == CameraInfo.ImageEncoding.RGBA_8888) {
                    new WeakReference<Bitmap>(CameraFrameUtil.convertToBmp(data.getInts(), cameraWidth, cameraHeight)).get()
                            .compress(Bitmap.CompressFormat.JPEG, STREAMING_COMPRESSION_QUALITY, stream);
                    byteArray = stream.toByteArray();
                }
                if (byteArray != null) {
                    log.debug("Stream image type = {}, size = {} bytes", cameraInfos.getEncoding(), byteArray.length);
                    StreamMetadata streamMetadata = StreamMetadata.builder()
                            .horizontallyFlipped(frameInfo.isHorizontallyFlipped())
                            .build();
                    PrintWriter pw = new PrintWriter(destOutputStream);
                    pw.println(gson.toJson(streamMetadata));
                    pw.close();
                    destOutputStream.write(byteArray, 0, byteArray.length);
                }
                previousFrameSendTimeMs = System.currentTimeMillis();
            } catch (Exception e) {
                log.error("Failed to send data to client", e);
            }
        }
    }

    private boolean shouldStreamCurrentFrame() {
        return ((destOutputStream != null) &&
                ((System.currentTimeMillis() - previousFrameSendTimeMs) >=
                (1000 / STREAMING_FRAMES_PER_SEC)));
    }
}