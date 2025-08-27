cmake_minimum_required(VERSION 3.22)

include_directories(
    ${CMAKE_CURRENT_LIST_DIR}/
    ${CMAKE_CURRENT_LIST_DIR}/spdlog/include
)

list(APPEND SOURCES
    ${CMAKE_CURRENT_LIST_DIR}/log/JniLogger.cpp
    ${CMAKE_CURRENT_LIST_DIR}/log/JniFileLogger.cpp
    ${CMAKE_CURRENT_LIST_DIR}/configuration/CConfiguration.cpp
    ${CMAKE_CURRENT_LIST_DIR}/monitor/CMonitor.cpp
)
