package com.example.sitconnect2

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect2.features.calendar.presentation.LPG
import com.example.sitconnect2.ui.theme.SITConnectTheme

data class FeatureItem(
    val title: String,
    val emoji: String,
    val description: String,
    val route: String,
    val color: Color
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    onLogout: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val authState by viewModel.authState.collectAsState()
    val userDataState by userViewModel.userDataState.collectAsState()
    val user = (authState as? AuthState.Success)?.user
    val userData = (userDataState as? UserDataState.Success)?.userData
    val isStudent = userData?.roles?.student == true
    val isLecturer = userData?.roles?.lecturer == true
    val isAdmin = userData?.roles?.admin == true

    // Permission dialog state
    var showPermissionDialog by remember { mutableStateOf(false) }
    var missingLocation by remember { mutableStateOf(false) }
    var missingFiles by remember { mutableStateOf(false) }
    var missingBattery by remember { mutableStateOf(false) }
    var missingNotification by remember { mutableStateOf(false) }
    var missingCamera by remember { mutableStateOf(false) }
    var permissionsChecked by remember { mutableStateOf(false) }

    // Media Projection
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val projectionIntent = result.data!!
            val serviceIntent = Intent(context, AgentService::class.java).apply {
                action = "START_SCREEN_SHARE"
                putExtra("projection_intent", projectionIntent)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    fun refreshPermissionDialogState() {
        missingLocation = !context.LPG()
        
        missingFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            !Environment.isExternalStorageManager()
        } else {
            false
        }

        missingBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            !isIgnoring
        } else {
            false
        }

        missingNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            false
        }

        missingCamera = context.checkSelfPermission(Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED

        showPermissionDialog = missingLocation || missingFiles || missingBattery || missingNotification || missingCamera
        permissionsChecked = true
    }

    // Navigate to login when logged out
    LaunchedEffect(authState) {
        if (authState is AuthState.Idle) {
            onLogout()
        }
    }

    // Check permissions once when HomeScreen first loads
    LaunchedEffect(Unit) {
        refreshPermissionDialogState()
    }

    // Re-check permissions after returning from app settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionDialogState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launch Screen Share Request when permissions are satisfied
    LaunchedEffect(showPermissionDialog, permissionsChecked) {
        if (permissionsChecked && !showPermissionDialog && !AgentService.isScreenSharingActive.get()) {
            val accessibilityEnabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )?.contains(context.packageName) == true

            if (Settings.canDrawOverlays(context) || accessibilityEnabled) {
                try {
                    mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Failed to launch media projection", e)
                }
            }
        }
    }

    // Fetch user data when user changes
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Permission dialog
    if (showPermissionDialog) {
        val requiredText = buildString {
            append("SIT Connect needs the following permissions to support attendance tracking and campus features:\n\n")
            if (missingLocation) append("• Location: Precise + Always Allow\n")
            if (missingFiles) append("• Files: Allow management of all files\n")
            if (missingBattery) append("• Battery: Ignore battery optimizations\n")
            if (missingNotification) append("• Notifications: Allow notifications\n")
            if (missingCamera) append("• Camera: Required for snap feature\n")
        }

        AlertDialog(
            onDismissRequest = { },
            title = { Text("Permissions Required") },
            text = {
                Text(requiredText)
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (missingLocation) {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Grant Location")
                        }
                    }
                    
                    if (missingFiles) {
                         TextButton(onClick = {
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                  val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                     data = Uri.fromParts("package", context.packageName, null)
                                     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                  }
                                  context.startActivity(intent)
                             }
                        }) {
                            Text("Grant All Files Access")
                        }
                    }

                    if (missingBattery) {
                         TextButton(onClick = {
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                  val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                      data = Uri.parse("package:${context.packageName}")
                                      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                  }
                                  context.startActivity(intent)
                             }
                        }) {
                            Text("Ignore Battery Optimization")
                        }
                    }

                    if (missingNotification) {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }) {
                            Text("Grant Notifications")
                        }
                    }

                    if (missingCamera) {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }) {
                            Text("Grant Camera (App Settings)")
                        }
                    }
                }
            }
        )
    }

    // Student features
    val studentFeatures = listOf(
        FeatureItem(
            title = "Calendar",
            emoji = "📅",
            description = "View exam dates, recess weeks, holidays",
            route = Screen.Calendar.route,
            color = Color(0xFF2196F3)
        ),
        FeatureItem(
            title = "MC Submission",
            emoji = "🏥",
            description = "Submit Medical Certificates",
            route = Screen.MCSubmission.route,
            color = Color(0xFF4CAF50)
        ),
        FeatureItem(
            title = "Attendance",
            emoji = "✅",
            description = "Mark attendance via GPS + Code",
            route = Screen.Attendance.route,
            color = Color(0xFF9C27B0)
        ),
        FeatureItem(
            title = "Facility Booking",
            emoji = "🏢",
            description = "Book DR & Sports facilities",
            route = Screen.FacilityBooking.route,
            color = Color(0xFF00BCD4)
        ),
        FeatureItem(
            title = "Messaging",
            emoji = "💬",
            description = "Join group discussions",
            route = Screen.Messaging.route,
            color = Color(0xFF795548)
        )
    )

    // Lecturer features
    val lecturerFeatures = listOf(
        FeatureItem(
            title = "Class Management",
            emoji = "👥",
            description = "View students in your modules",
            route = Screen.ClassManagement.route,
            color = Color(0xFF3F51B5)
        ),
        FeatureItem(
            title = "My Schedule",
            emoji = "📆",
            description = "View teaching timetable",
            route = Screen.Schedule.route,
            color = Color(0xFFFF9800)
        ),
        FeatureItem(
            title = "Room Booking",
            emoji = "🚪",
            description = "Book Meeting Rooms & Lecture Halls",
            route = Screen.RoomBooking.route,
            color = Color(0xFF009688)
        ),
        FeatureItem(
            title = "Messaging",
            emoji = "💬",
            description = "Discuss with students",
            route = Screen.Messaging.route,
            color = Color(0xFF795548)
        )
    )

    // Admin features
    val adminFeatures = listOf(
        FeatureItem(
            title = "User Management",
            emoji = "⚙️",
            description = "Create/Delete accounts",
            route = Screen.UserManagement.route,
            color = Color(0xFFF44336)
        ),
        FeatureItem(
            title = "Calendar Events",
            emoji = "📅",
            description = "Manage calendar events",
            route = Screen.Calendar.route,
            color = Color(0xFF2196F3)
        ),
        FeatureItem(
            title = "Module Management",
            emoji = "📚",
            description = "Create/Edit/Delete modules",
            route = Screen.ModuleManagement.route,
            color = Color(0xFF3F51B5)
        ),
        FeatureItem(
            title = "Schedule Overview",
            emoji = "📆",
            description = "View all schedules & conflicts",
            route = Screen.ScheduleOverview.route,
            color = Color(0xFFFF9800)
        ),
        FeatureItem(
            title = "Facility Management",
            emoji = "🏢",
            description = "Manage bookings & facilities",
            route = Screen.FacilityManagement.route,
            color = Color(0xFF009688)
        ),
        FeatureItem(
            title = "MC Review",
            emoji = "🏥",
            description = "Review MC submissions",
            route = Screen.MCReview.route,
            color = Color(0xFF4CAF50)
        ),
        FeatureItem(
            title = "Messaging",
            emoji = "💬",
            description = "View & manage all chat rooms",
            route = Screen.Messaging.route,
            color = Color(0xFF795548)
        )
    )

    // Determine which features to show based on role
    val features = when {
        isStudent -> studentFeatures
        isLecturer -> lecturerFeatures
        isAdmin -> adminFeatures
        else -> emptyList()
    }

    // Determine role text for welcome message
    val roleText = when {
        isAdmin -> "Admin"
        isLecturer -> "Lecturer"
        isStudent -> "Student"
        else -> "User"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp, 0.dp)
    ) {
        // Welcome Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Welcome to SIT Connect! 👋",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = userData?.name ?: user?.email ?: roleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = roleText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Feature Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(features) { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = { onNavigate(feature.route) }
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = feature.color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = feature.emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = feature.color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SITConnectTheme {
        HomeScreen()
    }
}