#ifndef CMONITOR_H_
#define CMONITOR_H_

#include <string>
#include <mutex>
#include <thread>
#include <fstream>
#include <sstream>
#include <unistd.h>
#include <atomic>
#include <chrono>
#include "common/log/JniLogger.h"
#include "rapidjson/document.h"
#include "common/log/JniFileLogger.h"
#include "common/configuration/CConfiguration.h"
#include <gst/gst.h>

using namespace std;

class CMonitor {
public:
    CMonitor();
    ~CMonitor();

    static CMonitor* getInstance() {
        if(NULL == pInstance_) pInstance_ = new CMonitor();
        return pInstance_;
    }

    void destroyInstance() {
        if(pInstance_) {
            delete pInstance_;
            pInstance_ = NULL;
        }
    }

    bool startMonitor();
    bool stopMonitor();

    void increasePipelineFrameCount();
    int getPipelineFps() {
        return pipelineFps_;    
    }
    void increaseCameraBytes(int bytes);
    void increasePipelineBytes(int bytes);
    long long getCameraBitrateBps() { return cameraBitrateBps_; }
    long long getPipelineBitrateBps() { return pipelineBitrateBps_; }
    double getCpuUsagePercent() { return cpuUsagePercent_; }
    double getMemoryUsagePercent() { return memoryUsagePercent_; }

    void setTextOverlay(GstElement* overlay) {
        std::lock_guard<std::mutex> lock(monitorMutex_);
        textOverlay_ = overlay;
    }

    void setCurrentEffectName(const string& effectName) {
        currentEffectName_ = effectName;
    }

private:
    void monitorLoop();
    double calculateCpuUsage();
    double calculateProcessCpuUsage();
    int getCpuCores();
    double calculateMemoryUsage();
    std::string formatRunningTime();

    std::mutex monitorMutex_;
    static CMonitor* pInstance_;
    int monitorInterval_ = 0;
    int pipelineFps_ = 0;
    int pipelineFrameCount_ = 0;
    long long cameraBytes_ = 0;     
    long long pipelineBytes_ = 0;
    long long cameraBitrateBps_ = 0;    // computed bits per second
    long long pipelineBitrateBps_ = 0;    // computed bits per second
    double cpuUsagePercent_ = 0.0;
    double memoryUsagePercent_ = 0.0;
    
    // CPU 계산을 위한 이전 값들
    long long prevTotalCpuTime_ = 0;
    long long prevIdleCpuTime_ = 0;
    long long prevProcessCpuTime_ = 0;

    std::thread monitorThread_;
    std::atomic<bool> isRunning_{false};
    GstElement* textOverlay_ = nullptr;

    string currentEffectName_ = "";
    // Running time tracking
    std::chrono::steady_clock::time_point startTime_;
};

#endif