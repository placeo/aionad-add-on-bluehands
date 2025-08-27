/*
 * Copyright (c) 2024 SK Telecom
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
 */

#include "JniFileLogger.h"
#include <stdio.h>
#include <pthread.h>
#include <stdarg.h>
#include <time.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <libgen.h>
#include <spdlog/spdlog.h>
#include <spdlog/sinks/rotating_file_sink.h>
#include <memory>
#include "JniLogger.h"
#include <gst/gst.h>
#include "configuration/CConfiguration.h"

static std::shared_ptr<spdlog::logger> g_frontend_spdlog_logger = nullptr;
static std::shared_ptr<spdlog::logger> g_photo_box_spdlog_logger = nullptr;
static std::shared_ptr<spdlog::logger> g_gstreamer_spdlog_logger = nullptr;

static pthread_mutex_t g_log_mutex = PTHREAD_MUTEX_INITIALIZER;

static void gstreamer_log_to_file_handler(GstDebugCategory *category, GstDebugLevel level,
                                          const gchar *file, const gchar *function, gint line,
                                          GObject *object, GstDebugMessage *message, gpointer user_data);

// Helper to convert LogLevel enum to spdlog level
static spdlog::level::level_enum to_spdlog_level(const LogLevel level) {
    switch (level) {
        case LOG_LEVEL_VERBOSE: return spdlog::level::trace;
        case LOG_LEVEL_DEBUG:   return spdlog::level::debug;
        case LOG_LEVEL_INFO:    return spdlog::level::info;
        case LOG_LEVEL_WARN:    return spdlog::level::warn;
        case LOG_LEVEL_ERROR:   return spdlog::level::err;
        case LOG_LEVEL_FATAL:   return spdlog::level::critical;
        default:                return spdlog::level::info;
    }
}

static LogLevel from_gstreamer_level(const GstDebugLevel level) {
    switch (level) {
        case GST_LEVEL_NONE:
        case GST_LEVEL_ERROR:   return LOG_LEVEL_ERROR;
        case GST_LEVEL_WARNING: return LOG_LEVEL_WARN;
        case GST_LEVEL_FIXME:
        case GST_LEVEL_INFO:    return LOG_LEVEL_INFO;
        case GST_LEVEL_DEBUG:   return LOG_LEVEL_DEBUG;
        case GST_LEVEL_LOG:
        case GST_LEVEL_TRACE:
        case GST_LEVEL_MEMDUMP:
        default:                return LOG_LEVEL_VERBOSE;
    }
}

static const char* get_color_for_level(const LogLevel level) {
    switch (level) {
        case LOG_LEVEL_VERBOSE: return ANSI_COLOR_GRAY;
        case LOG_LEVEL_DEBUG:   return ANSI_COLOR_LIGHT_GREEN;
        case LOG_LEVEL_INFO:    return ANSI_COLOR_LIGHT_BLUE;
        case LOG_LEVEL_WARN:    return ANSI_COLOR_YELLOW;
        case LOG_LEVEL_ERROR:   return ANSI_COLOR_RED;
        case LOG_LEVEL_FATAL:   return ANSI_COLOR_LIGHT_RED;
        default:                return ANSI_COLOR_GRAY;
    }
}

void jni_frontend_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex) {
    pthread_mutex_lock(&g_log_mutex);
    try {
        g_frontend_spdlog_logger = spdlog::rotating_logger_mt("frontend_file_logger", path, maxFileSize, maxBackupIndex);
        g_frontend_spdlog_logger->set_level(to_spdlog_level(level));
        g_frontend_spdlog_logger->set_pattern("%Y-%m-%d %H:%M:%S.%e [%l]: %v");
        g_frontend_spdlog_logger->flush_on(spdlog::level::info);
    } catch (const spdlog::spdlog_ex& ex) { 
        g_frontend_spdlog_logger = nullptr;
    }
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_photo_box_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex) {
    pthread_mutex_lock(&g_log_mutex);
    try {
        g_photo_box_spdlog_logger = spdlog::rotating_logger_mt("photobox_file_logger", path, maxFileSize, maxBackupIndex);
        g_photo_box_spdlog_logger->set_level(to_spdlog_level(level));
        g_photo_box_spdlog_logger->set_pattern("%Y-%m-%d %H:%M:%S.%e [%l] (%s:%#): %v");
        g_photo_box_spdlog_logger->flush_on(spdlog::level::info);
    } catch (const spdlog::spdlog_ex& ex) {
        g_photo_box_spdlog_logger = nullptr;
    }
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_gstreamer_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex) {
    pthread_mutex_lock(&g_log_mutex);
    try {
        g_gstreamer_spdlog_logger = spdlog::rotating_logger_mt("gstreamer_file_logger", path, maxFileSize, maxBackupIndex);
        g_gstreamer_spdlog_logger->set_level(to_spdlog_level(level));
        g_gstreamer_spdlog_logger->set_pattern("%Y-%m-%d %H:%M:%S.%e [%l] (%s:%#): %v");
        g_gstreamer_spdlog_logger->flush_on(spdlog::level::info);

        // 파일 로그 핸들러만 등록
        gst_debug_add_log_function(gstreamer_log_to_file_handler, nullptr, nullptr);
        // GStreamer 디버그 레벨 설정
        gst_debug_set_threshold_from_string(CConfiguration::getInstance()->getGstreamerLogThreshold().c_str(), TRUE);
    } catch (const spdlog::spdlog_ex& ex) {
        g_gstreamer_spdlog_logger = nullptr;
    }
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_frontend_file_logger_release() {
    pthread_mutex_lock(&g_log_mutex);
    if (g_frontend_spdlog_logger) {
        g_frontend_spdlog_logger->flush();
        g_frontend_spdlog_logger.reset();
    }
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_photo_box_file_logger_release() {
    pthread_mutex_lock(&g_log_mutex);
    if (g_photo_box_spdlog_logger) {
        g_photo_box_spdlog_logger->flush();
        g_photo_box_spdlog_logger.reset();
    }
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_gstreamer_file_logger_release() {
    pthread_mutex_lock(&g_log_mutex);
    
        // 파일 로그 핸들러만 제거
    gst_debug_remove_log_function(gstreamer_log_to_file_handler);

    if (g_gstreamer_spdlog_logger) {
        g_gstreamer_spdlog_logger->flush();
        g_gstreamer_spdlog_logger.reset();
    }
    pthread_mutex_unlock(&g_log_mutex);
}

// GStreamer 로그를 파일로 저장하는 핸들러 (원래 기능만 유지)
static void gstreamer_log_to_file_handler(GstDebugCategory *category, GstDebugLevel level,
                                          const gchar *file, const gchar *function, gint line,
                                          GObject *object, GstDebugMessage *message, gpointer user_data)
{
    pthread_mutex_lock(&g_log_mutex);
    
    if (!g_gstreamer_spdlog_logger) {
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }
    
    const LogLevel log_level = from_gstreamer_level(level);
    const char* color_start = get_color_for_level(log_level);
    const gchar* log_message = gst_debug_message_get(message);
    
    // 파일로 로그 저장
    g_gstreamer_spdlog_logger->log(spdlog::source_loc{file, line, ""}, to_spdlog_level(log_level), "{}{}{}", color_start, log_message, ANSI_COLOR_RESET);
    
    pthread_mutex_unlock(&g_log_mutex);
}

void jni_photo_box_file_logger_write(const LogLevel level, const char *tag, const char *file, const int line, const char *fmt, ...) {
    pthread_mutex_lock(&g_log_mutex);
    if (!g_photo_box_spdlog_logger) {
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }

    const char* color_start = get_color_for_level(level);

    char msg_buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(msg_buf, sizeof(msg_buf), fmt, args);
    va_end(args);

    g_photo_box_spdlog_logger->log(spdlog::source_loc{file, line, ""}, to_spdlog_level(level), "{}{}{}", color_start, msg_buf, ANSI_COLOR_RESET);
    pthread_mutex_unlock(&g_log_mutex);
} 

void jni_frontend_file_logger_write(const LogLevel level, const char *tag, const char *file, const int line, const char *fmt, ...) {
    pthread_mutex_lock(&g_log_mutex);
    if (!g_frontend_spdlog_logger) {
        pthread_mutex_unlock(&g_log_mutex);
        return;
    }

    const char* color_start = get_color_for_level(level);

    char msg_buf[1024];
    va_list args;
    va_start(args, fmt);
    vsnprintf(msg_buf, sizeof(msg_buf), fmt, args);
    va_end(args);

    g_frontend_spdlog_logger->log(spdlog::source_loc{file, line, ""}, to_spdlog_level(level), "{}{}{}", color_start, msg_buf, ANSI_COLOR_RESET);
    pthread_mutex_unlock(&g_log_mutex);
}