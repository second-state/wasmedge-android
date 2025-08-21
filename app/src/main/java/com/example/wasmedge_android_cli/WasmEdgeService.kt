package com.example.wasmedge_android_cli

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

class WasmEdgeService : Service() {
    
    private var process: Process? = null
    private var serviceJob: Job? = null
    private var monitoringJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var currentStatus = "Stopped"
    private var serverPort = 8080
    private var lastStartParams: StartParams? = null
    private val isMonitoringEnabled = AtomicBoolean(false)
    
    // Wake lock support
    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockHeld = AtomicBoolean(false)
    private lateinit var powerManager: PowerManager
    private lateinit var sharedPreferences: SharedPreferences
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "WasmEdgeServiceChannel"
        private const val MONITOR_INTERVAL_MS = 5000L // 5 seconds
        private const val RESTART_DELAY_MS = 2000L // 2 seconds
        private const val PREFS_NAME = "WasmEdgeServicePrefs"
        private const val PREF_WAKE_LOCK = "wake_lock_enabled"
        
        // Action constants for notification intents
        const val ACTION_STOP_SERVICE = "com.example.wasmedge_android_cli.STOP_SERVICE"
        const val ACTION_TOGGLE_WAKE_LOCK = "com.example.wasmedge_android_cli.TOGGLE_WAKE_LOCK"
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
        Log.d("WasmEdgeService", "Service created")
        
        // Initialize PowerManager and SharedPreferences
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        createNotificationChannel()
        
        // Restore wake lock state from preferences
        if (sharedPreferences.getBoolean(PREF_WAKE_LOCK, false)) {
            acquireWakeLock()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WasmEdgeService", "Service started with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                // Explicit request to stop service
                stopApiServerImpl()
                releaseWakeLock()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_WAKE_LOCK -> {
                // Toggle wake lock state
                if (isWakeLockHeld.get()) {
                    releaseWakeLock()
                } else {
                    acquireWakeLock()
                }
                updateNotification(currentStatus)
                return START_STICKY
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
            releaseWakeLock()
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
            lastStartParams = StartParams(modelFile, templateType, contextSize, port)
            updateNotification("Starting WasmEdge server on port $port")
            
            serviceJob = CoroutineScope(Dispatchers.IO).launch {
                executeWasmEdgeProcess(modelFile, templateType, contextSize, port)
            }
            
            // Start monitoring after process is fully started
            // (monitoring will be started from executeWasmEdgeProcess after server is ready)
            
            true
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to start API server: ${e.message}")
            updateNotification("Failed to start: ${e.message}")
            false
        }
    }

    private fun getProcessId(process: Process): Int {
        return try {
            val field: Field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to get PID by reflection", e)
            -1
        }
    }

    private fun stopApiServerImpl(): Boolean {
        return try {
            // Stop monitoring first
            stopProcessMonitoring()
            
            if (isRunning.get() && process != null) {
                val pid = getProcessId(process!!)
                if (pid != -1) {
                    Log.i("WasmEdgeService", "Attempting to kill process with PID: $pid")
                    Runtime.getRuntime().exec("kill -9 $pid").waitFor()
                } else {
                    Log.w("WasmEdgeService", "Could not get PID, falling back to destroy()")
                    process?.destroy()
                }
                serviceJob?.cancel()
                // Brief delay to allow the OS to release the port
                try {
                    Thread.sleep(500)
                } catch (ie: InterruptedException) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt()
                }
                isRunning.set(false)
                currentStatus = "Stopped"
                lastStartParams = null
                updateNotification("WasmEdge service stopped")
                Log.i("WasmEdgeService", "API server stopped")
                true
            } else {
                Log.w("WasmEdgeService", "API server is not running or process is null")
                false
            }
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to stop API server: ${e.message}")
            false
        }
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
        
        // Create action for toggling wake lock
        val wakeLockIntent = Intent(this, WasmEdgeService::class.java).apply {
            action = ACTION_TOGGLE_WAKE_LOCK
        }
        val wakeLockPendingIntent = PendingIntent.getService(
            this, 1, wakeLockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val wakeLockAction = NotificationCompat.Action.Builder(
            if (isWakeLockHeld.get()) android.R.drawable.ic_lock_lock else android.R.drawable.ic_lock_idle_lock,
            if (isWakeLockHeld.get()) "Release Wake Lock" else "Acquire Wake Lock",
            wakeLockPendingIntent
        ).build()
        
        // Create action for stopping service
        val stopIntent = Intent(this, WasmEdgeService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "Exit",
            stopPendingIntent
        ).build()
        
        // Build notification with wake lock status
        val notificationText = if (isWakeLockHeld.get()) {
            "$contentText (Wake Lock Active)"
        } else {
            contentText
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WasmEdge Service")
            .setContentText(notificationText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(if (isWakeLockHeld.get()) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .addAction(wakeLockAction)
            .addAction(stopAction)
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
                        
                        Log.i("WasmEdgeService", "Process check - alive: $isProcessAlive, responsive: $isServerResponsive")
                        
                        if (!isProcessAlive) {
                            Log.w("WasmEdgeService", "Process is dead - restarting")
                            handleProcessFailure()
                        } else if (!isServerResponsive) {
                            Log.w("WasmEdgeService", "Process alive but server not responsive - may be starting up or under load")
                            // Don't restart immediately for server responsiveness issues
                            // The process might just be busy or starting up
                            currentStatus = "Server not responding (port $serverPort)"
                            updateNotification(currentStatus)
                        } else {
                            Log.d("WasmEdgeService", "Process health check passed")
                            // Update notification with healthy status
                            if (currentStatus != "Server listening on port $serverPort") {
                                currentStatus = "Server listening on port $serverPort"
                                updateNotification(currentStatus)
                            }
                        }
                    } else {
                        Log.d("WasmEdgeService", "Service not running, skipping checks")
                    }
                    
                    delay(MONITOR_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e("WasmEdgeService", "Error in monitoring loop: ${e.message}")
                    delay(MONITOR_INTERVAL_MS)
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
        return try {
            process?.let { p ->
                val exitValue = p.exitValue() // This throws if process is still running
                Log.w("WasmEdgeService", "Process has exited with code: $exitValue")
                false // If we get here, process has exited
            } ?: run {
                Log.w("WasmEdgeService", "Process object is null")
                false
            }
        } catch (e: IllegalThreadStateException) {
            // Process is still running
            Log.d("WasmEdgeService", "Process is still alive")
            true
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
            // Try a simple socket check as fallback
            checkPortOpen()
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
    
    private fun handleProcessFailure() {
        Log.w("WasmEdgeService", "Handling process failure - attempting restart")
        updateNotification("WasmEdge process died - restarting...")
        
        try {
            // Signal the main process thread to stop reading
            isRunning.set(false)
            
            // Cancel the service job gracefully
            serviceJob?.cancel()
            
            // Clean up dead process after a brief delay
            Thread.sleep(1000)
            process?.destroy()
            
            // Wait before restart
            Thread.sleep(RESTART_DELAY_MS)
            
            // Restart with last known parameters
            lastStartParams?.let { params ->
                Log.i("WasmEdgeService", "Restarting WasmEdge with previous parameters")
                isRunning.set(false) // Reset state
                
                serviceJob = CoroutineScope(Dispatchers.IO).launch {
                    executeWasmEdgeProcess(
                        params.modelFile,
                        params.templateType,
                        params.contextSize,
                        params.port
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to restart process: ${e.message}")
            currentStatus = "Restart failed: ${e.message}"
            updateNotification(currentStatus)
        }
    }
    
    // Wake lock management methods
    private fun acquireWakeLock() {
        try {
            if (!isWakeLockHeld.get()) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "WasmEdgeService::WakeLock"
                ).apply {
                    acquire(10*60*1000L /*10 minutes*/)
                }
                isWakeLockHeld.set(true)
                
                // Save preference
                sharedPreferences.edit().putBoolean(PREF_WAKE_LOCK, true).apply()
                
                Log.i("WasmEdgeService", "Wake lock acquired")
                updateNotification(currentStatus)
            }
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to acquire wake lock: ${e.message}")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            if (isWakeLockHeld.get()) {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
                wakeLock = null
                isWakeLockHeld.set(false)
                
                // Save preference
                sharedPreferences.edit().putBoolean(PREF_WAKE_LOCK, false).apply()
                
                Log.i("WasmEdgeService", "Wake lock released")
                updateNotification(currentStatus)
            }
        } catch (e: Exception) {
            Log.e("WasmEdgeService", "Failed to release wake lock: ${e.message}")
        }
    }
    
    // Helper method to check battery optimization status
    fun isBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = powerManager
            return pm.isIgnoringBatteryOptimizations(packageName)
        }
        return true // Battery optimization doesn't exist before Android M
    }
}
