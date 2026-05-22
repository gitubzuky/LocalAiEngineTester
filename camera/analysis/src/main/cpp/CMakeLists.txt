cmake_minimum_required(VERSION 3.22.1)

project(camera_analysis_preprocessor)

add_library(camera_analysis_preprocessor SHARED native_preprocessor.cpp)

target_compile_features(camera_analysis_preprocessor PRIVATE cxx_std_17)

find_library(log-lib log)

target_link_libraries(camera_analysis_preprocessor ${log-lib})
