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

#ifndef JNIFILELOGGER_H_
#define JNIFILELOGGER_H_

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    LOG_LEVEL_VERBOSE = 2,
    LOG_LEVEL_DEBUG = 3,
    LOG_LEVEL_INFO = 4,
    LOG_LEVEL_WARN = 5,
    LOG_LEVEL_ERROR = 6,
    LOG_LEVEL_FATAL = 7,
    LOG_LEVEL_SILENT = 8
} LogLevel;

/**
 * @brief Initializes the file logger with the specified path.
 * This should be called once, typically from JNI_OnLoad or a specific init function.
 * @param path The absolute path to the log file.
 */
void jni_frontend_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex);
void jni_photo_box_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex);
void jni_gstreamer_file_logger_init(const char *path, const LogLevel level, const int maxFileSize, const int maxBackupIndex);

/**
 * @brief Releases the file logger resources.
 * This should be called once, typically from JNI_OnUnload or a specific release function.
 */
void jni_frontend_file_logger_release();
void jni_photo_box_file_logger_release();
void jni_gstreamer_file_logger_release();

/**
 * @brief Writes a formatted log message to the file.
 * This function is thread-safe.
 * @param level The log level (e.g., LOG_LEVEL_DEBUG).
 * @param tag The log tag.
 * @param file The source file name where the log occurs.
 * @param line The line number where the log occurs.
 * @param fmt The format string for the log message.
 * @param ... Variable arguments for the format string.
 */
void jni_frontend_file_logger_write(
	const LogLevel level,
	const char *tag,
	const char *file,
	const int line,
	const char *fmt, ...);
void jni_photo_box_file_logger_write(
	const LogLevel level,
	const char *tag,
	const char *file,
	const int line,
	const char *fmt, ...);
void jni_gstreamer_file_logger_write(
	const LogLevel level,
	const char *tag,
	const char *file,
	const int line,
	const char *fmt, ...);

#ifdef __cplusplus
}
#endif

#endif /* JNIFILELOGGER_H_ */ 