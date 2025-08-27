cmake_minimum_required(VERSION 3.22)

project(libausbc-native)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${COMPILATION_FLAGS} -std=c++17")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS} ${COMPILATION_RELEASE_FLAGS}")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS} ${COMPILATION_DEBUG_FLAGS}")

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${COMPILATION_FLAGS} -std=c99")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS} ${COMPILATION_RELEASE_FLAGS}")
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS} ${COMPILATION_DEBUG_FLAGS}")

set(ANDROID_ARM_NEON TRUE)
set(REQUIRE_SIMD ON)
set(ENABLE_SHARED FALSE)
set(ENABLE_STATIC TRUE)

add_compile_definitions(LIBUVC_HAS_JPEG)

include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/libjpeg-turbo-1.5.0/android/CMakeLists.txt)
include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/hacks/CMakeLists.txt)
include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/common/CMakeLists.txt)
include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/libusb/android/jni/CMakeLists.txt)
include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/libuvc/android/jni/CMakeLists.txt)
include(${CMAKE_CURRENT_LIST_DIR}/src/main/jni/UVCCamera/CMakeLists.txt)

add_library(libausbc-native SHARED ${SOURCES})

target_include_directories(libausbc-native PUBLIC
    ${CMAKE_CURRENT_LIST_DIR}/src/main/jni/
    ${CMAKE_CURRENT_LIST_DIR}/src/main/jni/spdlog/include
)

target_link_libraries(libausbc-native
        PUBLIC
        jpeg-static
        android
        log
        GStreamer::mobile)
