package com.example.sitconnect2

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlin.system.exitProcess

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Setup global crash handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("C2App", "Uncaught exception detected in thread ${thread.name}", throwable)
            
            // Schedule a restart via AlarmManager (more robust than just broadcast during crash)
            val restartIntent = Intent(applicationContext, RestartReceiver::class.java).apply {
                action = "com.example.sitconnect.RESTART_SERVICE"
            }
            
            // Reduce delay to make restart immediate (was 5000L)
            val restartDelay = 100L

            // Use FLAG_CANCEL_CURRENT to ensure we have a fresh pending intent
            val pendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                0,
                restartIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            
            try {
                // Try scheduling an exact alarm (requires permission on Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + restartDelay,
                            pendingIntent
                        )
                    } else {
                        // Fallback if permission not granted: inexact alarm
                        alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + restartDelay,
                            pendingIntent
                        )
                    }
                } else {
                    // Pre-Android 12, exact alarms are allowed by default
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + restartDelay,
                        pendingIntent
                    )
                }
            } catch (e: SecurityException) {
                // Fallback for security exceptions
                Log.e("C2App", "SecurityException scheduling exact alarm, using inexact", e)
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + restartDelay,
                    pendingIntent
                )
            }
            
            // FAIL SILENTLY: Do NOT invoke the default system handler.
            // Invoking defaultHandler shows the "App has stopped" dialog and triggers Android's 
            // "restart loop protection" which stops the app from restarting after a few crashes.
            Log.e("C2App", "Silently killing process to allow AlarmManager restart.")
            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }
}
