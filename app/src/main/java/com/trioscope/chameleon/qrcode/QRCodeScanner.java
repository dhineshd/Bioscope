package com.trioscope.chameleon.qrcode;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.LuminanceSource;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.trioscope.chameleon.ChameleonApplication;
import com.trioscope.chameleon.aop.Timed;
import com.trioscope.chameleon.camera.impl.FrameInfo;
import com.trioscope.chameleon.listener.CameraFrameAvailableListener;
import com.trioscope.chameleon.listener.CameraFrameBuffer;
import com.trioscope.chameleon.listener.CameraFrameData;
import com.trioscope.chameleon.listener.QRCodeScanEventListener;
import com.trioscope.chameleon.stream.CameraFrameUtil;
import com.trioscope.chameleon.types.CameraInfo;
import com.trioscope.chameleon.types.Size;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by dhinesh.dharman on 11/9/15.
 */
@RequiredArgsConstructor
@Slf4j
public class QRCodeScanner implements CameraFrameAvailableListener{
    private static final int SCANNING_FRAMES_PER_SEC = 2;
    private static final int IMAGE_COMPRESSION_QUALITY = 100; // 0 worst - 100 best

    @NonNull
    private volatile CameraFrameBuffer cameraFrameBuffer;
    @NonNull
    private volatile QRCodeScanEventListener qrCodeScanEventListener;
    private volatile long previousFrameScanTimeMs = 0;
    private volatile DecodeQRCodeThreadHandler decodeThreadHandler = new DecodeQRCodeThreadHandler();

    // TODO Initialize buffer after size is known
    private Size cameraFrameSize = ChameleonApplication.getDefaultCameraPreviewSize();
    private final int cameraFrameBytes = cameraFrameSize.getWidth() * cameraFrameSize.getHeight() * 3/2;
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream(cameraFrameBytes);
    private ByteBuffer inputByteBuffer = ByteBuffer.allocateDirect(cameraFrameBytes);
    private ByteBuffer outputByteBuffer = ByteBuffer.allocateDirect(cameraFrameBytes);
    private int[] bitmapPixels = new int[cameraFrameSize.getWidth() * cameraFrameSize.getHeight()];
    private QRCodeReader qrCodeReader = new QRCodeReader();

    public void start() {
        cameraFrameBuffer.addListener(this);
    }

    public void stop() {
        cameraFrameBuffer.removeListener(this);
    }

    @Override
    public void onFrameAvailable(CameraInfo cameraInfo, CameraFrameData data, FrameInfo frameInfo) {

        int cameraWidth = cameraInfo.getCameraResolution().getWidth();
        int cameraHeight = cameraInfo.getCameraResolution().getHeight();

        if (shouldScanCurrentFrame()) {
            try {
                stream.reset();
                inputByteBuffer.clear();
                outputByteBuffer.clear();

                if (cameraInfo.getEncoding() == CameraInfo.ImageEncoding.YUV_420_888) {
                    if (data.getBytes() != null) {
                        inputByteBuffer.put(data.getBytes());
                        byte[] imageBytes = CameraFrameUtil.convertYUV420888ByteBufferToJPEGByteArray(
                                inputByteBuffer, outputByteBuffer, null,
                                null, stream, cameraWidth, cameraHeight, cameraWidth,
                                cameraHeight, IMAGE_COMPRESSION_QUALITY,
                                frameInfo.isHorizontallyFlipped(), frameInfo.getOrientationDegrees());
                    decodeThreadHandler.sendMessage(decodeThreadHandler.obtainMessage(
                            DecodeQRCodeThreadHandler.DECODE_QR_CODE, imageBytes));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process camera frame for QR code detection : " + e.getMessage());
            }
            previousFrameScanTimeMs = System.currentTimeMillis();
        }
    }

    private boolean shouldScanCurrentFrame() {
        return (System.currentTimeMillis() - previousFrameScanTimeMs) >= (1000 / SCANNING_FRAMES_PER_SEC);
    }

    @Timed
    private String decodeQRCode(final byte[] imageBytes) {
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            bmp.getPixels(bitmapPixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());
            LuminanceSource source = new RGBLuminanceSource(bmp.getWidth(), bmp.getHeight(), bitmapPixels);
            BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));
            return decodeBitmap(binaryBitmap);
        } catch (Exception e) {
            log.warn("Failed to decode QR code from image: " + e.getMessage());
        }
        return null;
    }

    @Timed
    private String decodeBitmap(final BinaryBitmap binaryBitmap)
            throws FormatException, ChecksumException, NotFoundException {
        return qrCodeReader.decode(binaryBitmap).getText();
    }

    private class DecodeQRCodeThreadHandler extends Handler {
        public static final int DECODE_QR_CODE = 1;
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == DECODE_QR_CODE) {
                String decodedText = decodeQRCode((byte[]) msg.obj);
                if (decodedText != null) {
                    log.info("QR code detected = {}", decodedText);
                    qrCodeScanEventListener.onTextDecoded(decodedText);
                }
            } else {
                super.handleMessage(msg);
            }
        }
    }
}
