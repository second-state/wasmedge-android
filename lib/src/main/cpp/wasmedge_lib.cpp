#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <array>
#include <cstring>
#include <dlfcn.h>
#include <filesystem>
#include <fstream>
#include <jni.h>
#include <string>
#include <unistd.h>

#include <wasmedge/wasmedge.h>

#define LOG_TAG "WasmEdge"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_org_wasmedge_native_1lib_NativeLib_nativeServer(JNIEnv *env, jobject thiz,
                                                     jstring pluginPath,
                                                     jstring modelPath,
                                                     jstring wasmPath)
{
  // Convert jstring to C string
  const char *PluginPathStr = env->GetStringUTFChars(pluginPath, nullptr);
  const char *ModelPathStr = env->GetStringUTFChars(modelPath, nullptr);
  const char *WasmPathStr = env->GetStringUTFChars(wasmPath, nullptr);

  LOGI("PluginPathStr: %s", PluginPathStr);
  LOGI("ModelPathStr: %s", ModelPathStr);
  LOGI("WasmPathStr: %s", WasmPathStr);

  // Load the plugins.
  WasmEdge_PluginLoadWithDefaultPaths();
  WasmEdge_PluginLoadFromPath(PluginPathStr);

  LOGI("Listing all plugins:");
  WasmEdge_String Names[20];
  const uint32_t NumPlugins = WasmEdge_PluginListPlugins(Names, 20);
  for (int I = 0; I < NumPlugins; I++)
  {
    LOGI("   Plugin %d: %s", I, Names[I].Buf);
  }

  std::string NNPreloadArg = "default:GGML:AUTO:" + std::string(ModelPathStr);
  int argc = 8;
  const char *argv[] = {
      "wasmedge", "--dir", ".:.", "--nn-preload",
      NNPreloadArg.c_str(), WasmPathStr, "-p", "chatml"};
  WasmEdge_Driver_UniTool(argc, argv);

  // Release
  env->ReleaseStringUTFChars(pluginPath, PluginPathStr);
  env->ReleaseStringUTFChars(modelPath, ModelPathStr);
  env->ReleaseStringUTFChars(wasmPath, WasmPathStr);

  return NumPlugins;
}
