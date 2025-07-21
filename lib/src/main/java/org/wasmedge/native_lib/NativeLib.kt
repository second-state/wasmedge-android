package org.wasmedge.native_lib

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class NativeLib(ctx : Context) {
    private external fun nativeWasmFibonacci(imageBytes : ByteArray, idx : Int ) : Int
    private external fun nativeServer(pluginPath: String, modelPath: String, wasmPath: String) : Int

    companion object {
        init {
            System.loadLibrary("wasmedge_lib")
        }
    }

    private var context = ctx
    private var fibonacciWasmImageBytes : ByteArray = ctx.assets.open("fibonacci.wasm").readBytes()
    private var llamaApiServerWasmBytes : ByteArray = ctx.assets.open("llama-api-server.wasm").readBytes()
    private var wasinnLibBytes : ByteArray = ctx.assets.open("libwasmedgePluginWasiNN.so").readBytes()

    fun wasmFibonacci(idx : Int) : Int{
        return nativeWasmFibonacci(fibonacciWasmImageBytes, idx)
    }

    fun llamaApiServer() : Int {
        // Write wasinnLib to temp directory
        val tempDir = context.cacheDir
        val pluginFile = File(tempDir, "libwasmedgePluginWasiNN.so")
        FileOutputStream(pluginFile).use { fos ->
            fos.write(wasinnLibBytes)
        }

        // Write wasm to temp directory
        val wasmFile = File(tempDir, "llama-api-server.wasm")
        FileOutputStream(wasmFile).use { fos ->
            fos.write(llamaApiServerWasmBytes)
        }

        // Write model to temp direcotry
        var assetName = "gemma-3-1b-it-Q5_K_M.gguf"
        val modelFile = File(tempDir, assetName)
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(modelFile).use { outputStream ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }

        return nativeServer(pluginFile.absolutePath, modelFile.absolutePath, wasmFile.absolutePath)
    }
}
