package com.example.wasmedge_android_cli

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
    private var monitoringJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var currentStatus = "Stopped"
    private var serverPort = 8080
    private var lastStartParams: StartParams? = null
    private val isMonitoringEnabled = AtomicBoolean(false)
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "WasmEdgeServiceChannel"
    }
    
    private data class StartParams(
        val modelFile: String,
        val templateType: String,
        val contextSize: Int,
        val port: Int
    )
    
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
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WasmEdgeService", "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            "STOP_SERVICE" -> {
                // Explicit request to stop service
                stopApiServerImpl()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // Normal service start
                startForegroundService()
                return START_STICKY
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("WasmEdgeService", "Service destroyed")
        try {
            stopProcessMonitoring()
            stopApiServerImpl()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
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
            lastStartParams = StartParams(modelFile, templateType, contextSize, port)
            updateNotification("Starting WasmEdge server on port $port")
            
            serviceJob = CoroutineScope(Dispatchers.IO).launch {
                // executeWasmEdgeProcess(modelFile, templateType, contextSize, port)
                servicePid = runNativeLlamaApiServer(modelFile, templateType, contextSize, port)
            }
            isRunning.set(true)
            currentStatus = "Running on port $port"
            startProcessMonitoring()
            true
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to start API server: ${e.message}")
            updateNotification("Failed to start: ${e.message}")
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
        lastStartParams = null
        updateNotification("WasmEdge service stopped")
        Log.d("WasmEdgeService", "API server stopped")
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
            updateNotification("WasmEdge server running on port $port")
            
            // Start monitoring after a delay if server output doesn't contain "Server listening on"
            var monitoringStarted = false
            var lineCount = 0
            
            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null && isRunning.get()) {
                Log.d("WasmEdgeService", line!!)
                lineCount++
                
                // Update status based on output if needed
                if (line!!.contains("Server listening on")) {
                    currentStatus = "Server listening on port $port"
                    updateNotification("Server listening on port $port")
                    
                    // Start monitoring only after server is confirmed listening
                    if (!isMonitoringEnabled.get()) {
                        Log.i("WasmEdgeService", "Server is ready (found listening message), starting monitoring")
                        startProcessMonitoring()
                        monitoringStarted = true
                    } else {
                        Log.d("WasmEdgeService", "Monitoring already enabled")
                    }
                } else if (!monitoringStarted && lineCount > 100) {
                    // If we haven't seen "Server listening on" after 100 lines, start monitoring anyway
                    // The server might be running but not outputting the expected message
                    Log.i("WasmEdgeService", "Server seems ready (processed $lineCount lines), starting monitoring")
                    startProcessMonitoring()
                    monitoringStarted = true
                    currentStatus = "Server started (monitoring enabled)"
                    updateNotification(currentStatus)
                } else {
                    Log.v("WasmEdgeService", "Output: $line")
                }
            }
            
            Log.d("WasmEdgeService", "WasmEdge process completed")
            currentStatus = "Process completed"
            if (isMonitoringEnabled.get()) {
                // Process died unexpectedly while monitoring was active
                updateNotification("WasmEdge process died unexpectedly")
                Log.w("WasmEdgeService", "Process died unexpectedly while monitoring")
            } else {
                // Normal shutdown
                updateNotification("WasmEdge process completed")
            }
            isRunning.set(false)
            
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.message}"
            Log.e("WasmEdgeService", errorMsg)
            currentStatus = errorMsg
            updateNotification(errorMsg)
            isRunning.set(false)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WasmEdge Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WasmEdge API Server Service"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun startForegroundService() {
        val notification = createNotification("WasmEdge service is ready")
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WasmEdge Service")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun startProcessMonitoring() {
        if (isMonitoringEnabled.get()) {
            Log.d("WasmEdgeService", "Monitoring already started")
            return
        }
        
        isMonitoringEnabled.set(true)
        Log.i("WasmEdgeService", "Process monitoring enabled - starting in 5 seconds")
        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            delay(5000) // Wait for initial startup
            Log.i("WasmEdgeService", "Starting process monitoring loop")
            updateNotification("Monitoring WasmEdge process...")
            
            var loopCount = 0
            while (isMonitoringEnabled.get()) {
                try {
                    loopCount++
                    Log.d("WasmEdgeService", "Monitoring loop $loopCount")
                    
                    if (isRunning.get()) {
                        val isProcessAlive = checkProcessAlive()
                        val isServerResponsive = checkServerHealth()
                        val isPortOpen = checkPortOpen()
                        Log.i("WasmEdgeService", "Process check")
                        Log.i("WasmEdgeService", "- alive: $isProcessAlive")
                        Log.i("WasmEdgeService", "- responsive: $isServerResponsive")
                        Log.i("WasmEdgeService", "- port open: $isPortOpen")
                    } else {
                        Log.d("WasmEdgeService", "Service not running, skipping checks")
                    }
                    delay(5000) // 5 seconds between checks
                } catch (e: Exception) {
                    Log.e("WasmEdgeService", "Error in monitoring loop: ${e.message}")
                    delay(5000) // Wait before next check on error
                }
            }
            Log.i("WasmEdgeService", "Monitoring loop ended")
        }
    }
    
    private fun stopProcessMonitoring() {
        isMonitoringEnabled.set(false)
        monitoringJob?.cancel()
        monitoringJob = null
        Log.d("WasmEdgeService", "Process monitoring stopped")
    }
    
    private fun checkProcessAlive(): Boolean {
        // check if servicePid is still running
        return try {
            if (servicePid == -1) {
                Log.w("WasmEdgeService", "No valid PID to check")
                return false
            }
            val process = ProcessBuilder()
                .command("ps", "-p", servicePid.toString())
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            Log.d("WasmEdgeService", "Process check exit code: $exitCode")
            return exitCode == 0
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Error checking process alive: ${e.message}")
            false
        }
    }
    
    private fun checkServerHealth(): Boolean {
        return try {
            Log.d("WasmEdgeService", "Checking server health at localhost:$serverPort")
            val url = java.net.URL("http://localhost:$serverPort/v1/models")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "WasmEdgeService/1.0")
            
            val responseCode = connection.responseCode
            Log.d("WasmEdgeService", "Server health check response code: $responseCode")
            connection.disconnect()
            
            val isHealthy = responseCode in 200..299
            if (isHealthy) {
                Log.d("WasmEdgeService", "Server health check passed")
            } else {
                Log.w("WasmEdgeService", "Server health check failed with code: $responseCode")
            }
            isHealthy
        } catch (e: Exception) {
            Log.w("WasmEdgeService", "Server health check failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }
    
    private fun checkPortOpen(): Boolean {
        return try {
            Log.d("WasmEdgeService", "Fallback: checking if port $serverPort is open")
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", serverPort), 3000)
            socket.close()
            Log.d("WasmEdgeService", "Port $serverPort is open")
            true
        } catch (e: Exception) {
            Log.w("WasmEdgeService", "Port check failed: ${e.message}")
            false
        }
    }
}
