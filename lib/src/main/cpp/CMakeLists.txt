cmake_minimum_required(VERSION 3.22.1)

project("wasmedge_ndk")

set(WASMEDGE_BUILD_PLUGINS ON CACHE BOOL "" FORCE)
set(WASMEDGE_PLUGIN_WASI_NN_BACKEND "GGML" CACHE STRING "" FORCE)
set(WASMEDGE_PLUGIN_WASI_NN_GGML_LLAMA_NATIVE OFF CACHE BOOL "" FORCE)
set(WASMEDGE_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(WASMEDGE_BUILD_SHARED_LIB ON CACHE BOOL "" FORCE)
set(WASMEDGE_USE_LLVM OFF CACHE BOOL "" FORCE)
set(WASMEDGE_FORCE_DISABLE_LTO ON CACHE BOOL "" FORCE)  # fast fixed problem for `-fuse-ld=gold`, we use lld.

set(WASMEDGE_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../WasmEdge)
add_subdirectory(${WASMEDGE_SOURCE_DIR} WasmEdge)

add_library(
        wasmedge_ndk
        SHARED
        wasmedge.cpp)

target_link_libraries(
        wasmedge_ndk
        log
        android
        wasmedge_shared
)
