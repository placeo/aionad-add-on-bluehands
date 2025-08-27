#include "CMonitor.h"
#include "common/configuration/CConfiguration.h"
#include <gst/gst.h>

CMonitor* CMonitor::pInstance_ = NULL;

CMonitor::CMonitor() {

}

CMonitor::~CMonitor() {

}

bool CMonitor::startMonitor() {
    monitorInterval_ = CConfiguration::getInstance()->getMonitorInterval();
    LOGI("monitorInterval: %d", monitorInterval_);
    LOGD("startMonitor");
    if(monitorInterval_ <= 0) {
        LOGE("monitorInterval is not set");
        return false;
    }

    if (isRunning_) {
        LOGI("Monitor is already running.");
        return true;
    }

    isRunning_ = true;
    startTime_ = std::chrono::steady_clock::now();  // 시작 시간 기록
    monitorThread_ = std::thread(&CMonitor::monitorLoop, this);

    return true;
}

bool CMonitor::stopMonitor() {
    LOGD("stopMonitor");
    if (isRunning_) {
        isRunning_ = false;
        if (monitorThread_.joinable()) {
            monitorThread_.join();
        }
    }
    return true;
}

void CMonitor::increasePipelineFrameCount() {
    pipelineFrameCount_++;
}

void CMonitor::increaseCameraBytes(int bytes) {
    cameraBytes_ += bytes;
}

void CMonitor::increasePipelineBytes(int bytes) {
    pipelineBytes_ += bytes;
}

void CMonitor::monitorLoop() {
    while (isRunning_) {
        std::this_thread::sleep_for(std::chrono::seconds(monitorInterval_));

        if (!isRunning_) {
            break;
        }

        std::lock_guard<std::mutex> lock(monitorMutex_);
        if (monitorInterval_ > 0) {
            pipelineFps_ = pipelineFrameCount_ / monitorInterval_;
            cameraBitrateBps_ = (cameraBytes_ * 8) / monitorInterval_;
            pipelineBitrateBps_ = (pipelineBytes_ * 8) / monitorInterval_;
        }
        pipelineFrameCount_ = 0;
        cameraBytes_ = 0;
        pipelineBytes_ = 0;

        // CPU와 메모리 사용률 계산
        cpuUsagePercent_ = calculateProcessCpuUsage();
        memoryUsagePercent_ = calculateMemoryUsage();

        LOGI("================================================");
        LOGI("SYSTEM MONITOR");
        std::string runningTime = formatRunningTime();
        LOGI("running time: %s", runningTime.c_str());
        LOGI("effect: %s", currentEffectName_.c_str());
        LOGI("pipeline fps: %d", pipelineFps_);
        double cameraMbps = static_cast<double>(cameraBitrateBps_) / 1000000.0;
        double pipelineMbps = static_cast<double>(pipelineBitrateBps_) / 1000000.0;
        LOGI("camera bitrate: %.2f Mbps", cameraMbps);
        LOGI("pipeline bitrate: %.2f Mbps", pipelineMbps);
        LOGI("app cpu usage: %.1f%%", cpuUsagePercent_);
        LOGI("app memory usage: %.1f%%", memoryUsagePercent_);

        if (CConfiguration::getInstance()->isMonitorEnabled() && textOverlay_) {
            char text[512];
            snprintf(text, sizeof(text), "Running time: %s\nEffect: %s\n\nPipeline framerate: %d fps\nPipeline bitrate: %.2f Mbps\nCamera bitrate: %.2f Mbps\nApp cpu usage: %.1f %% / %d %%\nApp memory usage: %.1f %%", 
                     runningTime.c_str(), currentEffectName_.c_str(), pipelineFps_, pipelineMbps, cameraMbps, cpuUsagePercent_, getCpuCores() * 100, memoryUsagePercent_);
            g_object_set(textOverlay_, "text", text, NULL);
        }
        
        LOGI("================================================");
    }
}

double CMonitor::calculateProcessCpuUsage() {
    // /proc/self/stat에서 현재 프로세스의 CPU 시간 정보 읽기
    std::ifstream statFile("/proc/self/stat");
    if (!statFile.is_open()) {
        LOGE("Failed to open /proc/self/stat");
        return 0.0;
    }

    std::string line;
    if (!std::getline(statFile, line)) {
        LOGE("Failed to read /proc/self/stat");
        return 0.0;
    }

    // /proc/self/stat 형식: pid comm state ppid pgrp session tty_nr tpgid flags minflt cminflt majflt cmajflt utime stime cutime cstime priority nice num_threads itrealvalue starttime vsize rss
    std::istringstream iss(line);
    std::string pid, comm, state, ppid, pgrp, session, tty_nr, tpgid, flags;
    std::string minflt, cminflt, majflt, cmajflt, utime, stime, cutime, cstime;
    
    // 필요한 값들까지 파싱
    iss >> pid >> comm >> state >> ppid >> pgrp >> session >> tty_nr >> tpgid >> flags;
    iss >> minflt >> cminflt >> majflt >> cmajflt >> utime >> stime >> cutime >> cstime;
    
    long long processCpuTime = std::stoll(utime) + std::stoll(stime) + std::stoll(cutime) + std::stoll(cstime);
    
    // 첫 번째 측정이면 이전 값 저장하고 0 반환
    if (prevProcessCpuTime_ == 0) {
        prevProcessCpuTime_ = processCpuTime;
        return 0.0;
    }
    
    long long cpuDiff = processCpuTime - prevProcessCpuTime_;
    prevProcessCpuTime_ = processCpuTime;
    
    // CPU 사용률을 시간 기반으로 계산 (모니터링 간격 대비)
    double cpuUsage = 0.0;

    // CPU 시간은 clock ticks 단위이므로 HZ(보통 100)로 나누어 초 단위로 변환
    double cpuTimeSeconds = (double)cpuDiff / 100.0;
    cpuUsage = (cpuTimeSeconds / monitorInterval_) * 100.0;
    
    return cpuUsage;
}

int CMonitor::getCpuCores() {
    // sysconf를 사용하여 온라인 CPU 코어 수 확인
    int cores = sysconf(_SC_NPROCESSORS_ONLN);
    if (cores <= 0) {
        // sysconf 실패 시 /proc/cpuinfo에서 확인
        std::ifstream cpuinfoFile("/proc/cpuinfo");
        if (!cpuinfoFile.is_open()) {
            return 1; // 기본값
        }

        cores = 0;
        std::string line;
        while (std::getline(cpuinfoFile, line)) {
            if (line.find("processor") == 0) {
                cores++;
            }
        }
    }
    
    return cores > 0 ? cores : 1;
}

double CMonitor::calculateMemoryUsage() {
    // top과 동일한 방식: 현재 프로세스의 메모리 사용률 계산
    // /proc/self/status에서 VmRSS (Resident Set Size) 읽기
    std::ifstream statusFile("/proc/self/status");
    if (!statusFile.is_open()) {
        LOGE("Failed to open /proc/self/status");
        return 0.0;
    }

    long long vmRss = 0;
    long long memTotal = 0;
    
    std::string line;
    while (std::getline(statusFile, line)) {
        if (line.find("VmRSS:") == 0) {
            std::istringstream iss(line);
            std::string key;
            long long value;
            std::string unit;
            
            if (iss >> key >> value >> unit) {
                vmRss = value; // KB 단위
            }
        }
    }
    
    // 전체 메모리 정보 읽기
    std::ifstream meminfoFile("/proc/meminfo");
    if (meminfoFile.is_open()) {
        std::string line;
        while (std::getline(meminfoFile, line)) {
            std::istringstream iss(line);
            std::string key;
            long long value;
            std::string unit;
            
            if (iss >> key >> value >> unit) {
                if (key == "MemTotal:") {
                    memTotal = value;
                    break;
                }
            }
        }
    }
    
    if (memTotal == 0) {
        LOGE("Failed to read memory information");
        return 0.0;
    }
    
    // top과 동일한 방식: 현재 프로세스의 메모리 사용률
    double memoryUsage = ((double)vmRss / memTotal) * 100.0;
    
    return memoryUsage;
}

std::string CMonitor::formatRunningTime() {
    auto currentTime = std::chrono::steady_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::seconds>(currentTime - startTime_);
    
    int totalSeconds = duration.count();
    int hours = totalSeconds / 3600;
    int minutes = (totalSeconds % 3600) / 60;
    int seconds = totalSeconds % 60;
    
    char timeStr[16];
    snprintf(timeStr, sizeof(timeStr), "%02d:%02d:%02d", hours, minutes, seconds);
    
    return std::string(timeStr);
}