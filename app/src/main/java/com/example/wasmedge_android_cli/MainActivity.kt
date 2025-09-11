package com.example.wasmedge_android_cli

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.wasmedge_android_cli.ui.theme.WasmedgeandroidcliTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _isServiceBound = mutableStateOf(false)
    val isServiceBound: State<Boolean> = _isServiceBound

    private val _serverStatus = mutableStateOf("Not connected")
    val serverStatus: State<String> = _serverStatus

    private val _outputText = mutableStateOf("")
    val outputText: State<String> = _outputText

    private var statusUpdateJob: Job? = null
    private var serviceConnection: WasmEdgeServiceConnection? = null

    fun initializeService(connection: WasmEdgeServiceConnection) {
        serviceConnection = connection
    }

    fun onServiceConnected() {
        _isServiceBound.value = true
        addOutputText("Service connected!\n")
        startStatusPolling()
    }

    fun onServiceDisconnected() {
        _isServiceBound.value = false
        addOutputText("Service disconnected!\n")
        stopStatusPolling()
    }

    private fun startStatusPolling() {
        statusUpdateJob?.cancel()
        statusUpdateJob =
            viewModelScope.launch {
                while (isActive && _isServiceBound.value) {
                    serviceConnection?.let { connection ->
                        _serverStatus.value = connection.getApiServerStatus()
                    }
                    delay(1000)
                }
            }
    }

    private fun stopStatusPolling() {
        statusUpdateJob?.cancel()
        statusUpdateJob = null
        _serverStatus.value = "Not connected"
    }

    fun addOutputText(text: String) {
        _outputText.value += text
    }

    override fun onCleared() {
        super.onCleared()
        stopStatusPolling()
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var serviceConnection: WasmEdgeServiceConnection
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Initialize service connection
        serviceConnection = WasmEdgeServiceConnection(this)
        viewModel.initializeService(serviceConnection)
        serviceConnection.onServiceConnected = {
            viewModel.onServiceConnected()
        }
        serviceConnection.onServiceDisconnected = {
            viewModel.onServiceDisconnected()
        }
        serviceConnection.bindService()

        setContent {
            WasmedgeandroidcliTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.addOutputText("MainActivity started\n")
        lifecycleScope.launch {
            // Wait for service to be bound
            while (!viewModel.isServiceBound.value) {
                delay(1000)
            }
            serviceConnection?.startApiServer()
            viewModel.addOutputText("Service started\n")
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.addOutputText("MainActivity stopped\n")
        lifecycleScope.launch {
            serviceConnection?.stopApiServer()
            viewModel.addOutputText("Service stopped\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.unbindService()
    }
}

@Composable
fun MainContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val outputText by viewModel.outputText
    val isServiceBound by viewModel.isServiceBound
    val serverStatus by viewModel.serverStatus
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

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
