#include <jni.h>

#include <string.h>
#include <stdlib.h>
#include "libyuv.h"
#include <android/log.h>

#define LOG_TAG "YUVJNIWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define printf(...) LOGI(__VA_ARGS__)

using namespace libyuv;

extern "C" {
JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV12(
		JNIEnv* env, jobject thiz, jbyteArray i420, jbyteArray nv12, int w, int h) {

	int i420Len = w * h * 3 / 2;
	unsigned char* i420Buf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len,
			reinterpret_cast<jbyte*>(i420Buf));

	int Ysize = w * h;

	unsigned char* nv12Buf = new unsigned char[i420Len];

    unsigned char* src_y = i420Buf;
    unsigned char* src_u = i420Buf + Ysize;
    unsigned char* src_v = src_u + (Ysize / 4);

	unsigned char* dst_y = nv12Buf;
	unsigned char* dst_uv = dst_y + Ysize;

	libyuv::I420ToNV12(
			(uint8*) src_y, w, 
			(uint8*) src_u, w/2,
			(uint8*) src_v, w/2,
			(uint8*) dst_y, w,
			(uint8*) dst_uv, w,
			w, h);

	env->SetByteArrayRegion(nv12, 0, i420Len,
			reinterpret_cast<jbyte*>(nv12Buf));

	if (nv12Buf) {
		delete[] nv12Buf;
	}

	if (i420Buf) {
		delete[] i420Buf;
	}
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_scaleAndConvertI420ToNV21(
	JNIEnv* env, jobject thiz, jbyteArray i420, jbyteArray nv21, int oldW, int oldH, int newW, int newH) {
	int oldLen = oldW * oldH * 3 / 2;
    int newLen = newW * newH * 3 / 2;
	unsigned char* i420Buf = new unsigned char[oldLen];
	env->GetByteArrayRegion(i420, 0, oldLen,
	    reinterpret_cast<jbyte*>(i420Buf));

	int oldYsize = oldW * oldH;
    int newYsize = newW * newH;

	unsigned char* tempBuf = new unsigned char[newLen];

	unsigned char* src_y = i420Buf;
	unsigned char* src_u = i420Buf + oldYsize;
	unsigned char* src_v = src_u + (oldYsize / 4);

	unsigned char* temp_y = tempBuf;
	unsigned char* temp_u = tempBuf + newYsize;
	unsigned char* temp_v = src_u + (newYsize / 4);

	libyuv::I420Scale(
        src_y, oldW,
        src_u, oldW / 2,
        src_v, oldW / 2,
        oldW, oldH,
        temp_y, newW,
        temp_u, newW / 2,
        temp_v, newW / 2,
        newW, newH,
        libyuv::kFilterNone);

    unsigned char* outputBuf = new unsigned char[newLen];
    unsigned char* dst_y = outputBuf;
    unsigned char* dst_uv = dst_y + newYsize;

	libyuv::I420ToNV21(
		(uint8*) temp_y, newW,
		(uint8*) temp_u, newW / 2,
		(uint8*) temp_v, newW / 2,
		(uint8*) dst_y, newW,
		(uint8*) dst_uv, newW,
		newW, newH);

	env->SetByteArrayRegion(nv21, 0, newLen,
		reinterpret_cast<jbyte*>(outputBuf));

    if (tempBuf) {
        delete[] tempBuf;
    }

    if (i420Buf) {
        delete[] i420Buf;
    }

	if (outputBuf) {
		delete[] outputBuf;
	}
}

}
