package org.wasmedge.native_lib

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import android.util.Log

class NativeLib(ctx : Context) {
    private external fun nativeServer(pluginPath: String, modelPath: String, wasmPath: String) : Int

    companion object {
        init {
            System.loadLibrary("wasmedge_lib")
        }
    }

    private var context = ctx
    private var llamaApiServerWasmBytes : ByteArray = ctx.assets.open("llama-api-server.wasm").readBytes()
    private var wasinnLibBytes : ByteArray = ctx.assets.open("libwasmedgePluginWasiNN.so").readBytes()

    fun llamaApiServer() : Int {
        // Write wasinnLib to temp directory

        Log.w("WEDebug", "llamaApiServer start")
        val tempDir = context.cacheDir
        val pluginFile = File(tempDir, "libwasmedgePluginWasiNN.so")

        Log.w("WEDebug", "write wasinn lib")
        FileOutputStream(pluginFile).use { fos ->
            fos.write(wasinnLibBytes)
        }

        Log.w("WEDebug", "write wasinn lib...done")

        // Write wasm to temp directory
        val wasmFile = File(tempDir, "llama-api-server.wasm")

        Log.w("WEDebug", "write wasm file")
        FileOutputStream(wasmFile).use { fos ->
            fos.write(llamaApiServerWasmBytes)
        }

        Log.w("WEDebug", "write wasm file...done")

        // Write model to temp direcotry
        var assetName = "gemma-3-1b-it-Q4_K_M.gguf"

        Log.w("WEDebug", "write model file")
        val modelFile = File(tempDir, assetName)
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(modelFile).use { outputStream ->
                val buffer = ByteArray(1024 * 1024 * 128) // 1MB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
        Log.w("WEDebug", "write model file...done")

        return nativeServer(pluginFile.absolutePath, modelFile.absolutePath, wasmFile.absolutePath)
    }
}
