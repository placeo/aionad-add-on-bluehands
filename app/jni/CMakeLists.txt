cmake_minimum_required(VERSION 3.18.1)

project("PhotoBoxProject" LANGUAGES C CXX)

# Release 빌드 시 네이티브 코드 최적화 레벨 설정
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

if(NOT DEFINED GSTREAMER_ROOT_ANDROID)
    message(FATAL_ERROR "GSTREAMER_ROOT_ANDROID is not defined!")
endif()

if(ANDROID_ABI STREQUAL "armeabi")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/arm")
elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/armv7")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/arm64")
elseif(ANDROID_ABI STREQUAL "x86")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/x86")
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(GSTREAMER_ROOT "${GSTREAMER_ROOT_ANDROID}/x86_64")
else()
    message(FATAL_ERROR "Target arch ABI not supported: ${ANDROID_ABI}")
endif()

list(APPEND CMAKE_MODULE_PATH "${GSTREAMER_ROOT}/share/cmake")

include_directories(
    ${CMAKE_CURRENT_LIST_DIR}
    ${CMAKE_CURRENT_LIST_DIR}/../../libs/libausbc/src/main/jni
    ${CMAKE_CURRENT_LIST_DIR}/../../libs/libausbc/src/main/jni/spdlog/include
)

set(GSTREAMER_NDK_BUILD_PATH  "${GSTREAMER_ROOT}/share/gst-android/ndk-build/")
include("${GSTREAMER_NDK_BUILD_PATH}/plugins.cmake")
list(REMOVE_ITEM GSTREAMER_PLUGINS_CAPTURE "tinyalsa")
set(GSTREAMER_PLUGINS         ${GSTREAMER_PLUGINS_CORE} ${GSTREAMER_PLUGINS_SYS} ${GSTREAMER_PLUGINS_EFFECTS} ${GSTREAMER_PLUGINS_CAPTURE} ${GSTREAMER_PLUGINS_APP} androidmedia jpeg)
set(GStreamer_EXTRA_DEPS gstreamer-video-1.0 gstreamer-app-1.0 gobject-2.0)
find_library(ANDROID_LIB android REQUIRED)
find_library(LOG_LIB log REQUIRED)
find_library(MEDIANDK_LIB mediandk REQUIRED)
find_package(GStreamerMobile COMPONENTS ${GSTREAMER_PLUGINS} fonts REQUIRED)

message(INFO "CURRENT DIRECTORY: ${CMAKE_CURRENT_LIST_DIR}")

# Add compile definitions for UVC ABI
if (ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_definitions(UVC_ABI=1)
elseif (ANDROID_ABI STREQUAL "armeabi-v7a")
    add_compile_definitions(UVC_ABI=2)
elseif (ANDROID_ABI STREQUAL "x86_64")
    add_compile_definitions(UVC_ABI=3)
elseif (ANDROID_ABI STREQUAL "x86")
    add_compile_definitions(UVC_ABI=4)
endif ()

# Add other required compile definitions
add_compile_definitions(
    ACCESS_RAW_DESCRIPTORS
    LIBUVC_HAS_JPEG
    SIZEOF_SIZE_T=8
)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../libs/libausbc ../libausbc-build)

add_library(PhotoBox SHARED
    PhotoBoxMain.cpp
    CCustomData.cpp
    media/CPipeline.cpp
    media/CHardwareMjpegDecoder.cpp
)
target_link_libraries(PhotoBox
    PUBLIC
        libausbc-native
        ${ANDROID_LIB}
        ${LOG_LIB}
        ${MEDIANDK_LIB}
)
set_target_properties(PhotoBox
    PROPERTIES
        C_VISIBILITY_PRESET hidden
        CXX_VISIBILITY_PRESET hidden
)








