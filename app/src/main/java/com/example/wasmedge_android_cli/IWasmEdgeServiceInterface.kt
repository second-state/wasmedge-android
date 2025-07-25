package com.example.wasmedge_android_cli

/**
 * Interface for WasmEdge service operations
 * This replaces the AIDL interface for simpler implementation
 */
interface IWasmEdgeServiceInterface {
    /**
     * Start the API server with default parameters
     */
    fun startApiServer(): Boolean
    
    /**
     * Start the API server with custom parameters
     */
    fun startApiServerWithParams(modelFile: String, templateType: String, contextSize: Int, port: Int): Boolean
    
    /**
     * Stop the API server
     */
    fun stopApiServer(): Boolean
    
    /**
     * Check if the API server is running
     */
    fun isApiServerRunning(): Boolean
    
    /**
     * Get the current status of the API server
     */
    fun getApiServerStatus(): String
    
    /**
     * Get the port number the server is running on
     */
    fun getServerPort(): Int
}
