# WasmEdge Android Sample Project

## Install Android SDK and NDK

You could install Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) or [sdkmanager](https://developer.android.com/studio/command-line/sdkmanager) command line tool.

After installation, you need to set the `ANDROID_HOME` and `ANDROID_NDK_HOME` environment variable to the path of your Android SDK.

```
export ANDROID_HOME=/path/to/your/android/sdk
export ANDROID_NDK_HOME=/path/to/your/android/ndk
```

## Build the WasmEdge Assets

```
git submodule update --init
cd WasmEdge
cmake -Bbuild \
  -DCMAKE_BUILD_TYPE=Release \
  -DWASMEDGE_USE_LLVM=OFF \
  -DWASMEDGE_PLUGIN_WASI_NN_BACKEND=GGML \
  -DWASMEDGE_PLUGIN_WASI_NN_GGML_LLAMA_NATIVE=OFF \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  .
cmake --build build
```

## Copy the WasmEdge Assets

Copy the built WasmEdge assets to the Android project:

```
cp WasmEdge/build/tools/wasmedge/wasmedge app/src/main/assets/llamaedge/
cp WasmEdge/build/plugins/wasi_nn/libwasmedgePluginWasiNN.so app/src/main/assets/llamaedge/
cp WasmEdge/build/lib/api/libwasmedge.so app/src/main/assets/llamaedge/
```

## Download the LLM Model

Download the following assets to `app/src/main/assets/llamaedge` :

```
curl -LO https://huggingface.co/second-state/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q4_K_M.gguf app/src/main/assets/llamaedge/
```

## Build the Android App

```
./gradlew assembleDebug
```

## Install the App

```
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Run the App

After launching the app, you can open the browser and navigate to `http://localhost:8080` to interact with the Chatbot UI.

![Screen Recording](files/ScreenRecording.gif)
