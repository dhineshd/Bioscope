package com.trioscope.chameleon.listener;

import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.os.ParcelFileDescriptor;

import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.net.InetAddress;
import java.net.UnknownHostException;

import lombok.NonNull;

/**
 * Created by dhinesh.dharman on 6/4/15.
 */
public class VideoStreamFrameListener implements CameraFrameAvailableListener {
    private static final String TAG = VideoStreamFrameListener.class.getSimpleName();

    @NonNull
    private final MediaCodec mMediaCodec;
    @NonNull
    private final EncoderDebugger mEncoderDebugger;
    @NonNull
    private final AbstractPacketizer packetizer;
    @NonNull
    private final ParcelFileDescriptor writeParcelFd;

    private boolean firstTime = true;

    private long now = System.nanoTime()/1000, oldnow = now, i=0;

    public VideoStreamFrameListener(
            final MediaCodec mediaCodec,
            final EncoderDebugger encoderDebugger,
            final ParcelFileDescriptor writeParcelFd){
        mMediaCodec = mediaCodec;
        mEncoderDebugger = encoderDebugger;//EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
        this.writeParcelFd = writeParcelFd;
        packetizer = new H264Packetizer();
        packetizer.setInputStream(new MediaCodecInputStream(mediaCodec));
        try {
            packetizer.setDestination(InetAddress.getByName("192.168.0.5"), 5006, 5007);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        packetizer.start();
    }

    @Override
    public void onFrameAvailable(int[] data) {
//        ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
//        IntBuffer intBuffer = byteBuffer.asIntBuffer();
//        intBuffer.put(data);
//        byte[] frameBytes = byteBuffer.array();

        //Log.d(TAG, "Received frame bytes count = " + frameBytes.length);

        if (!firstTime){
            return;
        }
//        BufferedOutputStream bos = null;
//        try {
//            bos = new BufferedOutputStream(new FileOutputStream("/storage/sdcard0/_chameleon/test.png"));
//            Bitmap bmp = Bitmap.createBitmap(176, 144, Bitmap.Config.ARGB_8888);
//            bmp.copyPixelsFromBuffer(IntBuffer.wrap(data));
//            byte[] frameBytes = getNV21(176, 144, bmp);
//            bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
//            //bmp.recycle();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        } finally {
//            if (bos != null) try {
//                bos.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        firstTime = false;
        //ImageView imageView = (ImageView) activity.getWindow().getDecorView().findViewById(R.id.imageView_stream);
        //imageView.setImageBitmap(bmp);
//        oldnow = now;
//        now = System.nanoTime()/1000;
//        if (i++>3) {
//            i = 0;
//            //Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
//        }
//        int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
//        if (bufferIndex >= 0) {
//            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
//            inputBuffers[bufferIndex].clear();
//            if (data == null) Log.e(TAG, "Symptom of the \"Callback buffer was to small\" problem...");
//            else mEncoderDebugger.getNV21Convertor().convert(frameBytes, inputBuffers[bufferIndex]);
//            mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
//        } else {
//            Log.e(TAG, "No buffer available !");
//        }
    }

    // untested function
    byte [] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {
        int [] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte [] yuv = new byte[inputWidth*inputHeight*3/2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        scaled.recycle();

        return yuv;
    }

    void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
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
}
