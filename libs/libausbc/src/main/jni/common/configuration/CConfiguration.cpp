#include "CConfiguration.h"
#include "common/log/JniLogger.h"
#include <fstream>
#include <string>

CConfiguration* CConfiguration::pInstance_ = NULL;

CConfiguration::CConfiguration() {
}

CConfiguration::~CConfiguration() {
}

void CConfiguration::loadConfiguration(const char* config_path) {
    // photo-box.config file dump
    LOGI("config path: %s", config_path);
    std::ifstream configFile(config_path);
    if (!configFile.is_open()) {
        LOGE("Failed to open config file: %s", config_path);
        return;
    }

    std::string content;
    std::string line;
    LOGD("--- photo-box.config content start ---");
    while (std::getline(configFile, line)) {
        // Windows/DOS 형식의 파일에서 \r (캐리지 리턴)이 남아있을 수 있으므로 제거
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }

        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[PHOTO-BOX] line: %s", line.c_str());

        // '#' 주석 처리
        size_t comment_pos = line.find('#');
        if (comment_pos != std::string::npos) {
            line = line.substr(0, comment_pos);
        }

        content += line;
    }
    LOGD("--- photo-box.config content end ---");
    configFile.close();

    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[PHOTO-BOX] content: %s", content.c_str());

    if (false == parsePhotoBoxConfig(content.c_str())) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "[PHOTO-BOX][%d*%s:%d]:" "Failed to parse photo-box.config", gettid(), basename(__FILE__), __LINE__);
    }
}

// Helper function to safely get a string value from a JSON object
void safeGetString(const rapidjson::Value& obj, const char* key, std::string& out_val) {
    if (obj.HasMember(key) && obj[key].IsString()) {
        out_val = obj[key].GetString();
    }
}

// Helper function to safely get an integer value from a JSON object
void safeGetInt(const rapidjson::Value& obj, const char* key, int& out_val) {
    if (obj.HasMember(key) && obj[key].IsInt()) {
        out_val = obj[key].GetInt();
    }
}

// Helper function to safely get a boolean value from a JSON object
void safeGetBool(const rapidjson::Value& obj, const char* key, bool& out_val) {
    if (obj.HasMember(key) && obj[key].IsBool()) {
        out_val = obj[key].GetBool();
    }
}

bool CConfiguration::parsePhotoBoxConfig(const char* json_content) {
    jsonConfig_.Parse(json_content);

    if (jsonConfig_.HasParseError()) {
        LOGE("JSON parse error: %d", jsonConfig_.GetParseError());
        return false;
    }

    if (!jsonConfig_.IsObject()) {
        LOGE("Root is not a JSON object");
        return false;
    }

    if (jsonConfig_.HasMember("log") && jsonConfig_["log"].IsObject()) {
        const rapidjson::Value& logConfig = jsonConfig_["log"];

        if(logConfig.HasMember("type") && logConfig["type"].IsString()) {
            logType_ = logConfig["type"].GetString();
        }

        if(logConfig.HasMember("maxFileSize") && logConfig["maxFileSize"].IsInt()) {
            maxLogFileSize_ = logConfig["maxFileSize"].GetInt();
        }

        if(logConfig.HasMember("maxBackupIndex") && logConfig["maxBackupIndex"].IsInt()) {
            maxLogBackupIndex_ = logConfig["maxBackupIndex"].GetInt();
        }

        if (logConfig.HasMember("frontend") && logConfig["frontend"].IsObject()) {
            const rapidjson::Value& frontendLog = logConfig["frontend"];
            safeGetString(frontendLog, "level", frontendLogLevel_);
        }

        if (logConfig.HasMember("photo-box") && logConfig["photo-box"].IsObject()) {
            const rapidjson::Value& photoboxLog = logConfig["photo-box"];
            safeGetString(photoboxLog, "level", photoBoxLogLevel_);
        }

        if (logConfig.HasMember("gstreamer") && logConfig["gstreamer"].IsObject()) {
            const rapidjson::Value& gstreamerLog = logConfig["gstreamer"];
            safeGetString(gstreamerLog, "level", gstreamerLogLevel_);
            safeGetString(gstreamerLog, "threshold", gstreamerLogThreshold_);
        }

        // Set the global log level based on the parsed value
        setFrontendLogLevel(logLevelFromString(frontendLogLevel_));
        setPhotoBoxLogLevel(logLevelFromString(photoBoxLogLevel_));
        setGstreamerLogLevel(logLevelFromString(gstreamerLogLevel_));
        setLogType(logType_.c_str());
    }

    if (jsonConfig_.HasMember("effect") && jsonConfig_["effect"].IsObject()) {
        const rapidjson::Value& effectConfig = jsonConfig_["effect"];
        safeGetInt(effectConfig, "interval", effectInterval_);
        safeGetBool(effectConfig, "cycling", effectCycling_);
        
        // 효과 리스트 파싱
        if (effectConfig.HasMember("effects") && effectConfig["effects"].IsArray()) {
            const rapidjson::Value& effectsArray = effectConfig["effects"];
            effectList_.clear();
            for (rapidjson::SizeType i = 0; i < effectsArray.Size(); i++) {
                if (effectsArray[i].IsString()) {
                    effectList_.push_back(effectsArray[i].GetString());
                }
            }
            LOGI("Loaded %zu effects from configuration", effectList_.size());
        }
    }

    if (jsonConfig_.HasMember("jpegDecoder") && jsonConfig_["jpegDecoder"].IsObject()) {
        const rapidjson::Value& jpegDecoder = jsonConfig_["jpegDecoder"];
        safeGetString(jpegDecoder, "type", jpegDecoderType_);
    }

    if(jsonConfig_.HasMember("resolution") && jsonConfig_["resolution"].IsString()) {
        string resolution = jsonConfig_["resolution"].GetString();
        if(resolution == "480p") {
            resolutionWidth_ = 640;
            resolutionHeight_ = 480;
        } else if(resolution == "720p") {
            resolutionWidth_ = 1280;
            resolutionHeight_ = 720;
        } else if(resolution == "1080p") {
            resolutionWidth_ = 1920;
            resolutionHeight_ = 1080;
        } else {
            LOGE("Invalid resolution: %s", resolution.c_str());
            return false;
        }
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Resolution parsed in C++: width=%d, height=%d", resolutionWidth_, resolutionHeight_);
    }

    if(jsonConfig_.HasMember("fps") && jsonConfig_["fps"].IsInt()) {
        fps_ = jsonConfig_["fps"].GetInt();
    }

    if (jsonConfig_.HasMember("monitor") && jsonConfig_["monitor"].IsObject()) {
        const rapidjson::Value& monitorConfig = jsonConfig_["monitor"];
        safeGetInt(monitorConfig, "interval", monitorInterval_);
        safeGetBool(monitorConfig, "enable", isMonitorEnabled_);
    }

    if (jsonConfig_.HasMember("fullScreen") && jsonConfig_["fullScreen"].IsObject()) {
        const rapidjson::Value& fullScreenConfig = jsonConfig_["fullScreen"];
        safeGetBool(fullScreenConfig, "enable", isFullScreenEnabled_);
    }

    LOGI("Successfully parsed photo-box.config");
    return true;
}

bool CConfiguration::displayParsedConfig() {
    LOGI("log - LogType_: %s, MaxFileSize_: %d, MaxBackupIndex_: %d", logType_.c_str(), maxLogFileSize_, maxLogBackupIndex_);
    LOGI("frontend - LogLevel_: %s", frontendLogLevel_.c_str());
    LOGI("photoBox - LogLevel_: %s", photoBoxLogLevel_.c_str());
    LOGI("gstreamer - LogLevel_: %s, Threshold_: %s", gstreamerLogLevel_.c_str(), gstreamerLogThreshold_.c_str());
    LOGI("effect interval: %d, cycling: %s", effectInterval_, effectCycling_ ? "true" : "false");
    LOGI("resolution - width: %d, height: %d", resolutionWidth_, resolutionHeight_);
    LOGI("fps: %d", fps_);
    LOGI("jpeg decoder type: %s", jpegDecoderType_.c_str());
    LOGI("monitor - interval: %d", monitorInterval_);   
    LOGI("fullScreen - enable: %s", isFullScreenEnabled_ ? "true" : "false");
    return true;    
}

bool CConfiguration::releaseConfiguration() {
    return true;
}

LogLevel CConfiguration::getPhotoBoxLogLevel() const {
    return logLevelFromString(photoBoxLogLevel_);
}

LogLevel CConfiguration::getGstreamerLogLevel() const {
    return logLevelFromString(gstreamerLogLevel_);
}

LogLevel CConfiguration::getFrontendLogLevel() const {
    return logLevelFromString(frontendLogLevel_);
}