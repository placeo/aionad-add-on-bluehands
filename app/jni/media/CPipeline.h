#pragma once

#include <gst/gst.h>
#include <chrono>
#include <map>
#include <vector>
#include <string>
#include <atomic>
#include <thread>
#include "CHardwareMjpegDecoder.h"

using namespace std;

class CPipeline {
public:
  CPipeline();
  ~CPipeline();

  gboolean generatePipeline();
  gboolean terminatePipeline();

  GstElement* getPipelineElement() {
      return pipelineElement_;
  }

    GstElement* getVideoSink() {
    return videoSink_;
  }
  


  void setVideoSink(GstElement* videoSink) {
      videoSink_ = videoSink;
  }
  
  GstElement* getQueue() {
      return queue_;
  }

  gboolean connectCameraPipelineStreamProbingPad();
  gboolean disconnectCameraPipelineStreamProbingPad();

  static GstPadProbeReturn queueSrcPadProbeCallback(GstPad *pad, GstPadProbeInfo *info, gpointer data);
  static GstPadProbeReturn mjpegDataProbeCallback(GstPad *pad, GstPadProbeInfo *info, gpointer data);
  static gboolean displayBusCallback(GstBus* bus, GstMessage* message, gpointer userData); 

  // 하드웨어 MJPEG 디코더 관련 함수
  bool tryUseHardwareDecoder();
  void releaseHardwareDecoder();

  // 효과 순환 관련 함수
  void startEffectCycling();
  void stopEffectCycling();
  void switchToNextEffect();
  void applyCurrentEffect();
  
  // 효과 관리 헬퍼 함수
  void initializeEffectElements();
  GstElement* getEffectElement(const string& effectName);
  bool isValidEffect(const string& effectName);

protected:
  GstElement* pipelineElement_ = nullptr;
  GstElement* jpegdec_ = nullptr;
  GstElement* queue_ = nullptr;
  GstElement* secondQueue_ = nullptr;
  GstElement* thirdQueue_ = nullptr;
  GstElement* videoconvert_ = nullptr;
  GstElement* videoConvertFirst_ = nullptr;

  GstElement* glUpload_ = nullptr;

  GstElement* warptv_ = nullptr;
  GstElement* agingtv_ = nullptr;
  GstElement* edgetv_ = nullptr;
  GstElement* dicetv_ = nullptr;
  GstElement* optv_ = nullptr;
  GstElement* quarktv_ = nullptr;
  GstElement* radioactv_ = nullptr;
  GstElement* revtv_ = nullptr;
  GstElement* rippletv_ = nullptr;
  GstElement* shagadelictv_ = nullptr;
  GstElement* streaktv_ = nullptr;
  GstElement* vertigotv_ = nullptr;

  GstElement* identity_ = nullptr;
  GstElement* solarize_ = nullptr;
  GstElement* mirror_ = nullptr;
  GstElement* square_ = nullptr;

  GstElement* glEffectsXray_ = nullptr;
  GstElement* glEffectsFisheye_ = nullptr;
  GstElement* glEffectsHeat_ = nullptr;

  GstElement* videoConvertSecond_ = nullptr;
  GstElement* videoSink_ = nullptr;
  GstElement* glImageSink_ = nullptr;
  GstElement* textOverlay_ = nullptr;

  GstPad* queueSrcPad_ = nullptr;
	gulong queueSrcPadId_ = 0;

  GstPad* videoSinkSinkPad_ = nullptr;
	gulong videoSinkSinkPadId_ = 0;

  // 하드웨어 MJPEG 디코더
  CHardwareMjpegDecoder* hardwareDecoder_ = nullptr;

  // 성능 측정 (mjpegDataProbeCallback에서 처리)
  int64_t totalFramesReceived_ = 0;
  int64_t totalBytesReceived_ = 0;
  int64_t lastFrameTimestampUs_ = 0;

  // 효과 순환 관련 변수
  int currentEffectIndex_ = 0;
  string currentEffectName_ = "none";
  GstElement* currentEffectElement_ = nullptr;
  map<string, GstElement*> effectElementsMap_;
  vector<string> effectList_;
  
  // 타이머 관련
  std::chrono::steady_clock::time_point lastEffectChangeTime_;
  bool effectCyclingEnabled_ = false;
  
  // 효과 전환 동기화 관련
  std::atomic<bool> isTransitioning_{false};
  std::atomic<bool> padBlockingComplete_{false};
  
  // 스트림 독립적 이펙트 타이머 스레드
  std::thread effectTimerThread_;
  std::atomic<bool> effectTimerRunning_{false};
  void effectTimerThreadFunction();
};