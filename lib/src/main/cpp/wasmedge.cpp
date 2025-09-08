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

#define LOG_TAG "WasmEdgeNDK"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void listAllWasmEdgePlugins()
{
  LOGI("Listing all plugins:");
  WasmEdge_String Names[20];
  const uint32_t NumPlugins = WasmEdge_PluginListPlugins(Names, 20);
  for (int I = 0; I < NumPlugins; I++)
  {
    LOGI("   Plugin %d: %s", I, Names[I].Buf);
  }
  LOGW("List plugin...done");
}

static int stdout_pipe[2];
static int stderr_pipe[2];
static pthread_t stdout_thread;
static pthread_t stderr_thread;

void *log_thread_func(void *arg)
{
  int pipe_fd = *((int *)arg);
  char buffer[1024];
  const char *tag = "WasmEdgeNDK";

  while (true)
  {
    ssize_t count = read(pipe_fd, buffer, sizeof(buffer) - 1);
    if (count <= 0)
      break;

    buffer[count] = '\0';
    if (buffer[count - 1] == '\n')
      buffer[count - 1] = '\0';

    __android_log_write(ANDROID_LOG_INFO, tag, buffer);
  }
  return nullptr;
}

void setup_stdout_stderr_redirect()
{
  pipe(stdout_pipe);
  pipe(stderr_pipe);

  // backup stdout/stderr
  int original_stdout = dup(STDOUT_FILENO);
  int original_stderr = dup(STDERR_FILENO);

  // redirect stdout/stderr to pipe
  dup2(stdout_pipe[1], STDOUT_FILENO);
  dup2(stderr_pipe[1], STDERR_FILENO);

  // close pipes
  close(stdout_pipe[1]);
  close(stderr_pipe[1]);

  // create threads to read pipe and output to logcat
  pthread_create(&stdout_thread, nullptr, log_thread_func, &stdout_pipe[0]);
  pthread_create(&stderr_thread, nullptr, log_thread_func, &stderr_pipe[0]);
}

// Call this function after WasmEdge_Driver_UniTool finishes (optional)
void restore_stdout_stderr()
{
  // close pipes
  close(stdout_pipe[0]);
  close(stderr_pipe[0]);

  // wait for threads to finish
  pthread_join(stdout_thread, nullptr);
  pthread_join(stderr_thread, nullptr);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_wasmedge_native_1lib_NativeLib_nativeServer(JNIEnv *env, jobject thiz,
                                                     jstring wasmPath,
                                                     jstring modelPath,
                                                     jstring templateType,
                                                     jint contextSize,
                                                     jint port)
{
  // Convert jstring to C string
  const char *WasmPathStr = env->GetStringUTFChars(wasmPath, nullptr);
  const char *ModelPathStr = env->GetStringUTFChars(modelPath, nullptr);
  const char *TemplateTypeStr = env->GetStringUTFChars(templateType, nullptr);

  LOGI("WasmPathStr: %s", WasmPathStr);
  LOGI("ModelPathStr: %s", ModelPathStr);
  LOGI("TemplateTypeStr: %s", TemplateTypeStr);

  // Load the plugins.
  LOGD("Load default plugin");
  WasmEdge_PluginLoadWithDefaultPaths();
  LOGD("Load default plugin...done");

  listAllWasmEdgePlugins();

  // Load libwasmedgePluginWasiNN.so using dlopen
  LOGW("Loading libwasmedgePluginWasiNN.so with dlopen");
  void *handle = dlopen("libwasmedgePluginWasiNN.so", RTLD_LAZY);

  if (!handle)
  {
    LOGE("Failed to load libwasmedgePluginWasiNN.so: %s", dlerror());
  }
  else
  {
    LOGI("Successfully loaded libwasmedgePluginWasiNN.so");

    // Try to get plugin name function using dlsym first to get a valid address
    typedef const WasmEdge_PluginDescriptor *(*GetPluginNameFunc)();
    GetPluginNameFunc GetDescriptor = (GetPluginNameFunc)dlsym(handle, "GetDescriptor");

    if (!GetDescriptor)
    {
      LOGW("No GetDescriptor function found in the library");
    }
    else
    {
      const WasmEdge_PluginDescriptor *pluginDesc = GetDescriptor();
      LOGD("Plugin name from dlsym: %s", pluginDesc->Name);

      LOGI("Get library path using dladdr");
      Dl_info info;
      if (!dladdr((void *)GetDescriptor, &info))
      {
        LOGW("dladdr failed");
      }
      else
      {
        LOGI("Library path: %s", info.dli_fname);
        WasmEdge_PluginLoadFromPath(info.dli_fname);
      }
    }

    // Close the library handle
    dlclose(handle);
  }

  listAllWasmEdgePlugins();

  LOGW("Start server");
  // Set working directory to the same location as ModelPath
  std::string WorkingDir = std::filesystem::path(ModelPathStr).parent_path();
  if (chdir(WorkingDir.c_str()) != 0)
  {
    LOGW("Failed to change working directory to: %s, error: %s", WorkingDir.c_str(), strerror(errno));
  }
  LOGI("Changed working directory to: %s", WorkingDir.c_str());
  std::string NNPreloadArg = "default:GGML:AUTO:" + std::string(ModelPathStr);
  std::string ContextSizeStr = std::to_string(contextSize);
  std::string PortStr = std::to_string(port);
  int argc = 12;
  const char *argv[] = {
      "wasmedge", "--dir", ".:.", "--nn-preload", NNPreloadArg.c_str(), WasmPathStr,
      "--prompt-template", TemplateTypeStr,
      "--ctx-size", ContextSizeStr.c_str(),
      "--port", PortStr.c_str()};
  for (int i = 0; i < argc; i++)
  {
    LOGW("argv[%d]: %s", i, argv[i]);
  }

  /* Fork version */
  pid_t server_pid = fork();
  if (server_pid == 0)
  {
    // Child process
    setup_stdout_stderr_redirect();
    WasmEdge_Driver_UniTool(argc, argv);
    restore_stdout_stderr();
    exit(0);
  }
  else if (server_pid > 0)
  {
    // Parent process
    LOGW("Server started in process with PID: %d", server_pid);
  }
  else
  {
    // Fork failed
    LOGE("Failed to fork process: %s", strerror(errno));
  }
  LOGW("Start server...done");

  // Release
  env->ReleaseStringUTFChars(wasmPath, WasmPathStr);
  env->ReleaseStringUTFChars(modelPath, ModelPathStr);
  env->ReleaseStringUTFChars(templateType, TemplateTypeStr);

  return (jint)server_pid;
}
