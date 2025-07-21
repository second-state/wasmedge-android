cmake_minimum_required(VERSION 3.22.1)

project("wasmedge_lib")

include(FetchContent)

set(WASMEDGE_BUILD_PLUGINS ON CACHE BOOL "" FORCE)
set(WASMEDGE_PLUGIN_WASI_NN_BACKEND "GGML" CACHE STRING "" FORCE)
set(WASMEDGE_PLUGIN_WASI_NN_GGML_LLAMA_NATIVE OFF CACHE BOOL "" FORCE)
set(WASMEDGE_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(WASMEDGE_BUILD_SHARED_LIB ON CACHE BOOL "" FORCE)
set(WASMEDGE_USE_LLVM OFF CACHE BOOL "" FORCE)
set(WASMEDGE_FORCE_DISABLE_LTO ON CACHE BOOL "" FORCE)  # fast fixed problem for `-fuse-ld=gold`, we use lld.

FetchContent_Declare(
        wasmedge
        GIT_REPOSITORY https://github.com/WasmEdge/WasmEdge.git
        GIT_TAG        9438d6a3857d1bb4a6179598ff6dce67d037e40d # TODO: use a specific tag or commit for stability
        GIT_SHALLOW    FALSE
)
FetchContent_MakeAvailable(wasmedge)

add_library(
        wasmedge_lib
        SHARED
        wasmedge_lib.cpp)

target_link_libraries(
        wasmedge_lib
        log
        android
        wasmedge_shared
)
