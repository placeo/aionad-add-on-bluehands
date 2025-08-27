#pragma once

#include <cstdint>
#include <cstddef>

#ifdef __ANDROID__
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window.h>
#include <jni.h>
#else
// IDE 지원을 위한 더미 정의
typedef void* AMediaCodec;
typedef void* AMediaFormat;
typedef void* ANativeWindow;
typedef int media_status_t;
typedef long int64_t;
typedef unsigned char uint8_t;
typedef unsigned long size_t;
typedef long ssize_t;
#define AMEDIA_OK 0
#define AMEDIACODEC_INFO_TRY_AGAIN_LATER -1
#define AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED -2
#define AMEDIA_ERROR_UNKNOWN -1
#define AMEDIA_ERROR_MALFORMED -2
#define AMEDIA_ERROR_UNSUPPORTED -3
#define AMEDIA_ERROR_INVALID_OBJECT -4
#define AMEDIA_ERROR_INVALID_PARAMETER -5
#define AMEDIA_ERROR_INVALID_OPERATION -6
#define AMEDIA_ERROR_END_OF_STREAM -7
#define AMEDIA_ERROR_IO -8
#define AMEDIA_ERROR_WOULD_BLOCK -9
#define AMEDIAFORMAT_KEY_MIME "mime"
#define AMEDIAFORMAT_KEY_WIDTH "width"
#define AMEDIAFORMAT_KEY_HEIGHT "height"
#endif

#include "common/log/JniLogger.h"

class CHardwareMjpegDecoder {
public:
    CHardwareMjpegDecoder();
    ~CHardwareMjpegDecoder();

    // 사용 가능한 MJPEG 하드웨어 디코더 찾기
    static const char* findMjpegHardwareDecoder(int width, int height);

    // 하드웨어 디코더 초기화 (Surface 없이)
    bool initialize(int width, int height);
    
    // MJPEG 데이터 디코딩
    bool decode(uint8_t* mjpegData, size_t dataSize, int64_t timestampUs);
    
    // 디코딩된 출력 버퍼 가져오기
    bool getDecodedFrame(uint8_t** outputData, size_t* outputSize, int64_t* timestampUs);
    
    // 출력 버퍼 해제
    void releaseOutputBuffer();
    
    // 디코더 정리
    void release();
    
    // 디코더 상태 확인
    bool isInitialized() const { return initialized_; }
    
    // 출력 포맷 정보 가져오기
    bool getOutputFormat(int* width, int* height, int* colorFormat);

private:
    AMediaCodec* codec_;
    AMediaFormat* format_;
    bool initialized_;
    
    // 출력 버퍼 관련
    ssize_t currentOutputBufferIndex_;
    uint8_t* currentOutputBuffer_;
    size_t currentOutputSize_;
    int64_t currentTimestampUs_;
    
    // 출력 포맷 정보
    int outputWidth_;
    int outputHeight_;
    int outputColorFormat_;
    
    // 성능 측정
    int64_t lastInputTimestampUs_;
    int64_t lastOutputTimestampUs_;
    int64_t totalFramesDecoded_;
    int64_t totalDecodingTimeUs_;
    
    // 코덱 이름
    static const char* MJPEG_CODEC_NAME;
    
    // 입력 버퍼 처리
    bool processInputBuffer(uint8_t* data, size_t size, int64_t timestampUs);
    
    // 출력 버퍼 처리  
    bool processOutputBuffer();
    
    // 에러 처리
    void handleError(const char* operation, media_status_t status);
};
