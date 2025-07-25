package com.example.wasmedge_android_cli

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Example client activity showing how external applications can control the WasmEdge service
 */
class ExampleClientActivity : ComponentActivity() {

    private lateinit var serviceConnection: WasmEdgeServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        serviceConnection = WasmEdgeServiceConnection(this)
        
        setContent {
            ClientUI(serviceConnection = serviceConnection)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.unbindService()
    }
}

@Composable
fun ClientUI(serviceConnection: WasmEdgeServiceConnection) {
    var isServiceBound by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Not connected") }
    var logText by remember { mutableStateOf("WasmEdge Service Client\n") }
    val coroutineScope = rememberCoroutineScope()

    // Bind to service when component starts
    LaunchedEffect(Unit) {
        serviceConnection.onServiceConnected = {
            isServiceBound = true
            logText += "✓ Connected to WasmEdge service\n"
        }
        
        serviceConnection.onServiceDisconnected = {
            isServiceBound = false
            logText += "✗ Disconnected from WasmEdge service\n"
        }
        
        val bindResult = serviceConnection.bindService()
        logText += if (bindResult) {
            "Attempting to bind to WasmEdge service...\n"
        } else {
            "Failed to bind to WasmEdge service. Make sure the WasmEdge app is installed.\n"
        }
    }

    // Update server status periodically
    LaunchedEffect(isServiceBound) {
        while (isServiceBound) {
            kotlinx.coroutines.delay(1000)
            serverStatus = serviceConnection.getApiServerStatus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "WasmEdge Service Client",
            style = MaterialTheme.typography.headlineMedium
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Service Status: ${if (isServiceBound) "Connected" else "Disconnected"}")
                Text("Server Status: $serverStatus")
                if (serviceConnection.isBound() && serviceConnection.isApiServerRunning()) {
                    Text("Server Port: ${serviceConnection.getServerPort()}")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val success = serviceConnection.startApiServer()
                            logText += if (success) {
                                "✓ API server started successfully\n"
                            } else {
                                "✗ Failed to start API server\n"
                            }
                        } catch (e: Exception) {
                            logText += "✗ Error starting server: ${e.message}\n"
                        }
                    }
                },
                enabled = isServiceBound && !serviceConnection.isApiServerRunning(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Server")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            // Start with custom parameters
                            val success = serviceConnection.startApiServerWithParams(
                                modelFile = "gemma-3-1b-it-Q4_K_M.gguf",
                                templateType = "gemma-3", 
                                contextSize = 2048,
                                port = 8081
                            )
                            logText += if (success) {
                                "✓ API server started with custom params (port 8081)\n"
                            } else {
                                "✗ Failed to start API server with custom params\n"
                            }
                        } catch (e: Exception) {
                            logText += "✗ Error starting server: ${e.message}\n"
                        }
                    }
                },
                enabled = isServiceBound && !serviceConnection.isApiServerRunning(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Custom")
            }
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val success = serviceConnection.stopApiServer()
                        logText += if (success) {
                            "✓ API server stopped\n"
                        } else {
                            "✗ Failed to stop API server\n"
                        }
                    } catch (e: Exception) {
                        logText += "✗ Error stopping server: ${e.message}\n"
                    }
                }
            },
            enabled = isServiceBound && serviceConnection.isApiServerRunning(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Server")
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    try {
                        val isRunning = serviceConnection.isApiServerRunning()
                        val status = serviceConnection.getApiServerStatus()
                        val port = serviceConnection.getServerPort()
                        
                        logText += "Server running: $isRunning\n"
                        logText += "Status: $status\n"
                        logText += "Port: ${if (port > 0) port else "N/A"}\n"
                    } catch (e: Exception) {
                        logText += "✗ Error getting status: ${e.message}\n"
                    }
                }
            },
            enabled = isServiceBound,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Status")
        }

        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Log:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = logText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
