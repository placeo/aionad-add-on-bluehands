/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: utilbase.cpp
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

#include "common/log/JniLogger.h"

using namespace std;

static JavaVM *savedVm;

void setVM(JavaVM *vm) {
	savedVm = vm;
}

JavaVM *getVM() {
	return savedVm;
}

JNIEnv *getEnv() {
    JNIEnv *env = NULL;
    if (savedVm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
    	env = NULL;
    }
    return env;
}

static LogLevel frontendLogLevel = LOG_LEVEL_INFO; // Default log level
static LogLevel photoBoxLogLevel = LOG_LEVEL_INFO; // Default log level
static LogLevel gstreamerLogLevel = LOG_LEVEL_INFO; // Default log level

char logType_[10] = "console";

void setFrontendLogLevel(LogLevel level) {
    frontendLogLevel = level;
}

LogLevel getFrontendLogLevel() {
    return frontendLogLevel;
}

void setPhotoBoxLogLevel(LogLevel level) {
    photoBoxLogLevel = level;
}

LogLevel getPhotoBoxLogLevel() {
    return photoBoxLogLevel;
}

void setGstreamerLogLevel(LogLevel level) {
    gstreamerLogLevel = level;
}

LogLevel getGstreamerLogLevel() {
    return gstreamerLogLevel;
}

void setLogType(const char* type) {
    strcpy(logType_, type);
}

const char* getLogType() {
    return (const char*) logType_;
}

LogLevel logLevelFromString(const std::string& levelStr) {
    if (levelStr == "verbose") return LOG_LEVEL_VERBOSE;
    else if (levelStr == "debug") return LOG_LEVEL_DEBUG;
    else if (levelStr == "info") return LOG_LEVEL_INFO;
    else if (levelStr == "warn") return LOG_LEVEL_WARN;
    else if (levelStr == "error") return LOG_LEVEL_ERROR;
    else if (levelStr == "fatal") return LOG_LEVEL_FATAL;
    // Default to INFO if the string is unknown
    return LOG_LEVEL_INFO;
}
