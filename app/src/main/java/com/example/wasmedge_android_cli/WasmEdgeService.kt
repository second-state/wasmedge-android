package com.example.wasmedge_android_cli

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean
import org.wasmedge.native_lib.NativeLib

class WasmEdgeService : Service() {
    
    lateinit var lib: NativeLib
    private var process: Process? = null
    private var serviceJob: Job? = null
    private var servicePid: Int = -1
    private val isRunning = AtomicBoolean(false)
    private var currentStatus = "Stopped"
    private var serverPort = 8080
    
    private val binder = object : IWasmEdgeServiceStub() {
        
        override fun startApiServer(): Boolean {
            return startApiServerWithParams(
                "gemma-3-1b-it-Q4_K_M.gguf",
                "gemma-3",
                1024,
                8080
            )
        }
        
        override fun startApiServerWithParams(
            modelFile: String,
            templateType: String,
            contextSize: Int,
            port: Int
        ): Boolean {
            return this@WasmEdgeService.startApiServerWithParamsImpl(modelFile, templateType, contextSize, port)
        }
        
        override fun stopApiServer(): Boolean {
            return this@WasmEdgeService.stopApiServerImpl()
        }
        
        override fun isApiServerRunning(): Boolean {
            return this@WasmEdgeService.isApiServerRunningImpl()
        }
        
        override fun getApiServerStatus(): String {
            return this@WasmEdgeService.getApiServerStatusImpl()
        }
        
        override fun getServerPort(): Int {
            return this@WasmEdgeService.getServerPortImpl()
        }
    }
    
    override fun onBind(intent: Intent): IBinder {
        Log.d("WasmEdgeService", "Service bound")
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        lib = NativeLib(this)
        Log.d("WasmEdgeService", "Service created")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WasmEdgeService", "Service destroyed")
        try {
            stopApiServerImpl()
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Error stopping service: ${e.message}")
        }
    }
    
    // Implementation methods for the service
    private fun startApiServerWithParamsImpl(
        modelFile: String,
        templateType: String,
        contextSize: Int,
        port: Int
    ): Boolean {
        if (isRunning.get()) {
            Log.w("WasmEdgeService", "API server is already running")
            return false
        }
        
        return try {
            serverPort = port
            currentStatus = "Initializing..."
            serviceJob = CoroutineScope(Dispatchers.IO).launch {
                // executeWasmEdgeProcess(modelFile, templateType, contextSize, port)
                servicePid = runNativeLlamaApiServer(modelFile, templateType, contextSize, port)
            }
            isRunning.set(true)
            currentStatus = "Running on port $port"
            true
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to start API server: ${e.message}")
            false
        }
    }

    private fun runNativeLlamaApiServer(
        modelFile: String,
        templateType: String,
        contextSize: Int,
        port: Int
    ): Int {
        copyFilesFromAssetsToInternal()
        val modelPath = File(File(filesDir, "llamaedge"), modelFile).absolutePath
        val wasmPath = File(File(filesDir, "llamaedge"), "llama-api-server.wasm").absolutePath
        return lib.llamaApiServer(wasmPath, modelPath, templateType, contextSize, port)
    }

    private fun stopApiServerImpl(): Boolean {
        serviceJob?.let { job ->
            if (job.isActive) {
                Log.d("WasmEdgeService", "Job is active, cancelling service job")
                job.cancel()
            } else if (job.isCompleted) {
                Log.d("WasmEdgeService", "Job is already completed")
            } else if (job.isCancelled) {
                Log.d("WasmEdgeService", "Job is already cancelled")
            }
        }
        if (servicePid != -1) {
            Log.d("WasmEdgeService", "Stopping WasmEdge API server ($servicePid)...")
            ProcessBuilder("kill", "-9", servicePid.toString()).start().waitFor()
            Log.d("WasmEdgeService", "Stopping WasmEdge API server ($servicePid)...done")
            servicePid = -1
        }
        currentStatus = "Stopped"
        isRunning.set(false)
        return true
    }
    
    private fun isApiServerRunningImpl(): Boolean {
        return isRunning.get()
    }
    
    private fun getApiServerStatusImpl(): String {
        return currentStatus
    }
    
    private fun getServerPortImpl(): Int {
        return if (isRunning.get()) serverPort else -1
    }
    
    private fun copyFilesFromAssetsToInternal() {
        val internalFilesDir = File(filesDir, "llamaedge")
        
        if (!internalFilesDir.exists()) {
            internalFilesDir.mkdirs()
        }
        
        try {
            // Copy all files recursively
            copyAssetFolder("llamaedge", internalFilesDir)
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Error copying files: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun copyAssetFolder(assetPath: String, targetDir: File) {
        val assetFiles = assets.list(assetPath) ?: return
        
        assetFiles.forEach { fileName ->
            val assetFilePath = "$assetPath/$fileName"
            val targetFile = File(targetDir, fileName)
            
            try {
                // Try to open as file first
                try {
                    val inputStream = assets.open(assetFilePath)
                    val outputStream = FileOutputStream(targetFile)
                    
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    
                    if (fileName == "wasmedge") {
                        targetFile.setExecutable(true, false)
                        Log.d("WasmEdgeService", "Set executable permission for: $fileName")
                    }
                    
                    Log.d("WasmEdgeService", "Copied: $fileName (${targetFile.length()} bytes)")
                } catch (ioException: Exception) {
                    // If opening as file fails, it's probably a directory
                    val subFiles = assets.list(assetFilePath)
                    if (subFiles != null) {
                        targetFile.mkdirs()
                        Log.d("WasmEdgeService", "Created directory: $fileName")
                        copyAssetFolder(assetFilePath, targetFile)
                    } else {
                        Log.e("WasmEdgeService", "Could not process $fileName as file or directory")
                    }
                }
            } catch (e: Exception) {
                Log.e("WasmEdgeService", "Error copying $fileName: ${e.message}")
            }
        }
    }
    
    private suspend fun executeWasmEdgeProcess(
        modelFile: String,
        templateType: String,
        contextSize: Int,
        port: Int
    ) {
        try {
            currentStatus = "Initializing..."
            copyFilesFromAssetsToInternal()
            
            val wasmedgeFile = File(filesDir, "llamaedge/wasmedge")
            val workingDir = File(filesDir, "llamaedge")
            
            if (!wasmedgeFile.exists()) {
                currentStatus = "Error: WasmEdge binary not found"
                Log.e("WasmEdgeService", "WasmEdge binary not found at: ${wasmedgeFile.absolutePath}")
                return
            }
            
            currentStatus = "Starting WasmEdge..."
            Log.d("WasmEdgeService", "Starting WasmEdge API server...")
            Log.d("WasmEdgeService", "Working directory: ${workingDir.absolutePath}")
            Log.d("WasmEdgeService", "WasmEdge file: ${wasmedgeFile.absolutePath}")
            
            // Create environment array
            val env = arrayOf(
                "LD_LIBRARY_PATH=${workingDir.absolutePath}",
                "WASMEDGE_PLUGIN_PATH=${workingDir.absolutePath}"
            )
            
            process = Runtime.getRuntime().exec(
                arrayOf(
                    wasmedgeFile.absolutePath,
                    "--dir", ".:.",
                    "--nn-preload", "default:GGML:AUTO:$modelFile",
                    "llama-api-server.wasm",
                    "--prompt-template", templateType,
                    "--ctx-size", contextSize.toString(),
                    "--port", port.toString()
                ),
                env,
                workingDir
            )
            
            isRunning.set(true)
            currentStatus = "Running on port $port"
            
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null && isRunning.get()) {
                Log.d("WasmEdgeService", line!!)
                // Update status based on output if needed
                if (line!!.contains("Server listening on")) {
                    currentStatus = "Server listening on port $port"
                }
            }
            
            Log.d("WasmEdgeService", "WasmEdge process completed")
            currentStatus = "Process completed"
            isRunning.set(false)
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            Log.e("WasmEdgeService", errorMsg)
            currentStatus = errorMsg
            isRunning.set(false)
        }
    }
}
