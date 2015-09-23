#include <jni.h>

#include <string.h>
#include <stdlib.h>
#include "libyuv.h"
#include <android/log.h>

using namespace libyuv;

extern "C" {
JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV12(
		JNIEnv* env, jobject thiz, jbyteArray i420, jbyteArray nv12, int w, int h) {

	int i420Len = w * h * 3 / 2;
	unsigned char* i420Buf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len, reinterpret_cast<jbyte*>(i420Buf));

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

JNIEXPORT jbyteArray Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV12AndReturnByteArray(
        JNIEnv* env, jobject thiz, jbyteArray i420, int w, int h) {

	int i420Len = w * h * 3 / 2;
	unsigned char* i420Buf = new unsigned char[i420Len];
	env->GetByteArrayRegion(i420, 0, i420Len, reinterpret_cast<jbyte*>(i420Buf));

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

    jbyteArray nv12 = env->NewByteArray(i420Len);
	env->SetByteArrayRegion(nv12, 0, i420Len,
							reinterpret_cast<jbyte*>(nv12Buf));

	if (nv12Buf) {
		delete[] nv12Buf;
	}

	if (i420Buf) {
		delete[] i420Buf;
	}
    return nv12;
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_convertI420ToNV12Method2(
        JNIEnv* env, jobject thiz, jobject buffer_y, jobject buffer_u, jobject buffer_v,
        jbyteArray nv12, int w, int h) {

    int i420Len = w * h * 3 / 2;

    unsigned char* src_y = (unsigned char*) env->GetDirectBufferAddress(buffer_y);
    unsigned char* src_u = (unsigned char*) env->GetDirectBufferAddress(buffer_u);
    unsigned char* src_v = (unsigned char*) env->GetDirectBufferAddress(buffer_v);

    int Ysize = w * h;

    unsigned char* nv12Buf = new unsigned char[i420Len];

    unsigned char* dst_y = nv12Buf;
    unsigned char* dst_uv = dst_y + Ysize;

    libyuv::I420ToNV12(
            (uint8*) src_y, w,
            (uint8*) src_u, w / 2,
            (uint8*) src_v, w / 2,
            (uint8*) dst_y, w,
            (uint8*) dst_uv, w,
            w, h);

    env->SetByteArrayRegion(nv12, 0, i420Len,
                            reinterpret_cast<jbyte*>(nv12Buf));

    if (nv12Buf) {
        delete[] nv12Buf;
    }
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_scaleAndConvertI420ToNV21(
	JNIEnv* env, jobject thiz, jbyteArray i420, jbyteArray nv21, int oldW, int oldH, int newW, int newH) {
	int oldLen = oldW * oldH * 3 / 2;
    int newLen = newW * newH * 3 / 2;
	unsigned char* i420Buf = new unsigned char[oldLen];
	env->GetByteArrayRegion(i420, 0, oldLen, reinterpret_cast<jbyte*>(i420Buf));

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

	env->SetByteArrayRegion(nv21, 0, newLen, reinterpret_cast<jbyte*>(outputBuf));

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

JNIEXPORT jbyteArray Java_com_trioscope_chameleon_util_ColorConversionUtil_scaleAndConvertI420ToNV21AndReturnByteArray(
        JNIEnv* env, jobject thiz, jbyteArray i420, int oldW, int oldH, int newW, int newH, bool inversion) {
    int oldLen = oldW * oldH * 3 / 2;
    int newLen = newW * newH * 3 / 2;
    unsigned char* i420Buf = new unsigned char[oldLen];
    env->GetByteArrayRegion(i420, 0, oldLen, reinterpret_cast<jbyte*>(i420Buf));

    int oldYsize = oldW * oldH;
    int newYsize = newW * newH;

    unsigned char* tempBuf = new unsigned char[newLen];
    unsigned char* tempBuf2 = NULL;

    unsigned char* src_y = i420Buf;
    unsigned char* src_u = i420Buf + oldYsize;
    unsigned char* src_v = src_u + (oldYsize / 4);

    unsigned char* temp_y = tempBuf;
    unsigned char* temp_u = tempBuf + newYsize;
    unsigned char* temp_v = temp_u + (newYsize / 4);

    libyuv::I420Scale(
            src_y, oldW,
            src_u, oldW / 2,
            src_v, oldW / 2,
            inversion ? -oldW : oldW, inversion ? -oldH : oldH,
            temp_y, newW,
            temp_u, newW / 2,
            temp_v, newW / 2,
            newW, newH,
            libyuv::kFilterNone);

    if (inversion) {

//        tempBuf2 = new unsigned char[newLen];
//
//        unsigned char* temp2_y = tempBuf;
//        unsigned char* temp2_u = tempBuf + newYsize;
//        unsigned char* temp2_v = temp2_u + (newYsize / 4);
//
//        libyuv::I420Mirror(
//        (uint8*) temp_y, newW,
//        (uint8*) temp_u, newW / 2,
//        (uint8*) temp_v, newW / 2,
//        (uint8*) temp2_y, newW,
//        (uint8*) temp2_u, newW,
//        (uint8*) temp2_v, newW,
//        newW, newH);
//
//        // Re-assigning temp to new buffer
//        temp_y = tempBuf2;
//        temp_u = tempBuf2 + newYsize;
//        temp_v = temp_u + (newYsize / 4);
    }

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

    jbyteArray nv12 = env->NewByteArray(newLen);
    env->SetByteArrayRegion(nv12, 0, newLen, reinterpret_cast<jbyte*>(outputBuf));

    if (i420Buf) {
        delete[] i420Buf;
    }

    if (tempBuf) {
        delete[] tempBuf;
    }

    if (tempBuf2) {
        delete[] tempBuf2;
    }

    if (outputBuf) {
        delete[] outputBuf;
    }
    return nv12;
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_scaleAndConvertI420ToNV21Method2(
		JNIEnv* env, jobject thiz, jobject buffer_y, jobject buffer_u, jobject buffer_v,
        jbyteArray nv21, int oldW, int oldH, int newW, int newH) {

	int oldLen = oldW * oldH * 3 / 2;
	int newLen = newW * newH * 3 / 2;

    unsigned char* src_y = (unsigned char*) env->GetDirectBufferAddress(buffer_y);
    unsigned char* src_u = (unsigned char*) env->GetDirectBufferAddress(buffer_u);
    unsigned char* src_v = (unsigned char*) env->GetDirectBufferAddress(buffer_v);

    int oldYsize = oldW * oldH;
	int newYsize = newW * newH;

	unsigned char* tempBuf = new unsigned char[newLen];

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

	env->SetByteArrayRegion(nv21, 0, newLen, reinterpret_cast<jbyte*>(outputBuf));

	if (tempBuf) {
		delete[] tempBuf;
	}

	if (outputBuf) {
		delete[] outputBuf;
	}
}

JNIEXPORT void Java_com_trioscope_chameleon_util_ColorConversionUtil_rotateI420By90Degrees(
		JNIEnv* env, jobject thiz, jbyteArray inputArray, jbyteArray outputArray, int w, int h) {

	// Not working !!!

	int length = w * h * 3 / 2;
	unsigned char* inputBuf = new unsigned char[length];
	env->GetByteArrayRegion(inputArray, 0, length, reinterpret_cast<jbyte*>(inputBuf));

	int Ysize = w * h;

	unsigned char* outputBuf = new unsigned char[length * 2];

	unsigned char* src_y = inputBuf;
	unsigned char* src_u = inputBuf + Ysize;
	unsigned char* src_v = src_u + (Ysize / 4);

	unsigned char* dst_y = outputBuf;
	unsigned char* dst_u = outputBuf + Ysize;
    unsigned char* dst_v = dst_u + (Ysize / 4);

	libyuv::I420Rotate(
			(uint8*) src_y, w,
			(uint8*) src_u, w / 2,
			(uint8*) src_v, w / 2,
			(uint8*) dst_y, h,
			(uint8*) dst_u, h,
            (uint8*) dst_v, h,
			w, h,
            libyuv::kRotate90);

	env->SetByteArrayRegion(outputArray, 0, length, reinterpret_cast<jbyte*>(outputBuf));

	if (inputBuf) {
		delete[] inputBuf;
	}

	if (outputBuf) {
		delete[] outputBuf;
	}
}

}
