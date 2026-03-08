package com.example.sitconnect.securitydemo.malicious


import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build

fun Context.areAllLocationPermissionsGranted(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return fine && coarse && background
}