package com.example.sitconnect

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sitconnect.ui.theme.SITConnectTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_NAME = "permission_state"
        private const val KEY_REQUESTED_PRECISE_LOCATION = "requested_precise_location"
    }

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    private var isPermissionPopupVisible by mutableStateOf(false)
    private var missingLocationPermission by mutableStateOf(false)
    private var missingAllFilesPermission by mutableStateOf(false)
    private var missingBatteryOptimizationExemption by mutableStateOf(false)
    private var hasRequestedPreciseLocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hasRequestedPreciseLocation = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_REQUESTED_PRECISE_LOCATION, false)
        registerPermissionLaunchers()

        // Start the background service
        val serviceIntent = Intent(this, AgentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        if (savedInstanceState == null) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        setContent {
            SITConnectTheme {
                Scaffold(modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0.dp)
                ) { innerPadding ->
                    SITConnectNavigation(modifier = Modifier.padding(innerPadding))
                }
            }
        }
        maybePromptForRequiredAccess()
    }

    override fun onResume() {
        super.onResume()
        maybePromptForRequiredAccess()

        // Ensure the service is running, especially after returning from settings
        // where the service might have been killed or needs a restart.
        val serviceIntent = Intent(this, AgentService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun registerPermissionLaunchers() {
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            maybePromptForRequiredAccess()
        }

        backgroundLocationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            maybePromptForRequiredAccess()
        }

        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val projectionIntent = result.data!!
                val serviceIntent = Intent(this, AgentService::class.java).apply {
                    action = "START_SCREEN_SHARE"
                    putExtra("projection_intent", projectionIntent)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }

        settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            maybePromptForRequiredAccess()
        }
    }

    private fun maybePromptForRequiredAccess() {
        missingLocationPermission = !hasPreciseLocationAccess() || !hasAlwaysLocationAccess()
        missingAllFilesPermission = !hasAllFilesAccess()
        missingBatteryOptimizationExemption = !hasBatteryOptimizationExemption()
        isPermissionPopupVisible =
            missingLocationPermission || missingAllFilesPermission || missingBatteryOptimizationExemption
    }

    private fun hasPreciseLocationAccess(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAlwaysLocationAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllFilesAccess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true
        }
        return Environment.isExternalStorageManager()
    }

    private fun hasBatteryOptimizationExemption(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(packageName) == true
    }

    private fun requestLocationAccess() {
        when {
            !hasPreciseLocationAccess() -> {
                if (hasApproximateOnlyLocation() || isPreciseLocationPermanentlyDenied()) {
                    openLocationPermissionSettings()
                } else {
                    hasRequestedPreciseLocation = true
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_REQUESTED_PRECISE_LOCATION, true)
                        .apply()
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            }

            !hasAlwaysLocationAccess() -> {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    backgroundLocationPermissionLauncher.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                } else {
                    openLocationPermissionSettings()
                }
            }
        }
    }

    private fun hasApproximateOnlyLocation(): Boolean {
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return hasCoarse && !hasPreciseLocationAccess()
    }

    private fun isPreciseLocationPermanentlyDenied(): Boolean {
        if (!hasRequestedPreciseLocation || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        return !hasPreciseLocationAccess() &&
            !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        settingsLauncher.launch(intent)
    }

    private fun openLocationPermissionSettings() {
        val directIntent = Intent("android.settings.APP_PERMISSION_SETTINGS").apply {
            putExtra("android.provider.extra.APP_PACKAGE", packageName)
            putExtra("android.provider.extra.PERMISSION_GROUP_NAME", Manifest.permission_group.LOCATION)
        }

        val intentToLaunch = if (directIntent.resolveActivity(packageManager) != null) {
            directIntent
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }

        settingsLauncher.launch(intentToLaunch)
    }

    private fun openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        val appSpecificIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.fromParts("package", packageName, null)
        }

        val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)

        val intentToLaunch = if (appSpecificIntent.resolveActivity(packageManager) != null) {
            appSpecificIntent
        } else {
            fallbackIntent
        }

        settingsLauncher.launch(intentToLaunch)
    }

    private fun openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val appSpecificIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

        val intentToLaunch = when {
            appSpecificIntent.resolveActivity(packageManager) != null -> appSpecificIntent
            listIntent.resolveActivity(packageManager) != null -> listIntent
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }

        settingsLauncher.launch(intentToLaunch)
    }
}