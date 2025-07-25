# WasmEdge Android Service

This project provides a bounded service that allows other Android applications to control the WasmEdge runtime and API server.

## Features

- **Bounded Service**: Other apps can bind to the WasmEdge service and control it remotely
- **Simple Interface**: Clean Kotlin interface for inter-process communication
- **API Server Control**: Start/stop the WasmEdge API server with custom parameters
- **Status Monitoring**: Get real-time status and port information
- **Process Management**: Proper lifecycle management of the WasmEdge subprocess

## Service Interface

The service exposes the following methods through the `IWasmEdgeServiceInterface`:

### Methods

- `startApiServer()`: Start the API server with default parameters
- `startApiServerWithParams(String modelFile, String templateType, int contextSize, int port)`: Start with custom parameters
- `stopApiServer()`: Stop the running API server
- `isApiServerRunning()`: Check if the server is currently running
- `getApiServerStatus()`: Get current status message
- `getServerPort()`: Get the port number the server is running on

## Using the Service in Your App

### 1. Copy the Interface

Copy the `IWasmEdgeServiceInterface.kt` file to your project:

```kotlin
interface IWasmEdgeServiceInterface {
    fun startApiServer(): Boolean
    fun startApiServerWithParams(modelFile: String, templateType: String, contextSize: Int, port: Int): Boolean
    fun stopApiServer(): Boolean
    fun isApiServerRunning(): Boolean
    fun getApiServerStatus(): String
    fun getServerPort(): Int
}
```

### 2. Connect to the Service

```kotlin
class YourActivity : ComponentActivity() {
    private var wasmEdgeService: IWasmEdgeServiceInterface? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wasmEdgeService = service as? IWasmEdgeServiceInterface
            isBound = true
            Log.d("YourApp", "WasmEdge service connected")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            wasmEdgeService = null
            isBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bind to the service
        val intent = Intent().apply {
            component = ComponentName(
                "com.example.wasmedge_android_cli",
                "com.example.wasmedge_android_cli.WasmEdgeService"
            )
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun startServer() {
        wasmEdgeService?.startApiServer()
    }
    
    private fun startServerWithCustomParams() {
        wasmEdgeService?.startApiServerWithParams(
            "your-model.gguf",
            "your-template",
            2048,
            8081
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }
}
```

## Service Configuration

The service is configured in the AndroidManifest.xml as:

```xml
<service
    android:name=".WasmEdgeService"
    android:exported="true"
    android:enabled="true">
    <intent-filter>
        <action android:name="com.example.wasmedge_android_cli.WASMEDGE_SERVICE" />
    </intent-filter>
</service>
```

## Example Usage

See `ExampleClientActivity.kt` for a complete example of how to create a client application that controls the WasmEdge service.

## Default Server Parameters

When using `startApiServer()` without parameters, the following defaults are used:

- **Model File**: `gemma-3-1b-it-Q4_K_M.gguf`
- **Template Type**: `gemma-3`
- **Context Size**: `1024`
- **Port**: `8080`

## Error Handling

All service methods return boolean values indicating success/failure. Additionally, you can check the server status using `getApiServerStatus()` to get detailed information about any errors.

## Requirements

- Android API level as specified in your app's build.gradle
- The WasmEdge Android app must be installed on the device
- Proper permissions for inter-process communication

## Security Considerations

- The service is exported and can be accessed by other apps
- Consider adding custom permissions if you need to restrict access
- Validate all input parameters when calling service methods

## Troubleshooting

1. **Service not found**: Ensure the WasmEdge Android app is installed
2. **Binding fails**: Check if the service is properly declared in AndroidManifest.xml
3. **Server won't start**: Check logs for file permissions and asset copying issues
4. **Port conflicts**: Try using different port numbers with `startApiServerWithParams()`
