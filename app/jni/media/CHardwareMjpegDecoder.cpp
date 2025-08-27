#include "CHardwareMjpegDecoder.h"
#include <cstring>

#ifdef __ANDROID__
// Android에서만 실제 MediaCodec 함수들 사용
#else
// IDE 지원을 위한 더미 함수 정의
AMediaCodec* AMediaCodec_createCodecByName(const char* name) { return nullptr; }
void AMediaCodec_delete(AMediaCodec* codec) {}
AMediaFormat* AMediaFormat_new() { return nullptr; }
void AMediaFormat_delete(AMediaFormat* format) {}
void AMediaFormat_setString(AMediaFormat* format, const char* name, const char* value) {}
void AMediaFormat_setInt32(AMediaFormat* format, const char* name, int32_t value) {}
media_status_t AMediaCodec_configure(AMediaCodec* codec, const AMediaFormat* format, ANativeWindow* surface, void* crypto, uint32_t flags) { return AMEDIA_OK; }
media_status_t AMediaCodec_start(AMediaCodec* codec) { return AMEDIA_OK; }
media_status_t AMediaCodec_stop(AMediaCodec* codec) { return AMEDIA_OK; }
ssize_t AMediaCodec_dequeueInputBuffer(AMediaCodec* codec, int64_t timeoutUs) { return -1; }
uint8_t* AMediaCodec_getInputBuffer(AMediaCodec* codec, size_t idx, size_t* out_size) { return nullptr; }
media_status_t AMediaCodec_queueInputBuffer(AMediaCodec* codec, size_t idx, off_t offset, size_t size, uint64_t time, uint32_t flags) { return AMEDIA_OK; }
ssize_t AMediaCodec_dequeueOutputBuffer(AMediaCodec* codec, void* info, int64_t timeoutUs) { return -1; }
media_status_t AMediaCodec_releaseOutputBuffer(AMediaCodec* codec, size_t idx, bool render) { return AMEDIA_OK; }
AMediaFormat* AMediaCodec_getOutputFormat(AMediaCodec* codec) { return nullptr; }
uint8_t* AMediaCodec_getOutputBuffer(AMediaCodec* codec, size_t idx, size_t* out_size) { return nullptr; }
bool AMediaFormat_getInt32(AMediaFormat* format, const char* name, int32_t* out) { return false; }
#endif

const char* CHardwareMjpegDecoder::MJPEG_CODEC_NAME = "OMX.amlogic.mjpeg.decoder.awesome2";  // Amlogic 하드웨어 디코더

#include <sys/time.h>

static int64_t getCurrentTimeUs() {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    return static_cast<int64_t>(tv.tv_sec) * 1000000LL + tv.tv_usec;
}

CHardwareMjpegDecoder::CHardwareMjpegDecoder() 
    : codec_(nullptr), format_(nullptr), initialized_(false),
      currentOutputBufferIndex_(-1), currentOutputBuffer_(nullptr), 
      currentOutputSize_(0), currentTimestampUs_(0),
      outputWidth_(0), outputHeight_(0), outputColorFormat_(0),
      lastInputTimestampUs_(0), lastOutputTimestampUs_(0),
      totalFramesDecoded_(0), totalDecodingTimeUs_(0) {
    LOGD("%s", __FUNCTION__);
}

CHardwareMjpegDecoder::~CHardwareMjpegDecoder() {
    LOGD("%s", __FUNCTION__);
    release();
}

const char* CHardwareMjpegDecoder::findMjpegHardwareDecoder(int width, int height) {
    // 일단 기본 디코더로 시도
    const char* defaultCodecs[] = {
        "OMX.amlogic.mjpeg.decoder.awesome2",  // Amlogic 하드웨어 디코더 (우선 시도)
        "OMX.qcom.video.decoder.mjpeg",        // 퀄컴 하드웨어 디코더
        "OMX.Exynos.MJPEG.Decoder",           // 삼성 하드웨어 디코더
        "OMX.MTK.VIDEO.DECODER.MJPEG",        // 미디어텍 하드웨어 디코더
        "c2.android.mjpeg.decoder",           // AOSP Codec2 디코더
        "OMX.google.mjpeg.decoder"            // 구글 소프트웨어 디코더 (마지막 폴백)
    };

    for (const char* codecName : defaultCodecs) {
        // 코덱 생성 테스트
        AMediaCodec* testCodec = AMediaCodec_createCodecByName(codecName);
        if (testCodec) {
            LOGI("Testing codec: %s", codecName);
            
            // 해상도 지원 여부 확인
            AMediaFormat* testFormat = AMediaFormat_new();
            AMediaFormat_setString(testFormat, AMEDIAFORMAT_KEY_MIME, "video/x-motion-jpeg");
            AMediaFormat_setInt32(testFormat, AMEDIAFORMAT_KEY_WIDTH, width);
            AMediaFormat_setInt32(testFormat, AMEDIAFORMAT_KEY_HEIGHT, height);
            
            media_status_t configStatus = AMediaCodec_configure(testCodec, testFormat, nullptr, nullptr, 0);
            AMediaFormat_delete(testFormat);
            
            if (configStatus == AMEDIA_OK) {
                LOGI("Found working codec: %s", codecName);
                AMediaCodec_delete(testCodec);
                return strdup(codecName);
            }
            
            AMediaCodec_delete(testCodec);
        }
    }
    
    LOGI("No suitable MJPEG decoder found, using default: %s", MJPEG_CODEC_NAME);
    return strdup(MJPEG_CODEC_NAME);
}

bool CHardwareMjpegDecoder::initialize(int width, int height) {
    LOGD("%s: width=%d, height=%d", __FUNCTION__, width, height);
    
    if (initialized_) {
        LOGW("Decoder already initialized");
        return true;
    }
    
    // 적절한 디코더 찾기
    const char* codecName = findMjpegHardwareDecoder(width, height);
    if (!codecName) {
        LOGE("Failed to find decoder");
        return false;
    }
    
    // MediaCodec 생성
    codec_ = AMediaCodec_createCodecByName(codecName);
    free((void*)codecName);
    
    if (!codec_) {
        LOGE("Failed to create codec");
        return false;
    }
    
    // MediaFormat 설정
    format_ = AMediaFormat_new();
    if (!format_) {
        LOGE("Failed to create media format");
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
        return false;
    }
    
    // MJPEG 포맷 설정
    AMediaFormat_setString(format_, AMEDIAFORMAT_KEY_MIME, "video/x-motion-jpeg");
    AMediaFormat_setInt32(format_, AMEDIAFORMAT_KEY_WIDTH, width);
    AMediaFormat_setInt32(format_, AMEDIAFORMAT_KEY_HEIGHT, height);
    
    // 디코더 설정 (Surface 없이)
    media_status_t status = AMediaCodec_configure(codec_, format_, nullptr, nullptr, 0);
    if (status != AMEDIA_OK) {
        LOGE("Failed to configure codec: %d", status);
        handleError("configure", status);
        return false;
    }
    
    // 디코더 시작
    status = AMediaCodec_start(codec_);
    if (status != AMEDIA_OK) {
        LOGE("Failed to start codec: %d", status);
        handleError("start", status);
        return false;
    }
    
    initialized_ = true;
    LOGI("Hardware MJPEG decoder initialized successfully");
    return true;
}

bool CHardwareMjpegDecoder::decode(uint8_t* mjpegData, size_t dataSize, int64_t timestampUs) {
    if (!initialized_ || !mjpegData || dataSize == 0) {
        LOGE("Invalid parameters: initialized=%d, data=%p, size=%zu", 
             initialized_, mjpegData, dataSize);
        return false;
    }
    
    // 디코딩 시작 시간 기록
    int64_t decodeStartUs = getCurrentTimeUs();
    lastInputTimestampUs_ = timestampUs;
    
    // 입력 버퍼 처리
    if (!processInputBuffer(mjpegData, dataSize, timestampUs)) {
        LOGE("Failed to process input buffer");
        return false;
    }
    
    // 출력 버퍼 처리
    if (!processOutputBuffer()) {
        LOGE("Failed to process output buffer");
        return false;
    }
    
    // 디코딩 완료 시간 기록
    int64_t decodeEndUs = getCurrentTimeUs();
    int64_t decodingTimeUs = decodeEndUs - decodeStartUs;
    totalDecodingTimeUs_ += decodingTimeUs;
    totalFramesDecoded_++;
    lastOutputTimestampUs_ = getCurrentTimeUs();
    
    // 매 30프레임마다 성능 로그 출력
    if (totalFramesDecoded_ % 30 == 0) {
        float avgDecodingTimeMs = static_cast<float>(totalDecodingTimeUs_) / totalFramesDecoded_ / 1000.0f;
        float currentFps = 1000000.0f / decodingTimeUs;
        float avgFps = 1000.0f / avgDecodingTimeMs;
        
        LOGV("Decoding performance: current=%.2f ms (%.2f fps), avg=%.2f ms (%.2f fps), total frames=%lld", 
             decodingTimeUs/1000.0f, currentFps, avgDecodingTimeMs, avgFps, totalFramesDecoded_);
    }
    
    return true;
}

bool CHardwareMjpegDecoder::processInputBuffer(uint8_t* data, size_t size, int64_t timestampUs) {
    // 입력 버퍼 인덱스 가져오기
    ssize_t inputBufferIndex = AMediaCodec_dequeueInputBuffer(codec_, 0);
    if (inputBufferIndex < 0) {
        if (inputBufferIndex != AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
            LOGE("Failed to dequeue input buffer: %zd", inputBufferIndex);
        }
        return false;
    }
    
    // 입력 버퍼 가져오기
    size_t inputBufferSize;
    uint8_t* inputBuffer = AMediaCodec_getInputBuffer(codec_, inputBufferIndex, &inputBufferSize);
    if (!inputBuffer) {
        LOGE("Failed to get input buffer");
        return false;
    }
    
    if (size > inputBufferSize) {
        LOGE("Input data size (%zu) exceeds buffer size (%zu)", size, inputBufferSize);
        return false;
    }
    
    // 데이터 복사
    memcpy(inputBuffer, data, size);
    
    // 입력 버퍼 큐에 넣기
    media_status_t status = AMediaCodec_queueInputBuffer(codec_, inputBufferIndex, 0, size, timestampUs, 0);
    if (status != AMEDIA_OK) {
        LOGE("Failed to queue input buffer: %d", status);
        handleError("queueInputBuffer", status);
        return false;
    }
    
    return true;
}

bool CHardwareMjpegDecoder::processOutputBuffer() {
    // 이전 출력 버퍼가 있다면 해제
    if (currentOutputBufferIndex_ >= 0) {
        AMediaCodec_releaseOutputBuffer(codec_, currentOutputBufferIndex_, false);
        currentOutputBufferIndex_ = -1;
        currentOutputBuffer_ = nullptr;
        currentOutputSize_ = 0;
    }
    
    AMediaCodecBufferInfo bufferInfo;
    ssize_t outputBufferIndex = AMediaCodec_dequeueOutputBuffer(codec_, &bufferInfo, 0);
    
    if (outputBufferIndex >= 0) {
        // 출력 버퍼 데이터 가져오기
        size_t outputSize;
        uint8_t* outputBuffer = AMediaCodec_getOutputBuffer(codec_, outputBufferIndex, &outputSize);
        
        if (outputBuffer && bufferInfo.size > 0) {
            currentOutputBufferIndex_ = outputBufferIndex;
            currentOutputBuffer_ = outputBuffer;
            currentOutputSize_ = bufferInfo.size;
            currentTimestampUs_ = bufferInfo.presentationTimeUs;
            return true;
        } else {
            LOGE("Failed to get output buffer or empty buffer");
            AMediaCodec_releaseOutputBuffer(codec_, outputBufferIndex, false);
            return false;
        }
    } else if (outputBufferIndex == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
        // 출력 포맷 변경
        AMediaFormat* newFormat = AMediaCodec_getOutputFormat(codec_);
        if (newFormat) {
            LOGI("Output format changed");
            
            // 출력 포맷 정보 저장
            AMediaFormat_getInt32(newFormat, "width", &outputWidth_);
            AMediaFormat_getInt32(newFormat, "height", &outputHeight_);
            AMediaFormat_getInt32(newFormat, "color-format", &outputColorFormat_);
            
            LOGI("New output format: %dx%d, color-format: %d", outputWidth_, outputHeight_, outputColorFormat_);
            AMediaFormat_delete(newFormat);
        }
        return true;
    } else if (outputBufferIndex == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
        // 출력 버퍼가 아직 준비되지 않음
        return true;
    } else {
        LOGE("Unexpected output buffer index: %zd", outputBufferIndex);
        return false;
    }
}

bool CHardwareMjpegDecoder::getDecodedFrame(uint8_t** outputData, size_t* outputSize, int64_t* timestampUs) {
    if (!initialized_ || currentOutputBufferIndex_ < 0 || !currentOutputBuffer_) {
        return false;
    }
    
    *outputData = currentOutputBuffer_;
    *outputSize = currentOutputSize_;
    *timestampUs = currentTimestampUs_;
    
    return true;
}

void CHardwareMjpegDecoder::releaseOutputBuffer() {
    if (currentOutputBufferIndex_ >= 0) {
        AMediaCodec_releaseOutputBuffer(codec_, currentOutputBufferIndex_, false);
        currentOutputBufferIndex_ = -1;
        currentOutputBuffer_ = nullptr;
        currentOutputSize_ = 0;
        currentTimestampUs_ = 0;
    }
}

bool CHardwareMjpegDecoder::getOutputFormat(int* width, int* height, int* colorFormat) {
    if (!initialized_ || !width || !height || !colorFormat) {
        return false;
    }
    
    *width = outputWidth_;
    *height = outputHeight_;
    *colorFormat = outputColorFormat_;
    
    return (outputWidth_ > 0 && outputHeight_ > 0);
}

void CHardwareMjpegDecoder::release() {
    LOGD("%s", __FUNCTION__);
    
    // 출력 버퍼 해제
    releaseOutputBuffer();
    
    if (codec_) {
        if (initialized_) {
            AMediaCodec_stop(codec_);
            initialized_ = false;
        }
        AMediaCodec_delete(codec_);
        codec_ = nullptr;
    }
    
    if (format_) {
        AMediaFormat_delete(format_);
        format_ = nullptr;
    }
}

void CHardwareMjpegDecoder::handleError(const char* operation, media_status_t status) {
    const char* errorString = "Unknown error";
    
    switch (status) {
        case AMEDIA_ERROR_UNKNOWN:
            errorString = "Unknown error";
            break;
        case AMEDIA_ERROR_MALFORMED:
            errorString = "Malformed data";
            break;
        case AMEDIA_ERROR_UNSUPPORTED:
            errorString = "Unsupported";
            break;
        case AMEDIA_ERROR_INVALID_OBJECT:
            errorString = "Invalid object";
            break;
        case AMEDIA_ERROR_INVALID_PARAMETER:
            errorString = "Invalid parameter";
            break;
        case AMEDIA_ERROR_INVALID_OPERATION:
            errorString = "Invalid operation";
            break;
        case AMEDIA_ERROR_END_OF_STREAM:
            errorString = "End of stream";
            break;
        case AMEDIA_ERROR_IO:
            errorString = "IO error";
            break;
        case AMEDIA_ERROR_WOULD_BLOCK:
            errorString = "Would block";
            break;
        default:
            break;
    }
    
    LOGE("MediaCodec %s failed: %s (code: %d)", operation, errorString, status);
}
