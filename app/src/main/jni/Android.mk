LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE    := yuv_shared
LOCAL_SRC_FILES := ../prebuilt/$(TARGET_ARCH_ABI)/libyuv_shared.so
ifneq (,$(wildcard $(LOCAL_PATH)/$(LOCAL_SRC_FILES)))
include $(PREBUILT_SHARED_LIBRARY)
endif


include $(CLEAR_VARS)

LOCAL_MODULE    := yuv_jni
LOCAL_SRC_FILES := YUVJNIWrapper.cpp

#
# Header Includes
#
LOCAL_C_INCLUDES := \
            $(LOCAL_PATH)/../include
#
# Compile Flags and Link Libraries
#
LOCAL_CFLAGS := -DANDROID_NDK

LOCAL_LDLIBS := -llog
LOCAL_SHARED_LIBRARIES := yuv_shared

include $(BUILD_SHARED_LIBRARY)
