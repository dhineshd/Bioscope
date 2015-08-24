#include <jni.h>

#include <string.h>
#include <stdlib.h>
#include "libyuv.h"
#include <android/log.h>

#define LOG_TAG "YUVDemo"
#define LOGI(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define printf(...) LOGI(__VA_ARGS__)

using namespace libyuv;

extern "C" {
JNIEXPORT jbyteArray Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV12(
		JNIEnv* env, jobject thiz, jbyteArray i420, int w, int h) {

	int i420Len = env->GetArrayLength(i420);
	unsigned char* i420Buf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len,
			reinterpret_cast<jbyte*>(i420Buf));

	int Ysize = w * h;
	size_t src_size = Ysize * 3 / 2;

	unsigned char* nv12 = new unsigned char[i420Len];

        unsigned char* src_y = i420Buf;
        unsigned char* src_u = i420Buf + Ysize;
        unsigned char* src_v = src_u + (Ysize / 4);

	unsigned char* dst_y = nv12;
	unsigned char* dst_uv = dst_y + Ysize;

	libyuv::I420ToNV12(
			(uint8*) src_y, w, 
			(uint8*) src_u, w/2,
			(uint8*) src_v, w/2,
			(uint8*) dst_y, w,
			(uint8*) dst_uv, w,
			w, h);

	jbyteArray nv12byte = env->NewByteArray(i420Len);
	env->SetByteArrayRegion(nv12byte, 0, i420Len,
			reinterpret_cast<jbyte*>(nv12));

	if (nv12) {
		delete[] nv12;
	}

	if (i420Buf) {
		delete[] i420Buf;
	}

	return nv12byte;
}

JNIEXPORT jbyteArray Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV21(
		JNIEnv* env, jobject thiz, jbyteArray i420, int w, int h) {

	int i420Len = env->GetArrayLength(i420);
	unsigned char* i420Buf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len,
			reinterpret_cast<jbyte*>(i420Buf));

	int Ysize = w * h;
	size_t src_size = Ysize * 3 / 2;

	unsigned char* outputBuf = new unsigned char[i420Len];

        unsigned char* src_y = i420Buf;
        unsigned char* src_u = i420Buf + Ysize;
        unsigned char* src_v = src_u + (Ysize / 4);

	unsigned char* dst_y = outputBuf;
	unsigned char* dst_uv = dst_y + Ysize;

	libyuv::I420ToNV21(
			(uint8*) src_y, w, 
			(uint8*) src_u, w/2,
			(uint8*) src_v, w/2,
			(uint8*) dst_y, w,
			(uint8*) dst_uv, w,
			w, h);

	jbyteArray outputByteArray = env->NewByteArray(i420Len);
	env->SetByteArrayRegion(outputByteArray, 0, i420Len,
			reinterpret_cast<jbyte*>(outputBuf));

	if (outputBuf) {
		delete[] outputBuf;
	}

	if (i420Buf) {
		delete[] i420Buf;
	}

	return outputByteArray;
}


JNIEXPORT jbyteArray Java_com_trioscope_chameleon_util_ColorConversionUtil_i420ScaleAndRotateBy90(
		JNIEnv* env, jobject thiz, jbyteArray i420, int oldW, int oldH, int newW, int newH) {

	int i420Len = env->GetArrayLength(i420);
	unsigned char* inputBuf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len,
			reinterpret_cast<jbyte*>(inputBuf));

	int oldYsize = oldW * oldH;
	int newYsize = newW * newH;
	size_t src_size = oldYsize * 3 / 2;

	int outputLen = newW * newH * 3 / 2;
	unsigned char* outputBuf = new unsigned char[outputLen];

        unsigned char* src_y = inputBuf;
        unsigned char* src_u = inputBuf + oldYsize;
        unsigned char* src_v = src_u + (oldYsize / 4);


	unsigned char* dst_y = outputBuf;
	unsigned char* dst_u = outputBuf + newYsize;
        unsigned char* dst_v = dst_u + (newYsize / 4);

         /*libyuv::ConvertToI420(const uint8* src_frame, size_t src_size,
	 uint8* dst_y, int dst_stride_y,
	 uint8* dst_u, int dst_stride_u,
	 uint8* dst_v, int dst_stride_v,
	 int crop_x, int crop_y,
	 int src_width, int src_height,
	 int crop_width, int crop_height,
	 enum RotationMode rotation,
	 uint32 format);*/
	/*
	libyuv::ConvertToI420(
		(uint8*) inputBuf, src_size,
                (uint8*) dst_y, newW,
                (uint8*) dst_u, newW,
                (uint8*) dst_v, newW,
		1, 1,
		oldW, oldH,
		newW, newH,
		libyuv::kRotate90,
		libyuv::FOURCC_I420); 	
	*/
        /*I420Scale(const uint8* src_y, int src_stride_y,
              const uint8* src_u, int src_stride_u,
              const uint8* src_v, int src_stride_v,
              int src_width, int src_height,
              uint8* dst_y, int dst_stride_y,
              uint8* dst_u, int dst_stride_u,
              uint8* dst_v, int dst_stride_v,
              int dst_width, int dst_height,
              enum FilterMode filtering);
        */
		
	libyuv::I420Scale(
		src_y, oldW,
		src_u, oldW / 2,
		src_v, oldW / 2,
		oldW, oldH,
		dst_y, newW,
		dst_u, newW / 2,
		dst_v, newW / 2,
		newW, newH,
		libyuv::kFilterNone);
 	
	jbyteArray outputByteArray = env->NewByteArray(outputLen);
	env->SetByteArrayRegion(outputByteArray, 0, outputLen,
			reinterpret_cast<jbyte*>(outputBuf));

	if (outputBuf) {
		delete[] outputBuf;
	}

	if (inputBuf) {
		delete[] inputBuf;
	}

	return outputByteArray;
}
}
