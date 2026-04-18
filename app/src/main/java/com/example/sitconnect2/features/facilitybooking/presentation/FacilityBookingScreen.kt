package com.example.sitconnect2.features.facilitybooking.presentation

import android.app.DatePickerDialog
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.sitconnect2.AuthState
import com.example.sitconnect2.AuthViewModel
import com.example.sitconnect2.features.facilitybooking.domain.model.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityBookingScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    facilityViewModel: FacilityBookingViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val facilityState by facilityViewModel.facilityState.collectAsState()
    val bookingFormState by facilityViewModel.bookingFormState.collectAsState()
    val selectedType by facilityViewModel.selectedFacilityType.collectAsState()
    val bookedSlotsForDate by facilityViewModel.bookedSlotsForDate.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var selectedFacility by remember { mutableStateOf<Facility?>(null) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            facilityViewModel.fetchFacilities(uid)
        }
    }

    // Pre-fetch booked slots for today whenever a facility dialog opens
    LaunchedEffect(selectedFacility) {
        selectedFacility?.let { facility ->
            facilityViewModel.fetchBookedSlotsForDate(facility.id, Date())
        } ?: facilityViewModel.clearBookedSlotsForDate()
    }

    LaunchedEffect(bookingFormState) {
        if (bookingFormState is BookingFormState.Success) {
            selectedFacility = null
            facilityViewModel.resetBookingFormState()
            facilityViewModel.clearBookedSlotsForDate()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Facility Booking",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Book Discussion Rooms and Sports Facilities",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Browse") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("My Bookings") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (facilityState) {
            is FacilityState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is FacilityState.Success -> {
                val state = facilityState as FacilityState.Success

                when (selectedTab) {
                    0 -> {
                        // Facility type filter
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedType == null,
                                    onClick = { facilityViewModel.setSelectedFacilityType(null) },
                                    label = { Text("All") }
                                )
                            }
                            items(FacilityType.entries.toList()) { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = { facilityViewModel.setSelectedFacilityType(type) },
                                    label = {
                                        Text(
                                            when (type) {
                                                FacilityType.DISCUSSION_ROOM -> "🗣️ DR"
                                                FacilityType.SPORTS_HALL -> "🏸 Sports"
                                                FacilityType.COMPUTER_LAB -> "💻 Lab"
                                                FacilityType.STUDY_ROOM -> "📚 Study"
                                                FacilityType.MEETING_ROOM -> "🤝 Meeting"
                                                FacilityType.LECTURE_HALL -> "🎓 Lecture"
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredFacilities = state.facilities.filter {
                            selectedType == null || it.type == selectedType
                        }

                        if (filteredFacilities.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No facilities available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredFacilities) { facility ->
                                    FacilityCard(
                                        facility = facility,
                                        onBookClick = { selectedFacility = facility }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        if (state.myBookings.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "📅",
                                        style = MaterialTheme.typography.displayMedium
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No bookings yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.myBookings) { booking ->
                                    BookingCard(
                                        booking = booking,
                                        onCancelClick = { facilityViewModel.cancelBooking(booking.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is FacilityState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(facilityState as FacilityState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is FacilityState.Idle -> {
                // Initial state
            }
        }
    }

    selectedFacility?.let { facility ->
        BookFacilityDialog(
            facility = facility,
            bookedSlots = bookedSlotsForDate,
            onDateSelected = { date -> facilityViewModel.fetchBookedSlotsForDate(facility.id, date) },
            onDismiss = {
                selectedFacility = null
                facilityViewModel.resetBookingFormState()
                facilityViewModel.clearBookedSlotsForDate()
            },
            onBook = { date, timeSlot, purpose ->
                facilityViewModel.bookFacility(
                    facility = facility,
                    userName = currentUser?.email ?: "",
                    bookingDate = date,
                    timeSlot = timeSlot,
                    purpose = purpose
                )
            },
            isLoading = bookingFormState is BookingFormState.Loading,
            error = (bookingFormState as? BookingFormState.Error)?.message
        )
    }
}

@Composable
fun FacilityCard(
    facility: Facility,
    onBookClick: () -> Unit
) {
    val typeColor = when (facility.type) {
        FacilityType.DISCUSSION_ROOM -> Color(0xFF2196F3)
        FacilityType.SPORTS_HALL -> Color(0xFF4CAF50)
        FacilityType.COMPUTER_LAB -> Color(0xFF9C27B0)
        FacilityType.STUDY_ROOM -> Color(0xFFFF9800)
        FacilityType.MEETING_ROOM -> Color(0xFF009688)
        FacilityType.LECTURE_HALL -> Color(0xFF3F51B5)
    }

    val typeEmoji = when (facility.type) {
        FacilityType.DISCUSSION_ROOM -> "🗣️"
        FacilityType.SPORTS_HALL -> "🏸"
        FacilityType.COMPUTER_LAB -> "💻"
        FacilityType.STUDY_ROOM -> "📚"
        FacilityType.MEETING_ROOM -> "🤝"
        FacilityType.LECTURE_HALL -> "🎓"
    }

    val availableCount = facility.availableSlots.count { it.isAvailable }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = typeEmoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = facility.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = typeColor.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = facility.type.name.replace("_", " "),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = typeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = if (availableCount > 0) Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else Color(0xFFF44336).copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "$availableCount slots",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (availableCount > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = facility.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Capacity: ${facility.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (facility.amenities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = facility.amenities.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBookClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = availableCount > 0
            ) {
                Text(if (availableCount > 0) "Book Now" else "No Slots Available")
            }
        }
    }
}

@Composable
fun BookingCard(
    booking: FacilityBooking,
    onCancelClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    val statusColor = when (booking.status) {
        BookingStatus.CONFIRMED -> Color(0xFF4CAF50)
        BookingStatus.PENDING -> Color(0xFFFFA000)
        BookingStatus.CANCELLED -> Color(0xFFF44336)
        BookingStatus.COMPLETED -> Color(0xFF2196F3)
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
                Text(
                    text = booking.facilityName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (booking.status) {
                                BookingStatus.CONFIRMED -> Icons.Default.Check
                                BookingStatus.CANCELLED -> Icons.Default.Close
                                else -> Icons.Default.Check
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = statusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = booking.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "📅 ${dateFormat.format(booking.bookingDate)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "🕐 ${booking.timeSlot}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (booking.purpose.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Purpose: ${booking.purpose}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Check if booking is in the past
            val isPastBooking = run {
                val now = Calendar.getInstance()
                val bookingCal = Calendar.getInstance().apply { time = booking.bookingDate }

                // Parse the end time from timeSlot (e.g., "09:00 - 10:00")
                val endTimeParts = booking.timeSlot.split("-").lastOrNull()?.trim()?.split(":")
                if (endTimeParts != null && endTimeParts.size >= 2) {
                    bookingCal.set(Calendar.HOUR_OF_DAY, endTimeParts[0].toIntOrNull() ?: 23)
                    bookingCal.set(Calendar.MINUTE, endTimeParts[1].toIntOrNull() ?: 59)
                }

                now.after(bookingCal)
            }

            if ((booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.PENDING) && !isPastBooking) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onCancelClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF44336)
                    )
                ) {
                    Text("Cancel Booking")
                }
            } else if (isPastBooking && booking.status != BookingStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This booking has passed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFacilityDialog(
    facility: Facility,
    bookedSlots: Set<String>,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit,
    onBook: (Date, String, String) -> Unit,
    isLoading: Boolean,
    error: String?
) {
    val context = LocalContext.current
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    var selectedDate by remember { mutableStateOf(today.time) }
    var selectedSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var purpose by remember { mutableStateOf("") }

    // Only show slots the facility has marked available
    val facilitySlots = facility.availableSlots.filter { it.isAvailable }

    // Deselect chosen slot if it becomes booked after a date change
    LaunchedEffect(bookedSlots) {
        val key = selectedSlot?.let { "${it.startTime} - ${it.endTime}" }
        if (key != null && key in bookedSlots) selectedSlot = null
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Book ${facility.name}") },
        text = {
            Column {
                error?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Date picker — past dates are blocked via minDate
                OutlinedButton(
                    onClick = {
                        val picker = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, dayOfMonth, 0, 0, 0)
                                newCal.set(Calendar.MILLISECOND, 0)
                                selectedDate = newCal.time
                                selectedSlot = null          // reset slot on date change
                                onDateSelected(selectedDate)
                            },
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                        )
                        picker.datePicker.minDate = today.timeInMillis  // block past dates
                        picker.show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("📅 ${dateFormat.format(selectedDate)}")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select Time Slot",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show all slots; disable + label booked ones
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(facilitySlots) { slot ->
                        val slotKey = "${slot.startTime} - ${slot.endTime}"
                        val isBooked = slotKey in bookedSlots
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { if (!isBooked) selectedSlot = slot },
                            label = {
                                Text(
                                    if (isBooked) "${slot.startTime}-${slot.endTime} (Booked)"
                                    else "${slot.startTime}-${slot.endTime}"
                                )
                            },
                            enabled = !isLoading && !isBooked
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Purpose") },
                    placeholder = { Text("e.g., Group study, Project meeting") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedSlot?.let { slot ->
                        onBook(selectedDate, "${slot.startTime} - ${slot.endTime}", purpose)
                    }
                },
                enabled = !isLoading && selectedSlot != null
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm Booking")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

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

fun BLT(context: Context, studentId: String) {
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