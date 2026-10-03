LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := xcertplay_i2c
LOCAL_SRC_FILES := linux_i2c_jni.c
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := local_hotspot_radio
LOCAL_SRC_FILES := local_hotspot_radio.c
# NDK r26's fortified readlinkat inlining breaks against the android-19 platform headers.
LOCAL_CFLAGS := -Wall -Wextra -Werror -U_FORTIFY_SOURCE
include $(BUILD_SHARED_LIBRARY)
