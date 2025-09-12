# WasmEdge Android Sample Project

## Install Android SDK and NDK

You could install Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) or [sdkmanager](https://developer.android.com/studio/command-line/sdkmanager) command line tool.

After installation, you need to set the `ANDROID_HOME` and `ANDROID_NDK_HOME` environment variable to the path of your Android SDK.

```
export ANDROID_HOME=/path/to/your/android/sdk
export ANDROID_NDK_HOME=/path/to/your/android/ndk
```

## Patch the WasmEdge Assets

```
git submodule update --init
(cd WasmEdge && git apply ../patch/wasmedge-android.patch)
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

~~After launching the app, you can open the browser and navigate to `http://localhost:8080` to interact with the Chatbot UI.~~

The current version of the app will stop the service when the main activity is no longer visible.
If you want to test the API server, you can use adb forward to forward the port and test it on your computer:

```
adb forward tcp:8080 tcp:8080

curl -X POST http://localhost:8080/v1/chat/completions \
  -H 'accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{"messages":[{"role":"system", "content": "You are a helpful assistant."}, {"role":"user", "content": "Hi"}]}'
```
