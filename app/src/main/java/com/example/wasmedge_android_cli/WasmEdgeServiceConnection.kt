package com.example.wasmedge_android_cli

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * Helper class for other applications to connect to the WasmEdge service
 */
class WasmEdgeServiceConnection(private val context: Context) {
    
    private var wasmEdgeService: IWasmEdgeServiceInterface? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wasmEdgeService = service as? IWasmEdgeServiceInterface
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
     * Bind to the WasmEdge service
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
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("WasmEdgeServiceConnection", "Failed to bind service: ${e.message}")
            false
        }
    }
    
    /**
     * Unbind from the WasmEdge service
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
     * Start the API server with default parameters
     */
    fun startApiServer(): Boolean {
        return wasmEdgeService?.startApiServer() ?: false
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
        return wasmEdgeService?.startApiServerWithParams(modelFile, templateType, contextSize, port) ?: false
    }
    
    /**
     * Stop the API server
     */
    fun stopApiServer(): Boolean {
        return wasmEdgeService?.stopApiServer() ?: false
    }
    
    /**
     * Check if the API server is running
     */
    fun isApiServerRunning(): Boolean {
        return wasmEdgeService?.isApiServerRunning() ?: false
    }
    
    /**
     * Get the current status of the API server
     */
    fun getApiServerStatus(): String {
        return wasmEdgeService?.getApiServerStatus() ?: "Service not connected"
    }
    
    /**
     * Get the port number the server is running on
     */
    fun getServerPort(): Int {
        return wasmEdgeService?.getServerPort() ?: -1
    }
    
    /**
     * Check if the service is bound
     */
    fun isBound(): Boolean {
        return isBound
    }
}
