#include "CPipeline.h"
#include <gst/app/gstappsink.h>
#include <gst/app/gstappsrc.h>
#include <sys/time.h>
#include <inttypes.h>
#include <unistd.h>
#include "UVCCamera/gstreamer/CGstStreamSource.h"
#include "common/log/JniLogger.h"
#include "common/monitor/CMonitor.h"
#include "common/configuration/CConfiguration.h"
#include "CCustomData.h"

// 시간 측정 유틸리티 함수
static int64_t getCurrentTimeUs() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return static_cast<int64_t>(tv.tv_sec) * 1000000LL + tv.tv_usec;
}

CPipeline::CPipeline() : hardwareDecoder_(nullptr), currentEffectIndex_(0), currentEffectElement_(nullptr), effectCyclingEnabled_(false) {
    // 효과 맵 초기화
    effectElementsMap_.clear();
    
    // 설정에서 효과 리스트 가져오기
    effectList_ = CConfiguration::getInstance()->getEffectList();
    CMonitor::getInstance()->setCurrentEffectName(effectList_[0]);
    if (!effectList_.empty()) {
        currentEffectName_ = effectList_[0];
    } else {
        currentEffectName_ = "radioactv";  // 기본값
    }
}

CPipeline::~CPipeline() {
    // 타이머 스레드 정리
    stopEffectCycling();
    releaseHardwareDecoder();
}

gboolean CPipeline::generatePipeline() {
    LOGD("%s Start", __FUNCTION__);
    pipelineElement_ = gst_pipeline_new("usb-camera-pipeline");

    CGstStreamSource* gstStreamSource = CGstStreamSource::getInstance();
    if (FALSE == gstStreamSource->initializeStreamSource("UsbCamera", "")) {
        LOGE("Failed to initialize stream source");
        return FALSE;
    }

    // 하드웨어 디코더 시도, 실패 시 소프트웨어 디코더 사용
    if (tryUseHardwareDecoder()) {
        LOGI("Using hardware MJPEG decoder");
        jpegdec_ = nullptr;
    } else {
        LOGI("Using software MJPEG decoder");
        jpegdec_ = gst_element_factory_make("jpegdec", "jpegdec");
    }

    // appsrc caps는 CGstStreamSource에서 이미 MJPEG으로 설정됨 - 중복 설정 불필요
    LOGI("AppSrc caps already configured as image/jpeg in CGstStreamSource");

    queue_ = gst_element_factory_make("queue", "queue");
    g_object_set(G_OBJECT(queue_),
                 "max-size-buffers", 1,
                 "leaky", 1,  // 1 = downstream (drop oldest buffer)
                 NULL);

    secondQueue_ = gst_element_factory_make("queue", "secondQueue");
    g_object_set(G_OBJECT(secondQueue_),
                "max-size-buffers", 1,
                "leaky", 1,  // 1 = downstream (drop oldest buffer)
                NULL);

    identity_ = gst_element_factory_make("identity", "identity");
    if (!identity_) {
        LOGE("Failed to create identity");
        return FALSE;
    }

    solarize_ = gst_element_factory_make("solarize", "solarize");
    if (!solarize_) {
        LOGE("Failed to create gleffects");
        return FALSE;
    }

    mirror_ = gst_element_factory_make("mirror", "mirror");
    if (!mirror_) {
        LOGE("Failed to create mirror");
        return FALSE;
    }

    radioactv_ = gst_element_factory_make("radioactv", "radioactv");
    if (!radioactv_) {
        LOGE("Failed to create radioactv");
        return FALSE;
    }

    warptv_ = gst_element_factory_make("warptv", "warptv");
    if (!warptv_) {
        LOGE("Failed to create warptv");
        return FALSE;
    }

    square_ = gst_element_factory_make("square", "square");
    if (!square_) {
        LOGE("Failed to create square");
        return FALSE;
    }

    streaktv_ = gst_element_factory_make("streaktv", "streaktv");
    if (!streaktv_) {
        LOGE("Failed to create streaktv");
        return FALSE;
    }

    // 효과 요소들을 map에 저장
    initializeEffectElements();
    
    // 첫 번째 효과를 현재 효과로 설정
    currentEffectElement_ = getEffectElement(currentEffectName_);

    thirdQueue_ = gst_element_factory_make("queue", "thirdQueue");
    g_object_set(G_OBJECT(thirdQueue_),
                "max-size-buffers", 1,
                "leaky", 1,  // 1 = downstream (drop oldest buffer)
                NULL);

    videoConvertSecond_ = gst_element_factory_make("videoconvert", "videoconvert2");
    if (!videoConvertSecond_) {
        LOGE("Failed to create videoconvert");
        return FALSE;
    }

    // videoconvert와 glimagesink를 사용하여 직접 display 처리
    videoconvert_ = gst_element_factory_make("videoconvert", "videoconvert");
    if (!videoconvert_) {
        LOGE("Failed to create videoconvert");
        return FALSE;
    }

    if (CConfiguration::getInstance()->getJpegDecoderType() == "sw") {
        jpegdec_ = gst_element_factory_make("jpegdec", "jpegdec");
    }
    
    textOverlay_ = gst_element_factory_make("textoverlay", "textoverlay");
    g_object_set(textOverlay_, "text", "", "valignment", 2, "halignment", 0, "line-alignment", 0, "font-desc", "Sans 8", nullptr);
    CMonitor::getInstance()->setTextOverlay(textOverlay_);

    // glimagesink 생성 (하드웨어 가속 GL 렌더링)
    videoSink_ = gst_element_factory_make("glimagesink", "videosink");
    if (!videoSink_) {
        LOGI("glimagesink not available, trying autovideosink");
        videoSink_ = gst_element_factory_make("autovideosink", "videosink");
        if (!videoSink_) {
            LOGE("Failed to create videosink");
            return FALSE;
        }
        LOGI("Using autovideosink for main pipeline");
    } else {
        LOGI("Using glimagesink for main pipeline");
    }
    
    g_object_set(videoSink_,
                 "sync", FALSE,  // 동기화 비활성화
                 "async-handling", TRUE,
                 "force-aspect-ratio", TRUE,  // 종횡비 유지
                 "qos", FALSE,  // QoS 비활성화
                 nullptr);
    
    // GL 관련 환경 변수 설정 (EGL 사용 강제)
    g_setenv("GST_GL_API", "gles2", TRUE);
    g_setenv("GST_GL_PLATFORM", "egl", TRUE);
    LOGI("GL environment configured for Android EGL/GLES2 with main pipeline");

    // for test
    // videoSink_ = gst_element_factory_make("fakesink", "videosink");
    // g_object_set(videoSink_, "sync", FALSE, "async", TRUE, nullptr);

    // 각 element 생성 상태를 개별적으로 체크
    if (!pipelineElement_) {
        LOGE("Failed to create pipeline element");
        return FALSE;
    }
    if (!gstStreamSource->getAppSrc()) {
        LOGE("Failed to create appsrc element");
        return FALSE;
    }
    if (!queue_) {
        LOGE("Failed to create queue element");
        return FALSE;
    }
    if (!videoconvert_) {
        LOGE("Failed to create videoconvert element");
        return FALSE;
    }
    if (!textOverlay_) {
        LOGE("Failed to create textoverlay element");
        return FALSE;
    }
    if (!videoSink_) {
        LOGE("Failed to create videosink element");
        return FALSE;
    }
    
    // jpegdec는 소프트웨어 디코더 사용 시에만 필요
    if (CConfiguration::getInstance()->getJpegDecoderType() == "sw" && !jpegdec_) {
        LOGE("Failed to create jpegdec element (software decoder)");
        return FALSE;
    }
    
    LOGI("All pipeline elements created successfully");

    if (CConfiguration::getInstance()->getJpegDecoderType() == "hw") {
        // 하드웨어 디코더 사용 시: NV12 caps를 미리 설정한 static pipeline 구성
        // appsrc -> queue -> [mjpegDataProbeCallback에서 NV12 변환] -> videoconvert -> videosink
        
        // queue와 videoconvert 사이에 NV12 caps 설정
        GstElement* capsfilter = gst_element_factory_make("capsfilter", "nv12-capsfilter");
        if (!capsfilter) {
            LOGE("Failed to create NV12 capsfilter");
            return FALSE;
        }
        
        // NV12 포맷으로 고정 caps 설정
        GstCaps* nv12Caps = gst_caps_new_simple("video/x-raw",
            "format", G_TYPE_STRING, "NV12",
            "width", G_TYPE_INT, CConfiguration::getInstance()->getResolutionWidth(),
            "height", G_TYPE_INT, CConfiguration::getInstance()->getResolutionHeight(),
            "framerate", GST_TYPE_FRACTION, CConfiguration::getInstance()->getFps(), 1,
            nullptr);
        
        g_object_set(capsfilter, "caps", nv12Caps, nullptr);
        gst_caps_unref(nv12Caps);
        
        gst_bin_add_many(GST_BIN(pipelineElement_), gstStreamSource->getAppSrc(), queue_, capsfilter, videoconvert_, textOverlay_, videoSink_, nullptr);
        
        if (!gst_element_link_many(gstStreamSource->getAppSrc(), queue_, capsfilter, videoconvert_, textOverlay_, videoSink_, nullptr)) {
            LOGE("Failed to link hardware decoder pipeline elements");
            return FALSE;
        }
        LOGI("Hardware decoder pipeline: appsrc -> queue -> capsfilter(NV12) -> videoconvert -> textoverlay -> videosink");
    } else if (CConfiguration::getInstance()->getJpegDecoderType() == "sw") {
        // 소프트웨어 디코더 사용 시: appsrc -> queue -> jpegdec -> videoconvert -> textoverlay -> videosink  
        // 모든 효과 요소들을 bin에 추가 (하지만 연결은 하나만)
        gst_bin_add_many(GST_BIN(pipelineElement_), gstStreamSource->getAppSrc(), queue_, jpegdec_, videoconvert_, secondQueue_, 
                         identity_, radioactv_, streaktv_, mirror_, warptv_, square_, solarize_, thirdQueue_, textOverlay_, videoSink_, nullptr);
        
        // 초기 효과로 파이프라인 연결
        if (!gst_element_link_many(gstStreamSource->getAppSrc(), queue_, jpegdec_, videoconvert_, secondQueue_, currentEffectElement_, thirdQueue_, textOverlay_, videoSink_, nullptr)) {
            LOGE("Failed to link software decoder pipeline elements");
            return FALSE;
        }
        LOGI("Software decoder pipeline: appsrc -> queue -> jpegdec -> videoconvert -> queue -> %s -> queue -> textoverlay -> videosink", currentEffectName_.c_str());
    } else {
        LOGE("Invalid jpeg decoder type");
        return FALSE;
    }

    // 효과 순환 시작
    startEffectCycling();

    LOGD("%s End", __FUNCTION__);
    return TRUE;
}

gboolean CPipeline::terminatePipeline() {
    stopEffectCycling();
    gst_object_unref(GST_OBJECT((GstElement*)pipelineElement_));
	pipelineElement_ = nullptr;
	return TRUE;
}

gboolean CPipeline::connectCameraPipelineStreamProbingPad() {
    LOGD("%s start", __FUNCTION__);
    videoSinkSinkPad_ = gst_element_get_static_pad(videoSink_, "sink");
    if (videoSinkSinkPad_ == nullptr) {
        LOGE("Failed to get video sink pad");
        return FALSE;
    }

    if (CConfiguration::getInstance()->getJpegDecoderType() == "hw") {
        // 하드웨어 디코더 사용 시 MJPEG 데이터를 가로채서 하드웨어 디코더로 처리
        videoSinkSinkPadId_ = gst_pad_add_probe(videoSinkSinkPad_, GST_PAD_PROBE_TYPE_BUFFER, CPipeline::mjpegDataProbeCallback, this, nullptr);
    } else {
        // 소프트웨어 디코더 사용 시 기본 probe 사용
        videoSinkSinkPadId_ = gst_pad_add_probe(videoSinkSinkPad_, GST_PAD_PROBE_TYPE_BUFFER, CPipeline::queueSrcPadProbeCallback, this, nullptr);
    }
    
    LOGD("%s end", __FUNCTION__);
    return TRUE;
}

gboolean CPipeline::disconnectCameraPipelineStreamProbingPad() {
    LOGD("%s start", __FUNCTION__);
	if(0 != videoSinkSinkPadId_) gst_pad_remove_probe(videoSinkSinkPad_, videoSinkSinkPadId_);
	if(NULL != videoSinkSinkPad_) gst_object_unref(videoSinkSinkPad_);
    LOGD("%s end", __FUNCTION__);
    return TRUE;
}

GstPadProbeReturn CPipeline::queueSrcPadProbeCallback(GstPad *pad, GstPadProbeInfo *info, gpointer user_data) {
    LOGV("%s start", __FUNCTION__);
    GstBuffer *readBuffer = GST_PAD_PROBE_INFO_BUFFER(info);
    if (readBuffer == nullptr) {
        LOGE("Failed to get readBuffer");
        return GST_PAD_PROBE_OK;
    }

    gsize bufSize = gst_buffer_get_size(readBuffer);
    CMonitor::getInstance()->increasePipelineBytes((int)bufSize);
    CMonitor::getInstance()->increasePipelineFrameCount();

    // 효과 전환은 타이머 스레드에서만 처리 (probe callback에서는 비활성화)

    LOGV("read buffer size: %zu", bufSize);
    LOGV("%s end", __FUNCTION__);
    return GST_PAD_PROBE_OK;
}

GstPadProbeReturn CPipeline::mjpegDataProbeCallback(GstPad *pad, GstPadProbeInfo *info, gpointer user_data) {
    LOGV("%s start", __FUNCTION__);
    CPipeline *pipeline = static_cast<CPipeline *>(user_data);
    
    GstBuffer *buffer = GST_PAD_PROBE_INFO_BUFFER(info);
    if (buffer == nullptr) {
        LOGE("Failed to get buffer");
        return GST_PAD_PROBE_OK;
    }

    gsize bufSize = gst_buffer_get_size(buffer);
    CMonitor::getInstance()->increasePipelineBytes((int)bufSize);
    CMonitor::getInstance()->increasePipelineFrameCount();

    // 효과 전환은 타이머 스레드에서만 처리 (probe callback에서는 비활성화)

    // 하드웨어 디코더가 있다면 MJPEG 데이터를 하드웨어 디코더로 전달
    if (CConfiguration::getInstance()->getJpegDecoderType() == "hw" && pipeline->hardwareDecoder_) {
        LOGV("Processing MJPEG frame through hardware decoder: %zu bytes", bufSize);
        GstMapInfo map;
        if (gst_buffer_map(buffer, &map, GST_MAP_READ)) {
            int64_t timestampUs = GST_BUFFER_PTS(buffer) / 1000; // nano to micro seconds
            
            // 하드웨어 디코더로 MJPEG 데이터 디코딩
            bool success = pipeline->hardwareDecoder_->decode(map.data, map.size, timestampUs);
            if (success) {
                // 디코딩된 프레임 데이터 가져오기
                uint8_t* outputData = nullptr;
                size_t outputSize = 0;
                int64_t outputTimestampUs = 0;
                
                if (pipeline->hardwareDecoder_->getDecodedFrame(&outputData, &outputSize, &outputTimestampUs)) {
                    // 하드웨어 디코더의 실제 출력 포맷 확인
                    int hwWidth = 0, hwHeight = 0, hwColorFormat = 0;
                    const char* gstFormat = "NV12";  // 기본값을 NV12로 설정
                    
                    if (pipeline->hardwareDecoder_->getOutputFormat(&hwWidth, &hwHeight, &hwColorFormat)) {
                        // Android color format을 GStreamer format으로 변환
                        switch (hwColorFormat) {
                            case 19: // COLOR_FormatYUV420Planar (I420)
                                gstFormat = "I420";
                                break;
                            case 21: // COLOR_FormatYUV420SemiPlanar (NV12)
                                gstFormat = "NV12";
                                break;
                            case 20: // COLOR_FormatYUV420PackedPlanar (YV12)
                                gstFormat = "YV12";
                                break;
                            default:
                                LOGW("Unknown color format %d, using NV12", hwColorFormat);
                                gstFormat = "NV12";
                                break;
                        }
                    } else {
                        LOGW("Could not get hardware decoder output format, using 720p NV12");
                        hwWidth = 1280;
                        hwHeight = 720;
                    }
                    
                    // NV12 버퍼 크기 검증 (720p 기준)
                    size_t expectedNV12Size = hwWidth * hwHeight * 3 / 2;  // NV12 = 1.5 bytes per pixel
                    if (outputSize != expectedNV12Size) {
                        LOGE("Invalid decoded buffer size: %zu bytes (expected: %zu for %dx%d %s) - DROPPING FRAME", 
                             outputSize, expectedNV12Size, hwWidth, hwHeight, gstFormat);
                        // 잘못된 크기의 frame은 drop - 하드웨어 디코더 출력 버퍼 해제
                        pipeline->hardwareDecoder_->releaseOutputBuffer();
                        // 원본 MJPEG 버퍼도 drop하여 malformed frame이 downstream으로 가지 않도록 함
                        return GST_PAD_PROBE_DROP;
                    }
                    
                    // Static pipeline에서 NV12를 요구하므로, 다른 포맷이면 경고하고 NV12로 처리
                    // videoconvert가 자동으로 포맷 변환을 처리할 것임
                    if (strcmp(gstFormat, "NV12") != 0) {
                        LOGW("Hardware decoder outputs %s format, but static pipeline expects NV12. videoconvert will handle conversion.", gstFormat);
                        // Static pipeline의 capsfilter가 NV12를 강제하므로 실제로는 NV12로 변환됨
                    } else {
                        LOGV("Hardware decoder outputs NV12 format - perfect match for static pipeline");
                    }
                    
                    // 새로운 GstBuffer 생성 (디코딩된 YUV 데이터)
                    GstBuffer* decodedBuffer = gst_buffer_new_allocate(nullptr, outputSize, nullptr);
                    if (decodedBuffer) {
                        GstMapInfo decodedMap;
                        if (gst_buffer_map(decodedBuffer, &decodedMap, GST_MAP_WRITE)) {
                            memcpy(decodedMap.data, outputData, outputSize);
                            gst_buffer_unmap(decodedBuffer, &decodedMap);
                            
                            // 타임스탬프 설정
                            GST_BUFFER_PTS(decodedBuffer) = outputTimestampUs * 1000; // micro to nano seconds
                            GST_BUFFER_DTS(decodedBuffer) = GST_BUFFER_PTS(decodedBuffer);
                            GST_BUFFER_DURATION(decodedBuffer) = GST_BUFFER_DURATION(buffer);
                            
                            // 기존 버퍼를 디코딩된 버퍼로 교체
                            info->data = (gpointer)decodedBuffer;
                            gst_buffer_unref(buffer);
                            
                            LOGV("Hardware decoder output verified: %dx%d, format: %s (color format: %d), size: %zu bytes", 
                                 hwWidth, hwHeight, gstFormat, hwColorFormat, outputSize);
                            
                            // Static pipeline에서 이미 NV12 caps가 설정되어 있으므로 별도 caps 이벤트 불필요
                            // capsfilter가 NV12 포맷을 강제하여 안정적이고 예측 가능한 포맷 변환 보장
                            
                            LOGV("Hardware decoded frame: %zu bytes, will be processed through static NV12 pipeline", outputSize);
                        } else {
                            LOGE("Failed to map decoded buffer - DROPPING FRAME");
                            gst_buffer_unref(decodedBuffer);
                            pipeline->hardwareDecoder_->releaseOutputBuffer();
                            return GST_PAD_PROBE_DROP;
                        }
                    } else {
                        LOGE("Failed to create decoded buffer - DROPPING FRAME");
                        pipeline->hardwareDecoder_->releaseOutputBuffer();
                        return GST_PAD_PROBE_DROP;
                    }
                    
                    // 출력 버퍼 해제
                    pipeline->hardwareDecoder_->releaseOutputBuffer();
                } else {
                    LOGW("No decoded frame available from hardware decoder - DROPPING FRAME");
                    return GST_PAD_PROBE_DROP;
                }
            } else {
                LOGW("Hardware decoder failed for frame - DROPPING FRAME");
                return GST_PAD_PROBE_DROP;
            }
            
            gst_buffer_unmap(buffer, &map);
        } else {
            LOGE("Failed to map buffer for hardware decoding - DROPPING FRAME");
            return GST_PAD_PROBE_DROP;
        }
        
        // 처리된 버퍼(원본 또는 디코딩된)를 다운스트림으로 전달
        return GST_PAD_PROBE_OK;
    }

    // LOGV("read buffer size: %zu", bufSize);
    LOGV("%s end", __FUNCTION__);
    return GST_PAD_PROBE_OK;
}

// 메인 파이프라인 버스 메시지 콜백
gboolean CPipeline::displayBusCallback(GstBus* bus, GstMessage* message, gpointer userData) {
    CPipeline* pipeline = static_cast<CPipeline*>(userData);
    
    switch (GST_MESSAGE_TYPE(message)) {
        case GST_MESSAGE_ERROR: {
            GError* error;
            gchar* debug;
            gst_message_parse_error(message, &error, &debug);
            LOGE("Pipeline error: %s", error->message);
            if (debug) {
                LOGE("Debug info: %s", debug);
                g_free(debug);
            }
            
            g_error_free(error);
            break;
        }
        case GST_MESSAGE_WARNING: {
            GError* warning;
            gchar* debug;
            gst_message_parse_warning(message, &warning, &debug);
            LOGW("Pipeline warning: %s", warning->message);
            if (debug) {
                LOGW("Debug info: %s", debug);
                g_free(debug);
            }
            g_error_free(warning);
            break;
        }
        case GST_MESSAGE_STATE_CHANGED: {
            // Main pipeline state changes only
            GstState oldState, newState, pendingState;
            gst_message_parse_state_changed(message, &oldState, &newState, &pendingState);
            LOGI("Pipeline state change: %s -> %s", 
                 gst_element_state_get_name(oldState), 
                 gst_element_state_get_name(newState));
            break;
        }
        default:
            break;
    }
    
    return TRUE;
}

bool CPipeline::tryUseHardwareDecoder() {
    LOGD("%s", __FUNCTION__);
    
    hardwareDecoder_ = new CHardwareMjpegDecoder();
    if (!hardwareDecoder_) {
        LOGE("Failed to create hardware decoder");
        return false;
    }
    
    // 실제 카메라 해상도 사용
    int width = CConfiguration::getInstance()->getResolutionWidth();
    int height = CConfiguration::getInstance()->getResolutionHeight();
    
    LOGI("Initializing hardware decoder with resolution %dx%d", width, height);
    if (!hardwareDecoder_->initialize(width, height)) {
        LOGE("Failed to initialize hardware decoder, falling back to software");
        delete hardwareDecoder_;
        hardwareDecoder_ = nullptr;
        return false;
    }
    
    LOGI("Hardware MJPEG decoder initialized successfully");
    return true;
}

void CPipeline::releaseHardwareDecoder() {
    LOGD("%s", __FUNCTION__);
    
    if (hardwareDecoder_) {
        delete hardwareDecoder_;
        hardwareDecoder_ = nullptr;
    }
}

void CPipeline::startEffectCycling() {
    if (CConfiguration::getInstance()->getEffectCycling()) {
        LOGI("Starting effect cycling with interval: %d seconds", CConfiguration::getInstance()->getEffectInterval());
        effectCyclingEnabled_ = true;
        lastEffectChangeTime_ = std::chrono::steady_clock::now();
        
        // 스트림 독립적 타이머 스레드 시작
        if (!effectTimerRunning_.load()) {
            effectTimerRunning_ = true;
            effectTimerThread_ = std::thread(&CPipeline::effectTimerThreadFunction, this);
            LOGI("Effect timer thread started for stream-independent switching");
        }
    } else {
        LOGI("Effect cycling is disabled in configuration");
        effectCyclingEnabled_ = false;
    }
}

void CPipeline::stopEffectCycling() {
    LOGI("Stopping effect cycling");
    effectCyclingEnabled_ = false;
    
    // 타이머 스레드 종료
    if (effectTimerRunning_.load()) {
        effectTimerRunning_ = false;
        if (effectTimerThread_.joinable()) {
            effectTimerThread_.join();
            LOGI("Effect timer thread stopped");
        }
    }
}

void CPipeline::switchToNextEffect() {
    if (!effectCyclingEnabled_) {
        return;
    }

    auto currentTime = std::chrono::steady_clock::now();
    auto elapsedSeconds = std::chrono::duration_cast<std::chrono::seconds>(currentTime - lastEffectChangeTime_).count();
    
    // interval이 경과했는지 확인
    if (elapsedSeconds >= CConfiguration::getInstance()->getEffectInterval()) {
        // 다음 효과로 변경
        string previousEffectName = currentEffectName_;
        int previousEffectIndex = currentEffectIndex_;
        currentEffectIndex_ = (currentEffectIndex_ + 1) % effectList_.size();
        currentEffectName_ = effectList_[currentEffectIndex_];
        
        LOGI("Switching effect from %s to %s (index: %d -> %d)", 
             previousEffectName.c_str(), currentEffectName_.c_str(), 
             previousEffectIndex, currentEffectIndex_);
        applyCurrentEffect();
        lastEffectChangeTime_ = currentTime;
    }
}

void CPipeline::applyCurrentEffect() {
    // 이미 전환 중이면 스킵
    if (isTransitioning_.exchange(true)) {
        LOGW("Effect transition already in progress, skipping switch to %s", currentEffectName_.c_str());
        return;
    }
    
    if (!pipelineElement_ || !currentEffectElement_) {
        LOGE("Pipeline or current effect element is null");
        isTransitioning_ = false;  // 가드 해제
        return;
    }

    // 현재 효과가 유효한지 확인
    if (!isValidEffect(currentEffectName_)) {
        LOGE("Invalid effect name: %s", currentEffectName_.c_str());
        isTransitioning_ = false;  // 가드 해제
        return;
    }

    // 바로 fallback 방식 사용 (pad blocking 건너뛰기)
    LOGI("Using direct fallback method for effect transition to: %s", currentEffectName_.c_str());
    if (CConfiguration::getInstance()->getEffectCycling()) {
        CMonitor::getInstance()->setCurrentEffectName(currentEffectName_);
    }
    
    // VideoAppSource 완전 정지 - 이펙트 전환 중 스트림 차단
    CGstStreamSource::getInstance()->setStreamingAllowedState(FALSE);
    LOGI("Stream blocked for safe effect transition");
    
    // 충분한 대기 시간으로 진행 중인 버퍼 처리 완료
    g_usleep(50000);  // 50ms 대기
    
    // 기존 방식으로 전환 시도
    GstElement* oldEffectElement = currentEffectElement_;
    GstElement* newEffectElement = getEffectElement(currentEffectName_);
    
    if (newEffectElement) {
        LOGI("Starting element unlinking for effect transition");
        
        // 이전 element 연결 해제
        gst_element_unlink(secondQueue_, oldEffectElement);
        gst_element_unlink(oldEffectElement, thirdQueue_);
        
        // 연결 해제 후 추가 대기
        g_usleep(20000);  // 20ms 대기
        
        LOGI("Linking new effect element: %s", currentEffectName_.c_str());
        
        // 새 element 연결
        if (gst_element_link(secondQueue_, newEffectElement) && 
            gst_element_link(newEffectElement, thirdQueue_)) {
            
            currentEffectElement_ = newEffectElement;
            gst_element_sync_state_with_parent(currentEffectElement_);
            
            // 상태 동기화 완료까지 대기
            g_usleep(30000);  // 30ms 대기
            
            LOGI("Fallback transition successful: %s", currentEffectName_.c_str());
        } else {
            // 폴백도 실패 시 이전 상태로 복원
            LOGE("Failed to link new effect element, restoring previous state");
            gst_element_link(secondQueue_, oldEffectElement);
            gst_element_link(oldEffectElement, thirdQueue_);
            LOGE("Fallback transition failed for: %s", currentEffectName_.c_str());
        }
    }
    
    // 이펙트 전환 완료 후 스트림 재개
    g_usleep(20000);  // 최종 안정화 대기 (20ms)
    CGstStreamSource::getInstance()->setStreamingAllowedState(TRUE);
    LOGI("Stream resumed after effect transition completion");
    
    // 전환 완료, 가드 해제
    isTransitioning_ = false;
}

void CPipeline::initializeEffectElements() {
    LOGD("Initializing effect elements map");
    
    effectElementsMap_["none"] = identity_;
    effectElementsMap_["radioac"] = radioactv_;
    effectElementsMap_["mirror"] = mirror_;
    effectElementsMap_["warp"] = warptv_;
    effectElementsMap_["square"] = square_;
    effectElementsMap_["solarize"] = solarize_;
    effectElementsMap_["streak"] = streaktv_;
    
    LOGI("Effect elements map initialized with %zu effects", effectElementsMap_.size());
}

GstElement* CPipeline::getEffectElement(const string& effectName) {
    auto it = effectElementsMap_.find(effectName);
    if (it != effectElementsMap_.end()) {
        return it->second;
    }
    
    LOGE("Effect element not found: %s", effectName.c_str());
    return nullptr;
}

bool CPipeline::isValidEffect(const string& effectName) {
    return effectElementsMap_.find(effectName) != effectElementsMap_.end();
}

// 스트림 독립적 이펙트 타이머 스레드 함수
void CPipeline::effectTimerThreadFunction() {
    LOGI("Effect timer thread started for stream-independent switching");
    
    while (effectTimerRunning_.load()) {
        // 1초마다 체크
        std::this_thread::sleep_for(std::chrono::seconds(1));
        
        if (!effectTimerRunning_.load()) {
            break;
        }
        
        if (effectCyclingEnabled_) {
            auto currentTime = std::chrono::steady_clock::now();
            auto elapsedSeconds = std::chrono::duration_cast<std::chrono::seconds>(currentTime - lastEffectChangeTime_).count();
            
            // interval이 경과했는지 확인
            if (elapsedSeconds >= CConfiguration::getInstance()->getEffectInterval()) {
                // 다음 효과로 변경
                string previousEffectName = currentEffectName_;
                int previousEffectIndex = currentEffectIndex_;
                currentEffectIndex_ = (currentEffectIndex_ + 1) % effectList_.size();
                currentEffectName_ = effectList_[currentEffectIndex_];
                
                LOGI("Timer-based effect switching from %s to %s (index: %d -> %d)", 
                     previousEffectName.c_str(), currentEffectName_.c_str(), 
                     previousEffectIndex, currentEffectIndex_);
                
                applyCurrentEffect();
                lastEffectChangeTime_ = currentTime;
            }
        }
    }
    
    LOGI("Effect timer thread ended");
}





