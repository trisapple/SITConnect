package com.example.sitconnect2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sitconnect2.ui.theme.SITConnectTheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val PREFS_NAME = "permission_state"
        private const val KEY_REQUESTED_PRECISE_LOCATION = "requested_precise_location"
    }

    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backgroundLocationPermissionLauncher: ActivityResultLauncher<String>
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
}