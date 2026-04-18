package com.example.sitconnect2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Keep logs explicit so boot-delivery failures are easy to spot in Logcat.
        Log.i("BootReceiver", "Received action: ${intent?.action}")

        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON"
        )

        if (intent?.action in actions) {
            try {
                val serviceIntent = Intent(context, AgentService::class.java).apply {
                    putExtra("from_boot", true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Log.i("BootReceiver", "Starting AgentService via startForegroundService()")
                    context.startForegroundService(serviceIntent)
                } else {
                    Log.i("BootReceiver", "Starting AgentService via startService()")
                    context.startService(serviceIntent)
                }
            } catch (e: SecurityException) {
                Log.e("BootReceiver", "Security exception starting service", e)
            } catch (e: IllegalStateException) {
                Log.e("BootReceiver", "Foreground service start not allowed at this moment", e)
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start service", e)
            }
        }
    }
}
