/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: jniLogger.h
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#ifndef UTILBASE_H_
#define UTILBASE_H_

#include <jni.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif
#include <unistd.h>
#include <libgen.h>
#ifdef __cplusplus
#include <string>
#include "common/configuration/CConfiguration.h" // Include CConfiguration header
#endif
#include "common/localdefines.h"
#include "JniFileLogger.h"
#include <string.h>

// ANSI color codes
#define ANSI_COLOR_RED           "\e[0;31m"
#define ANSI_COLOR_LIGHT_RED     "\e[1;31m"
#define ANSI_COLOR_GREEN         "\e[0;32m"
#define ANSI_COLOR_LIGHT_GREEN   "\e[1;32m"
#define ANSI_COLOR_ORANGE        "\e[0;33m"
#define ANSI_COLOR_YELLOW        "\e[1;33m"
#define ANSI_COLOR_BLUE          "\e[0;34m"
#define ANSI_COLOR_LIGHT_BLUE    "\e[1;34m"
#define ANSI_COLOR_MAGENTA       "\e[0;35m"
#define ANSI_COLOR_LIGHT_MAGENTA "\e[1;35m"
#define ANSI_COLOR_CYAN          "\e[0;36m"
#define ANSI_COLOR_LIGHT_CYAN    "\e[1;36m"
#define ANSI_COLOR_GRAY          "\e[0;37m"
#define ANSI_COLOR_WHITE         "\e[1;37m"
#define ANSI_COLOR_RESET         "\e[0m"


void setLogType(const char* type);
void setFrontendLogLevel(LogLevel level);
void setPhotoBoxLogLevel(LogLevel level);
void setGstreamerLogLevel(LogLevel level);

#ifdef __cplusplus
extern "C" {
#endif

LogLevel getFrontendLogLevel();
LogLevel getPhotoBoxLogLevel();
LogLevel getGstreamerLogLevel();
const char* getLogType();

#ifdef __cplusplus
}
#endif

#ifdef __cplusplus
LogLevel logLevelFromString(const std::string& levelStr);
#endif


#define		SAFE_FREE(p)				{ if (p) { free((p)); (p) = NULL; } }
#define		SAFE_DELETE(p)				{ if (p) { delete (p); (p) = NULL; } }
#define		SAFE_DELETE_ARRAY(p)		{ if (p) { delete [](p); (p) = NULL; } }
#define		NUM_ARRAY_ELEMENTS(p)		((int) sizeof(p) / sizeof(p[0]))

#if defined(__GNUC__)
// the macro for branch prediction optimaization for gcc(-O2/-O3 required)
#define		CONDITION(cond)				((__builtin_expect((cond)!=0, 0)))
#define		LIKELY(x)					((__builtin_expect(!!(x), 1)))	// x is likely true
#define		UNLIKELY(x)					((__builtin_expect(!!(x), 0)))	// x is likely false
#else
#define		CONDITION(cond)				((cond))
#define		LIKELY(x)					((x))
#define		UNLIKELY(x)					((x))
#endif

// XXX assertはNDEBUGが定義されていたら引数を含めて丸ごと削除されてしまうので
// 関数実行を直接assertの引数にするとその関数はNDEBUGの時に実行されなくなるので注意
#include <assert.h>
#define CHECK(CONDITION) { bool RES = (CONDITION); assert(RES); }
#define CHECK_EQ(X, Y) { bool RES = (X == Y); assert(RES); }
#define CHECK_NE(X, Y) { bool RES = (X != Y); assert(RES); }
#define CHECK_GE(X, Y) { bool RES = (X >= Y); assert(RES); }
#define CHECK_GT(X, Y) { bool RES = (X > Y); assert(RES); }
#define CHECK_LE(X, Y) { bool RES = (X <= Y); assert(RES); }
#define CHECK_LT(X, Y) { bool RES = (X < Y); assert(RES); }

#if defined(__ANDROID__) && !defined(LOG_NDEBUG)

#define FRONTEND_LOGV(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_VERBOSE) { \
            jni_frontend_file_logger_write(LOG_LEVEL_VERBOSE, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)
#define FRONTEND_LOGD(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_DEBUG) { \
            jni_frontend_file_logger_write(LOG_LEVEL_DEBUG, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)
#define FRONTEND_LOGI(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_INFO) { \
            jni_frontend_file_logger_write(LOG_LEVEL_INFO, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)
#define FRONTEND_LOGW(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_WARN) { \
            jni_frontend_file_logger_write(LOG_LEVEL_WARN, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)
#define FRONTEND_LOGE(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_ERROR) { \
            jni_frontend_file_logger_write(LOG_LEVEL_ERROR, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)
#define FRONTEND_LOGF(FMT, ...) \
    do { \
        if (getFrontendLogLevel() <= LOG_LEVEL_FATAL) { \
            jni_frontend_file_logger_write(LOG_LEVEL_FATAL, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
        } \
    } while(0)

#ifdef __cplusplus
#define LOGV(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_VERBOSE) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, ANSI_COLOR_GRAY "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_VERBOSE, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define LOGD(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_DEBUG) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, ANSI_COLOR_LIGHT_GREEN "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_DEBUG, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define LOGI(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_INFO) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_INFO, LOG_TAG, ANSI_COLOR_LIGHT_BLUE "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_INFO, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define LOGW(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_WARN) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG, ANSI_COLOR_YELLOW "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_WARN, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define LOGE(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_ERROR) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, ANSI_COLOR_RED "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_ERROR, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define LOGF(FMT, ...) \
    do { \
        if (getPhotoBoxLogLevel() <= LOG_LEVEL_FATAL) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, ANSI_COLOR_RED "[PHOTO-BOX][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_FATAL, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#else // for C files
// C files cannot access C++ classes, so file logging will be unconditional here.
// To make it conditional, a C-compatible wrapper around CConfiguration would be needed.
	#define LOGV(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_VERBOSE) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_photo_box_file_logger_write(LOG_LEVEL_VERBOSE, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define LOGD(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_DEBUG) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_photo_box_file_logger_write(LOG_LEVEL_DEBUG, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define LOGI(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_INFO) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_photo_box_file_logger_write(LOG_LEVEL_INFO, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define LOGW(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_WARN) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_photo_box_file_logger_write(LOG_LEVEL_WARN, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define LOGE(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_ERROR) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_photo_box_file_logger_write(LOG_LEVEL_ERROR, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define LOGF(FMT, ...) \
		do { \
            if (getPhotoBoxLogLevel() <= LOG_LEVEL_FATAL) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_photo_box_file_logger_write(LOG_LEVEL_FATAL, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
		} while(0)
#endif

#ifdef __cplusplus
#define GSTLOGV(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_VERBOSE) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, ANSI_COLOR_GRAY "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_VERBOSE, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define GSTLOGD(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_DEBUG) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, ANSI_COLOR_LIGHT_GREEN "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_DEBUG, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define GSTLOGI(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_INFO) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_INFO, LOG_TAG, ANSI_COLOR_LIGHT_BLUE "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_INFO, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define GSTLOGW(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_WARN) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_WARN, LOG_TAG, ANSI_COLOR_YELLOW "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_WARN, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define GSTLOGE(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_ERROR) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, ANSI_COLOR_RED "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_ERROR, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#define GSTLOGF(FMT, ...) \
    do { \
        if (getGstreamerLogLevel() <= LOG_LEVEL_FATAL) { \
            if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, ANSI_COLOR_RED "[GSTREAMER][%d*%s:%d]:" ANSI_COLOR_GRAY FMT ANSI_COLOR_RESET, \
                                    gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
            } \
            else if (strncmp(getLogType(), "file", 4) == 0) { \
                jni_gstreamer_file_logger_write(LOG_LEVEL_FATAL, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
        } \
    } while(0)
#else // for C files
// C files cannot access C++ classes, so file logging will be unconditional here.
// To make it conditional, a C-compatible wrapper around CConfiguration would be needed.
	#define GSTLOGV(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_VERBOSE) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_VERBOSE, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define GSTLOGD(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_DEBUG) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_DEBUG, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define GSTLOGI(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_INFO) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_INFO, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define GSTLOGW(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_WARN) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_WARN, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define GSTLOGE(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_ERROR) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_ERROR, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
                } \
            } \
		} while(0)
	#define GSTLOGF(FMT, ...) \
		do { \
            if (getGstreamerLogLevel() <= LOG_LEVEL_FATAL) { \
                if (strncmp(getLogType(), "console", 7) == 0 || strncmp(getLogType(), "both", 4) == 0) { \
                    __android_log_print(ANDROID_LOG_FATAL, LOG_TAG, "[GSTREAMER][%d*%s:%d]:" FMT, gettid(), basename(__FILE__), __LINE__, ## __VA_ARGS__); \
                } \
                else if (strncmp(getLogType(), "file", 4) == 0) { \
                    jni_gstreamer_file_logger_write(LOG_LEVEL_FATAL, LOG_TAG, basename(__FILE__), __LINE__, FMT, ## __VA_ARGS__); \
            } \
		} while(0)
#endif

#else
	#define LOGV(...)
	#define LOGD(...)
	#define LOGI(...)
	#define LOGW(...)
	#define LOGE(...)
	#define LOGF(...)
#endif

#ifndef		LOG_ALWAYS_FATAL_IF
#define		LOG_ALWAYS_FATAL_IF(cond, ...) \
				( (CONDITION(cond)) \
				? ((void)__android_log_assert(#cond, LOG_TAG, ## __VA_ARGS__)) \
				: (void)0 )
#endif

#ifndef		LOG_ALWAYS_FATAL
#define		LOG_ALWAYS_FATAL(...) \
				( ((void)__android_log_assert(NULL, LOG_TAG, ## __VA_ARGS__)) )
#endif

#ifndef		LOG_ASSERT
#define		LOG_ASSERT(cond, ...) LOG_FATAL_IF(!(cond), ## __VA_ARGS__)
#endif

#ifdef LOG_NDEBUG

#ifndef		LOG_FATAL_IF
#define		LOG_FATAL_IF(cond, ...) ((void)0)
#endif
#ifndef		LOG_FATAL
#define		LOG_FATAL(...) ((void)0)
#endif

#else

#ifndef		LOG_FATAL_IF
#define		LOG_FATAL_IF(cond, ...) LOG_ALWAYS_FATAL_IF(cond, ## __VA_ARGS__)
#endif
#ifndef		LOG_FATAL
#define		LOG_FATAL(...) LOG_ALWAYS_FATAL(__VA_ARGS__)
#endif

#endif

#define		RETURN(code,type)	{type RESULT = code; return RESULT;}
#define		RET(code)			{LOGD("end"); return code;}
#define		EXIT()				{LOGD("end"); return;}
#define		PRE_EXIT()			LOGD("end")

#if defined(__ANDROID__) && !defined(LOG_NDEBUG)
#ifdef __cplusplus
#define MARK(FMT, ...) \
	do { \
		if (getLogLevel() <= LOG_LEVEL_INFO) \
			__android_log_print(ANDROID_LOG_INFO, LOG_TAG, ANSI_COLOR_GREEN "[%s:%d:%s]:" FMT ANSI_COLOR_RESET, \
								basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__); \
	} while(0)
#else // for C files
#define MARK(FMT, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s:%d:%s]:" FMT, basename(__FILE__), __LINE__, __FUNCTION__, ## __VA_ARGS__)
#endif
#else
#define		MARK(...)
#endif

#define LITERAL_TO_STRING_INTERNAL(x)    #x
#define LITERAL_TO_STRING(x) LITERAL_TO_STRING_INTERNAL(x)

#define TRESPASS() \
		LOG_ALWAYS_FATAL(                                       \
			__FILE__ ":" LITERAL_TO_STRING(__LINE__)            \
			" Should not be here.");

void setVM(JavaVM *);
JavaVM *getVM();
JNIEnv *getEnv();

#endif /* UTILBASE_H_ */
