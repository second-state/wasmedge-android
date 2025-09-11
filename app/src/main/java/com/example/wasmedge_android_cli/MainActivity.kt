package com.example.wasmedge_android_cli

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wasmedge_android_cli.ui.theme.WasmedgeandroidcliTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*

class MainActivity : ComponentActivity() {
    private lateinit var serviceConnection: WasmEdgeServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize service connection
        serviceConnection = WasmEdgeServiceConnection(this)

        setContent {
            WasmedgeandroidcliTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        serviceConnection = serviceConnection,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.unbindService()
    }
}

@Composable
fun MainContent(
    serviceConnection: WasmEdgeServiceConnection,
    modifier: Modifier = Modifier,
) {
    var outputText by remember { mutableStateOf("Select an option below:\n") }
    var isServiceBound by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Not connected") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Bind to service on first composition
    LaunchedEffect(Unit) {
        serviceConnection.onServiceConnected = {
            isServiceBound = true
            outputText = outputText + "Service connected!\n"
        }
        serviceConnection.onServiceDisconnected = {
            isServiceBound = false
            outputText = outputText + "Service disconnected!\n"
        }
        serviceConnection.bindService()
    }

    // Periodically update server status
    LaunchedEffect(isServiceBound) {
        while (isServiceBound) {
            kotlinx.coroutines.delay(1000)
            serverStatus = serviceConnection.getApiServerStatus()
        }
    }

    // Auto-scroll to bottom when new content is added
    LaunchedEffect(outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status Display
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
            ) {
                Text(
                    text = "Service Status: ${if (isServiceBound) "Connected" else "Disconnected"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "API Server: $serverStatus",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (serviceConnection.startApiServer()) {
                            outputText = outputText + "Starting API server via service...\n"
                        } else {
                            outputText = outputText + "Failed to start API server via service\n"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = isServiceBound,
            ) {
                Text("Start Server")
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (serviceConnection.stopApiServer()) {
                            outputText = outputText + "Stopping API server...\n"
                        } else {
                            outputText = outputText + "Failed to stop API server\n"
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = isServiceBound,
            ) {
                Text("Stop Server")
            }
        }

        // Output display
        Card(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
        ) {
            Text(
                text = outputText,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(scrollState),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
        }
    }
}
