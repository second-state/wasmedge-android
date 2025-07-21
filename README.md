# WasmEdge Android Sample Project

## Install Android SDK

You could install Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) or [sdkmanager](https://developer.android.com/studio/command-line/sdkmanager) command line tool.

After installation, you need to set the `ANDROID_HOME` environment variable to the path of your Android SDK.

```
export ANDROID_HOME=/path/to/your/android/sdk
```

## Download Assets

Download the following assets to `app/src/main/assets`:

```
curl -LO https://huggingface.co/second-state/gemma-3-1b-it-GGUF/resolve/main/gemma-3-1b-it-Q5_K_M.gguf app/src/main/assets
curl -LO https://github.com/LlamaEdge/LlamaEdge/releases/download/0.24.0/llama-api-server.wasm app/src/main/assets
```

## Build the WasmEdge WASI-NN Plugin

We need to build the WasmEdge WASI-NN plugin to use it in the Android project. After building the plugin, copy the `libwasmedgePluginWasiNN.so` file to the assets directory.

```
./gradlew assembleDebug
cp -f \
  ./lib/build/intermediates/library_jni/debug/jni/arm64-v8a/libwasmedgePluginWasiNN.so \
  ./app/src/main/assets/libwasmedgePluginWasiNN.so
```

## Build the Android App

```
./gradlew assembleDebug
```

## Install the App

```
adb install app/build/outputs/apk/debug/app-debug.apk
```
