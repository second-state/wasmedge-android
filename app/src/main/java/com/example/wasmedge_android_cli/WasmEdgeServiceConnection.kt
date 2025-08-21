package com.example.wasmedge_android_cli

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Helper class for other applications to connect to the WasmEdge service
 */
class WasmEdgeServiceConnection(private val context: Context) {
    
    private var wasmEdgeService: IWasmEdgeService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wasmEdgeService = IWasmEdgeServiceStub.asInterface(service)
            isBound = true
            Log.d("WasmEdgeServiceConnection", "Service connected")
            onServiceConnected?.invoke()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            wasmEdgeService = null
            isBound = false
            Log.d("WasmEdgeServiceConnection", "Service disconnected")
            onServiceDisconnected?.invoke()
        }
    }
    
    var onServiceConnected: (() -> Unit)? = null
    var onServiceDisconnected: (() -> Unit)? = null
    
    /**
     * Bind to the WasmEdge service and start it as foreground service
     */
    fun bindService(): Boolean {
        if (isBound) {
            Log.w("WasmEdgeServiceConnection", "Service is already bound")
            return true
        }
        
        val intent = Intent().apply {
            component = ComponentName(
                "com.example.wasmedge_android_cli",
                "com.example.wasmedge_android_cli.WasmEdgeService"
            )
        }
        
        return try {
            // Start the service as foreground service first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            // Then bind to it for communication
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Failed to start and bind service: ${e.message}")
            false
        }
    }
    
    /**
     * Unbind from the WasmEdge service (but keep service running)
     */
    fun unbindService() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            wasmEdgeService = null
            Log.d("WasmEdgeServiceConnection", "Service unbound")
        }
    }
    
    /**
     * Stop the WasmEdge service completely
     */
    fun stopService() {
        try {
            // Unbind if bound
            unbindService()
            
            // Stop the service with explicit stop action
            val intent = Intent().apply {
                component = ComponentName(
                    "com.example.wasmedge_android_cli",
                    "com.example.wasmedge_android_cli.WasmEdgeService"
                )
                action = "STOP_SERVICE"
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.d("WasmEdgeServiceConnection", "Service stop requested")
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error stopping service: ${e.message}")
        }
    }
    
    /**
     * Start the API server with default parameters
     */
    fun startApiServer(): Boolean {
        return try {
            wasmEdgeService?.startApiServer() ?: false
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error starting API server: ${e.message}")
            false
        }
    }
    
    /**
     * Start the API server with custom parameters
     */
    fun startApiServerWithParams(
        modelFile: String,
        templateType: String,
        contextSize: Int,
        port: Int
    ): Boolean {
        return try {
            wasmEdgeService?.startApiServerWithParams(modelFile, templateType, contextSize, port) ?: false
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error starting API server with params: ${e.message}")
            false
        }
    }
    
    /**
     * Stop the API server
     */
    fun stopApiServer(): Boolean {
        return try {
            wasmEdgeService?.stopApiServer() ?: false
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error stopping API server: ${e.message}")
            false
        }
    }
    
    /**
     * Check if the API server is running
     */
    fun isApiServerRunning(): Boolean {
        return try {
            wasmEdgeService?.isApiServerRunning() ?: false
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error checking server status: ${e.message}")
            false
        }
    }
    
    /**
     * Get the API server status
     */
    fun getApiServerStatus(): String {
        return try {
            wasmEdgeService?.getApiServerStatus() ?: "Unknown"
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error getting server status: ${e.message}")
            "Error"
        }
    }
    
    /**
     * Get the server port
     */
    fun getServerPort(): Int {
        return try {
            wasmEdgeService?.getServerPort() ?: -1
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Error getting server port: ${e.message}")
            -1
        }
    }    /**
     * Check if the service is bound
     */
    fun isBound(): Boolean {
        return isBound
    }
}
