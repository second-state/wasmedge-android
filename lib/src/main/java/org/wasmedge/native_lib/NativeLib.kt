package org.wasmedge.native_lib

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class NativeLib(
    ctx: Context,
) {
    private external fun nativeServer(
        wasmPath: String,
        modelPath: String,
        templateType: String,
        contextSize: Int,
        port: Int,
    ): Int

    companion object {
        init {
            System.loadLibrary("wasmedge_ndk")
        }
    }

    private var context = ctx

    fun llamaApiServer(
        wasmPath: String,
        modelPath: String,
        templateType: String,
        contextSize: Int,
        port: Int,
    ): Int {
        Log.w("WasmEdgeNDK", "llamaApiServer starting...")
        return nativeServer(wasmPath, modelPath, templateType, contextSize, port)
    }
}
