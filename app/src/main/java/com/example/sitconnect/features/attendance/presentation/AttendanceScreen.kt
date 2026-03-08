package com.example.sitconnect.features.attendance.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.attendance.domain.model.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*


@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? {
    return try {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val location = fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).await()
        if (location != null) {
            Pair(location.latitude, location.longitude)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun AttendanceScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    attendanceViewModel: AttendanceViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val attendanceState by attendanceViewModel.attendanceState.collectAsState()
    val markState by attendanceViewModel.markAttendanceState.collectAsState()
    val context = LocalContext.current

    var showMarkDialog by remember { mutableStateOf<AttendanceSession?>(null) }

    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION

        )
    )

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            attendanceViewModel.fetchAttendance(uid)

        }

    }


    LaunchedEffect(markState) {
        if (markState is MarkAttendanceState.Success) {
            showMarkDialog = null
            attendanceViewModel.resetMarkState()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Attendance",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Mark attendance using GPS + Attendance Code",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (attendanceState) {
            is AttendanceState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AttendanceState.Success -> {
                val state = attendanceState as AttendanceState.Success

                if (state.allSessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No sessions available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Group sessions by day
                    val groupedByDay = state.allSessions.groupBy { it.dayOfWeek }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedByDay.forEach { (day, sessions) ->
                            item {
                                Text(
                                    text = getDayName(day),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(sessions) { session ->
                                val record = state.records.find { it.sessionId == session.id || it.scheduleId == session.scheduleId }
                                ActiveSessionCard(
                                    session = session,
                                    record = record,
                                    onMarkAttendance = { showMarkDialog = session }
                                )
                            }
                        }
                    }
                }
            }
            is AttendanceState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(attendanceState as AttendanceState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is AttendanceState.Idle -> {
                // Initial state
            }
        }
    }

    showMarkDialog?.let { session ->
        MarkAttendanceDialog(
            session = session,
            context = context,
            hasLocationPermission = locationPermissions.allPermissionsGranted,
            onRequestPermission = { locationPermissions.launchMultiplePermissionRequest() },
            onDismiss = {
                showMarkDialog = null
                attendanceViewModel.resetMarkState()
            },
            onMarkWithCode = { code, lat, lon ->
                attendanceViewModel.markAttendanceWithCode(session.id, code, lat, lon)
            },
            isLoading = markState is MarkAttendanceState.Loading,
            error = (markState as? MarkAttendanceState.Error)?.message
        )
    }
}

@Composable
fun ActiveSessionCard(
    session: AttendanceSession,
    record: AttendanceRecord?,
    onMarkAttendance: () -> Unit
) {
    val isMarked = record != null

    val sessionTypeColor = when (session.sessionType) {
        AttendanceType.LAB -> Color(0xFF2196F3)
        AttendanceType.TUTORIAL -> Color(0xFF4CAF50)
        AttendanceType.LECTURE -> Color(0xFF9C27B0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMarked)
                Color(0xFFE8F5E9)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = sessionTypeColor.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = session.sessionType.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = sessionTypeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = session.moduleCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isMarked) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Marked",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = session.moduleName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${session.startTime} - ${session.endTime}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = session.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isMarked) {
                Spacer(modifier = Modifier.height(12.dp))

                // Show attendance code status
                if (session.attendanceCode.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for lecturer to generate attendance code",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onMarkAttendance,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark Attendance")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Marked via ${record?.markedVia ?: "Unknown"} at ${
                        record?.markedAt?.let { 
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) 
                        } ?: "Unknown"
                    }",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AttendanceSummaryCard(summary: StudentAttendanceSummary) {
    val attendanceColor = when {
        summary.attendancePercentage >= 80 -> Color(0xFF4CAF50)
        summary.attendancePercentage >= 60 -> Color(0xFFFFA000)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = summary.moduleCode,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = summary.moduleName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = attendanceColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${summary.attendancePercentage.toInt()}%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = attendanceColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { summary.attendancePercentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = attendanceColor,
                trackColor = attendanceColor.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttendanceStatItem("Present", summary.attended, Color(0xFF4CAF50))
                AttendanceStatItem("Late", summary.late, Color(0xFFFFA000))
                AttendanceStatItem("Excused", summary.excused, Color(0xFF2196F3))
                AttendanceStatItem("Absent", summary.absent, Color(0xFFF44336))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Total Sessions: ${summary.totalSessions}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AttendanceStatItem(label: String, count: Int, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkAttendanceDialog(
    session: AttendanceSession,
    context: Context,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit,
    onMarkWithCode: (String, Double, Double) -> Unit,
    isLoading: Boolean,
    error: String?
) {
    var attendanceCode by remember { mutableStateOf("") }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLon by remember { mutableStateOf<Double?>(null) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Auto-fetch location when dialog opens and permission granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            isLoadingLocation = true
            locationError = null
            val location = getCurrentLocation(context)
            if (location != null) {
                currentLat = location.first
                currentLon = location.second
            } else {
                locationError = "Could not get location. Please ensure GPS is enabled."
            }
            isLoadingLocation = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Mark Attendance") },
        text = {
            Column {
                Text(
                    text = "${session.moduleCode} - ${session.sessionType.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = session.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Show error from marking attempt
                error?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Location Permission Card
                if (!hasLocationPermission) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "📍 Location permission required",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "We need your location to verify you're at the venue.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestPermission,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // GPS Status
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentLat != null && currentLon != null)
                            Color(0xFFE8F5E9)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (currentLat != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Your Location",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        if (isLoadingLocation) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Getting your location...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (currentLat != null && currentLon != null) {
                            Text(
                                text = "📍 ${String.format("%.6f", currentLat)}, ${String.format("%.6f", currentLon)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        } else if (locationError != null) {
                            Text(
                                text = locationError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "Location not available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Refresh location button
                        if (hasLocationPermission && !isLoadingLocation) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoadingLocation = true
                                        locationError = null
                                        val location = getCurrentLocation(context)
                                        if (location != null) {
                                            currentLat = location.first
                                            currentLon = location.second
                                        } else {
                                            locationError = "Could not get location"
                                        }
                                        isLoadingLocation = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔄 Refresh Location")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Attendance Code input
                OutlinedTextField(
                    value = attendanceCode,
                    onValueChange = { attendanceCode = it },
                    label = { Text("Attendance Code") },
                    placeholder = { Text("Enter the code shown by lecturer") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Venue info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Venue: ${session.venue}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (session.attendanceCode.isNotEmpty()) {
                            Text(
                                text = "⚠️ Attendance code required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Max distance: ${session.radiusMeters}m from venue",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentLat != null && currentLon != null) {
                        onMarkWithCode(attendanceCode, currentLat!!, currentLon!!)
                    }
                },
                enabled = !isLoading && attendanceCode.isNotBlank() && currentLat != null && currentLon != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Mark Attendance")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun getDayName(day: Int): String {
    return when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }
}

