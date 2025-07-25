package com.example.client_app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Example client activity showing how other apps can control the WasmEdge service
 * 
 * To use this in your app:
 * 1. Copy the IWasmEdgeServiceInterface.kt to your project
 * 2. Use this code as a reference for connecting to the WasmEdge service
 */
class ExampleClientActivity : ComponentActivity() {
    
    private var wasmEdgeService: IWasmEdgeServiceInterface? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wasmEdgeService = service as? IWasmEdgeServiceInterface
            isBound = true
            Log.d("ExampleClient", "Connected to WasmEdge service")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            wasmEdgeService = null
            isBound = false
            Log.d("ExampleClient", "Disconnected from WasmEdge service")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bind to WasmEdge service
        bindToWasmEdgeService()
        
        setContent {
            ExampleClientUI()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    private fun bindToWasmEdgeService() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.example.wasmedge_android_cli",
                "com.example.wasmedge_android_cli.WasmEdgeService"
            )
        }
        
        try {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e("ExampleClient", "Failed to bind to WasmEdge service: ${e.message}")
        }
    }
    
    @Composable
    fun ExampleClientUI() {
        var status by remember { mutableStateOf("Not connected") }
        var isServerRunning by remember { mutableStateOf(false) }
        var serverPort by remember { mutableStateOf(-1) }
        
        // Update status periodically
        LaunchedEffect(isBound) {
            while (isBound) {
                wasmEdgeService?.let { service ->
                    status = service.getApiServerStatus()
                    isServerRunning = service.isApiServerRunning()
                    if (isServerRunning) {
                        serverPort = service.getServerPort()
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("WasmEdge Service Client Example", style = MaterialTheme.typography.headlineMedium)
            
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Service Connected: ${if (isBound) "Yes" else "No"}")
                    Text("Server Status: $status")
                    if (isServerRunning) {
                        Text("Server Port: $serverPort")
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        wasmEdgeService?.startApiServer()
                    },
                    enabled = isBound && !isServerRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Server")
                }
                
                Button(
                    onClick = {
                        wasmEdgeService?.stopApiServer()
                    },
                    enabled = isBound && isServerRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop Server")
                }
            }
            
            Button(
                onClick = {
                    wasmEdgeService?.startApiServerWithParams(
                        "custom-model.gguf",
                        "custom-template",
                        2048,
                        8081
                    )
                },
                enabled = isBound && !isServerRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start with Custom Parameters")
            }
            
            Text(
                "This demonstrates how other Android apps can control the WasmEdge service remotely.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Copy this interface to your client app project
 */
interface IWasmEdgeServiceInterface {
    fun startApiServer(): Boolean
    fun startApiServerWithParams(modelFile: String, templateType: String, contextSize: Int, port: Int): Boolean
    fun stopApiServer(): Boolean
    fun isApiServerRunning(): Boolean
    fun getApiServerStatus(): String
    fun getServerPort(): Int
}
