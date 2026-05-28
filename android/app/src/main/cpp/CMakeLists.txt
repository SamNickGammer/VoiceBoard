cmake_minimum_required(VERSION 3.22)

# `appmodules` is RN's default app-side lib — it links OnLoad.cpp, autolinking
# providers, and the FBReactNativeSpec TurboModule provider (which is what
# exposes PlatformConstants etc. to JS). REPLACING it without including the
# ReactNative-application.cmake glue silently breaks every TurboModule lookup.
project(appmodules CXX C)

include(${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake)

# --- VoiceBoard local Whisper (whisper.cpp via FetchContent) ---------------

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS         OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS       OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER      OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES       OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS          OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE               OFF CACHE BOOL "" FORCE)
set(GGML_CUDA                 OFF CACHE BOOL "" FORCE)
set(GGML_HIPBLAS              OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN               OFF CACHE BOOL "" FORCE)
set(GGML_METAL                OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP               OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE               OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL               OFF CACHE BOOL "" FORCE)
set(GGML_BLAS                 OFF CACHE BOOL "" FORCE)

include(FetchContent)
FetchContent_Declare(
    whisper_cpp
    GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
    GIT_TAG        v1.7.4
)
FetchContent_MakeAvailable(whisper_cpp)
message(STATUS "VoiceBoard whisper_cpp_SOURCE_DIR=${whisper_cpp_SOURCE_DIR}")

add_library(voiceboardwhisper SHARED whisper/whisper_jni.cpp)
target_include_directories(voiceboardwhisper PRIVATE
    "${whisper_cpp_SOURCE_DIR}/include"
    "${whisper_cpp_SOURCE_DIR}/ggml/include"
)
target_link_libraries(voiceboardwhisper PRIVATE whisper android log)
