# WasmEdge Android Sample Project

## Install Android SDK

You could install Android SDK via [Android Studio](https://developer.android.com/studio/intro/update#sdk-manager) or [sdkmanager](https://developer.android.com/studio/command-line/sdkmanager) command line tool.

After installation, you need to set the `ANDROID_HOME` environment variable to the path of your Android SDK.

```
export ANDROID_HOME=/path/to/your/android/sdk
```

## Download Assets

Download the following assets to `app/src/main/assets` :

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
