#ifndef CCONFIGURATION_H_
#define CCONFIGURATION_H_

#include <string>
#include <vector>
#include "common/log/JniLogger.h"
#include "rapidjson/document.h"
#include "common/log/JniFileLogger.h"

using namespace std;

class CConfiguration {
public:
    CConfiguration();
    ~CConfiguration();

    static CConfiguration* getInstance() {
      if(NULL == pInstance_) pInstance_ = new CConfiguration();
      return pInstance_;
    }

    void destroyInstance() {
      if(pInstance_) {
        delete pInstance_;
        pInstance_ = NULL;
      }
    }

    void loadConfiguration(const char* config_path);
    bool releaseConfiguration();
    
    bool parsePhotoBoxConfig(const char* json_content);

    bool displayParsedConfig();

    const char* getLogType() const { return logType_.c_str(); }

    LogLevel getFrontendLogLevel() const;
    LogLevel getPhotoBoxLogLevel() const;
    LogLevel getGstreamerLogLevel() const;

    string getGstreamerLogThreshold() const { return gstreamerLogThreshold_; }

    int getMaxLogFileSize() const { return maxLogFileSize_; }
    int getMaxLogBackupIndex() const { return maxLogBackupIndex_; } 

    int getResolutionWidth() const { return resolutionWidth_; }
    int getResolutionHeight() const { return resolutionHeight_; }

    int getFps() const { return fps_; }

    int getMonitorInterval() const { return monitorInterval_; }

    string getJpegDecoderType() const { return jpegDecoderType_; }

    int getEffectInterval() const { return effectInterval_; }
    bool getEffectCycling() const { return effectCycling_; }
    const vector<string>& getEffectList() const { return effectList_; }

    bool isMonitorEnabled() const { return isMonitorEnabled_; }
    bool isFullScreenEnabled() const { return isFullScreenEnabled_; }

protected:
    static CConfiguration* pInstance_;

    int maxLogFileSize_ = 0;
    int maxLogBackupIndex_ = 0;
    string logType_ = "";

    string frontendLogLevel_ = "";

    string photoBoxLogLevel_ = "";

    string gstreamerLogLevel_ = "";
    string gstreamerLogThreshold_ = "";

    string effectName_ = "";
    string jpegDecoderType_ = "";

    int resolutionWidth_ = 0;
    int resolutionHeight_ = 0;
    int fps_ = 0;

    bool isMonitorEnabled_ = false;
    bool isFullScreenEnabled_ = false;

    int monitorInterval_ = 0;

    int effectInterval_ = 0;
    bool effectCycling_ = true;
    vector<string> effectList_;

private:
    rapidjson::Document jsonConfig_;
};

#endif // CCONFIGURATION_H_