package com.example.sitconnect.features.facilitybooking.presentation

import android.app.DatePickerDialog
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
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.facilitybooking.domain.model.*
import java.text.SimpleDateFormat
import java.util.*

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

    var selectedTab by remember { mutableStateOf(0) }
    var selectedFacility by remember { mutableStateOf<Facility?>(null) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            facilityViewModel.fetchFacilities(uid)
        }
    }

    LaunchedEffect(bookingFormState) {
        if (bookingFormState is BookingFormState.Success) {
            selectedFacility = null
            facilityViewModel.resetBookingFormState()
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
            userName = currentUser?.email ?: "",
            onDismiss = {
                selectedFacility = null
                facilityViewModel.resetBookingFormState()
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

            if (booking.status == BookingStatus.CONFIRMED || booking.status == BookingStatus.PENDING) {
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookFacilityDialog(
    facility: Facility,
    userName: String,
    onDismiss: () -> Unit,
    onBook: (Date, String, String) -> Unit,
    isLoading: Boolean,
    error: String?
) {
    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    var selectedDate by remember { mutableStateOf(Date()) }
    var selectedSlot by remember { mutableStateOf<TimeSlot?>(null) }
    var purpose by remember { mutableStateOf("") }

    val availableSlots = facility.availableSlots.filter { it.isAvailable }

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

                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                calendar.set(year, month, dayOfMonth)
                                selectedDate = calendar.time
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
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

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableSlots) { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text("${slot.startTime}-${slot.endTime}") },
                            enabled = !isLoading
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Confirm Booking")
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

