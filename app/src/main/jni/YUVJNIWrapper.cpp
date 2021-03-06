#include <jni.h>

#include <stdlib.h>
#include "libyuv.h"

using namespace libyuv;

extern "C" {

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ByteBufferToNV12ByteBuffer(
        JNIEnv* env, jobject thiz, jobject i420Buf, jobject nv12Buf, int w, int h, bool isHorizontallyFlipped) {

    int Ysize = w * h;

    uint8* src_y = (uint8*) env->GetDirectBufferAddress(i420Buf);
    uint8* src_u = src_y + Ysize;
    uint8* src_v = src_u + (Ysize / 4);

    uint8* dst_y = (uint8*) env->GetDirectBufferAddress(nv12Buf);
    uint8* dst_uv = dst_y + Ysize;

    I420ToNV12(
            src_y, w,
            src_u, w / 2,
            src_v, w / 2,
            dst_y, w,
            dst_uv, w,
            w, isHorizontallyFlipped? -h : h);
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ByteBufferToNV21ByteBuffer(
        JNIEnv* env, jobject thiz, jobject i420Buf, jobject nv21Buf, int w, int h, bool isHorizontallyFlipped) {

    int Ysize = w * h;

    uint8* src_y = (uint8*) env->GetDirectBufferAddress(i420Buf);
    uint8* src_u = src_y + Ysize;
    uint8* src_v = src_u + (Ysize / 4);

    uint8* dst_y = (uint8*) env->GetDirectBufferAddress(nv21Buf);
    uint8* dst_uv = dst_y + Ysize;

    I420ToNV21(
            src_y, w,
            src_u, w / 2,
            src_v, w / 2,
            dst_y, w,
            dst_uv, w,
            w, isHorizontallyFlipped? -h : h);
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_transformI420ByteBuffer(
        JNIEnv* env, jobject thiz, jobject i420InputBuf, jobject i420OutputBuf, int w, int h,
        bool isHorizontallyFlipped) {

    int Ysize = w * h;

    uint8* src_y = (uint8*) env->GetDirectBufferAddress(i420InputBuf);
    uint8* src_u = src_y + Ysize;
    uint8* src_v = src_u + (Ysize / 4);

    uint8* dst_y = (uint8*) env->GetDirectBufferAddress(i420OutputBuf);
    uint8* dst_u = dst_y + Ysize;
    uint8* dst_v = dst_u + (Ysize / 4);

    I420Copy(
            src_y, w,
            src_u, w / 2,
            src_v, w / 2,
            dst_y, w,
            dst_u, w / 2,
            dst_v, w / 2,
            w, isHorizontallyFlipped? -h : h);
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_scaleAndConvertI420ByteBufferToNV21ByteBuffer(
		JNIEnv* env, jobject thiz, jobject i420Buf, jobject nv21Buf, jobject scalingBuf,  jobject rotationBuf,
        int oldW, int oldH, int newW, int newH, bool isHorizontallyFlipped, int orientationDegrees) {
    int newLen = newW * newH * 3 / 2;

    int oldYsize = oldW * oldH;
    int newYsize = newW * newH;

    uint8* src_y = (uint8*) env->GetDirectBufferAddress(i420Buf);
    uint8* src_u = src_y + oldYsize;
    uint8* src_v = src_u + (oldYsize / 4);

    uint8* scaling_y = (uint8*) env->GetDirectBufferAddress(scalingBuf);
    uint8* scaling_u =scaling_y + newYsize;
    uint8* scaling_v = scaling_u + (newYsize / 4);

    I420Scale(
		src_y, oldW,
		src_u, oldW / 2,
        src_v, oldW / 2,
        oldW, isHorizontallyFlipped ? -oldH : oldH,
        scaling_y, newW,
        scaling_u, newW / 2,
        scaling_v, newW / 2,
        newW, newH,
        kFilterNone);

    uint8* rotation_y = (uint8*) env->GetDirectBufferAddress(rotationBuf);
    uint8* rotation_u = rotation_y + newYsize;
    uint8* rotation_v = rotation_u + (newYsize / 4);

    int width = newW, height = newH;
    RotationMode rotationMode;
    switch (orientationDegrees) {
        case 90: rotationMode = kRotate90; width = newH; height = newW; break;
        case 180: rotationMode = kRotate180; break;
        case 270: rotationMode = kRotate270; width = newH; height = newW; break;
        default: rotationMode = kRotate0;
    }

    I420Rotate(
            scaling_y, newW,
            scaling_u, newW / 2,
            scaling_v, newW / 2,
            rotation_y, width,
            rotation_u, width / 2,
            rotation_v, width / 2,
            newW, newH,
            rotationMode);

    uint8* dst_y = (uint8*) env->GetDirectBufferAddress(nv21Buf);
    uint8* dst_uv = dst_y + newYsize;

    I420ToNV21(
        rotation_y, width,
        rotation_u, width / 2,
        rotation_v, width / 2,
        dst_y, width,
        dst_uv, width,
        width, height);
}

}
