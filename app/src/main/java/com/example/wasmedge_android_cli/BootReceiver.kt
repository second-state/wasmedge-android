package com.example.wasmedge_android_cli

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver to restart the WasmEdge service on device boot
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i("BootReceiver", "Device boot completed, checking if service should be started")
            
            // Check if the service should be auto-started based on user preference
            val sharedPreferences = context.getSharedPreferences("WasmEdgeServicePrefs", Context.MODE_PRIVATE)
            val shouldAutoStart = sharedPreferences.getBoolean("auto_start_on_boot", false)
            
            if (shouldAutoStart) {
                Log.i("BootReceiver", "Starting WasmEdge service on boot")
                
                val serviceIntent = Intent(context, WasmEdgeService::class.java)
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    
                    Log.i("BootReceiver", "WasmEdge service start requested")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start WasmEdge service on boot: ${e.message}")
                }
            } else {
                Log.d("BootReceiver", "Auto-start on boot is disabled")
            }
        }
    }
}