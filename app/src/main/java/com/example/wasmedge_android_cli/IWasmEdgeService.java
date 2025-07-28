package com.example.wasmedge_android_cli;

import android.os.IInterface;
import android.os.RemoteException;

/**
 * Interface for WasmEdge service operations for inter-process communication
 */
public interface IWasmEdgeService extends IInterface {
    
    /**
     * Start the API server with default parameters
     * @return true if server started successfully, false otherwise
     */
    boolean startApiServer() throws RemoteException;
    
    /**
     * Start the API server with custom parameters
     * @param modelFile The model file name to use
     * @param templateType The template type (e.g., "gemma-3")
     * @param contextSize The context size for the model
     * @param port The port number to run the server on
     * @return true if server started successfully, false otherwise
     */
    boolean startApiServerWithParams(String modelFile, String templateType, int contextSize, int port) throws RemoteException;
    
    /**
     * Stop the running API server
     * @return true if server stopped successfully, false otherwise
     */
    boolean stopApiServer() throws RemoteException;
    
    /**
     * Check if the API server is currently running
     * @return true if server is running, false otherwise
     */
    boolean isApiServerRunning() throws RemoteException;
    
    /**
     * Get the current status of the API server
     * @return status message as string
     */
    String getApiServerStatus() throws RemoteException;
    
    /**
     * Get the port number the server is running on
     * @return port number, or -1 if server is not running
     */
    int getServerPort() throws RemoteException;
}
