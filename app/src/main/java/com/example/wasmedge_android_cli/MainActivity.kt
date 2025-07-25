package com.example.wasmedge_android_cli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.*
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
import android.util.Log

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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.unbindService()
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
            Log.e("WasmEdge", "Error copying files: ${e.message}")
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
                        Log.d("WasmEdge", "Set executable permission for: $fileName")
                    }
                    
                    Log.d("WasmEdge", "Copied: $fileName (${targetFile.length()} bytes)")
                } catch (ioException: Exception) {
                    // If opening as file fails, it's probably a directory
                    val subFiles = assets.list(assetFilePath)
                    if (subFiles != null) {
                        targetFile.mkdirs()
                        Log.d("WasmEdge", "Created directory: $fileName")
                        copyAssetFolder(assetFilePath, targetFile)
                    } else {
                        Log.e("WasmEdge", "Could not process $fileName as file or directory")
                    }
                }
            } catch (e: Exception) {
                Log.e("WasmEdge", "Error copying $fileName: ${e.message}")
            }
        }
    }

    suspend fun executeShellScriptStreaming(onNewLine: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                copyFilesFromAssetsToInternal()
                
                val wasmedgeFile = File(filesDir, "llamaedge/wasmedge")
                val workingDir = File(filesDir, "llamaedge")
            
                withContext(Dispatchers.Main) {
                    onNewLine("All required files present. Starting WasmEdge...")
                }
                
                Log.d("WasmEdge", "All required files present. Trying to execute via shell...")
                Log.d("WasmEdge", "Working directory: ${workingDir.absolutePath}")
                Log.d("WasmEdge", "WasmEdge file: ${wasmedgeFile.absolutePath}")
                
                // Create environment array similar to Termux
                val env = arrayOf(
                    "LD_LIBRARY_PATH=${workingDir.absolutePath}",
                    "WASMEDGE_PLUGIN_PATH=${workingDir.absolutePath}"
                )
                
                val process = Runtime.getRuntime().exec(
                    arrayOf(
                        wasmedgeFile.absolutePath,
                        "--dir", ".:.",
                        "--nn-preload", "default:GGML:AUTO:gemma-3-1b-it-Q4_K_M.gguf",
                        "llama-api-server.wasm",
                        "--prompt-template", "gemma-3",
                        "--ctx-size", "1024",
                        "--port", "8080"
                    ),
                    env,
                    workingDir
                )
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d("WasmEdge", line!!)
                    withContext(Dispatchers.Main) {
                        onNewLine(line!!)
                    }
                }
                
                Log.d("WasmEdge", "Process completed")
                withContext(Dispatchers.Main) {
                    onNewLine("Process completed")
                }
            } catch (e: Exception) {
                val errorMsg = "Error: ${e.message}"
                Log.e("WasmEdge", errorMsg)
                withContext(Dispatchers.Main) {
                    onNewLine("Error: $errorMsg")
                }
            }
        }
    }
}

@Composable
fun MainContent(
    serviceConnection: WasmEdgeServiceConnection,
    modifier: Modifier = Modifier
) {
    var outputText by remember { mutableStateOf("Select an option below:\n") }
    var isServiceBound by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf("Not connected") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainActivity = context as MainActivity
    
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
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status Display
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Service Status: ${if (isServiceBound) "Connected" else "Disconnected"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "API Server: $serverStatus",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                enabled = isServiceBound
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
                enabled = isServiceBound
            ) {
                Text("Stop Server")
            }
        }
        
        Button(
            onClick = {
                coroutineScope.launch {
                    outputText = outputText + "Starting LlamaEdge directly...\n"
                    mainActivity.executeShellScriptStreaming { newLine: String ->
                        outputText = outputText + newLine + "\n"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run LlamaEdge Direct (fallback)")
        }
        
        // Output display
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp)
        ) {
            Text(
                text = outputText,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(scrollState),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ShellOutput(modifier: Modifier = Modifier) {
    var outputText by remember { mutableStateOf("Starting LlamaEdge...\n") }
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    LaunchedEffect(Unit) {
        val activity = context as MainActivity
        activity.executeShellScriptStreaming { newLine ->
            outputText += newLine + "\n"
        }
    }
    
    // Auto-scroll to bottom when new content is added
    LaunchedEffect(outputText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Text(
        text = outputText,
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
}
