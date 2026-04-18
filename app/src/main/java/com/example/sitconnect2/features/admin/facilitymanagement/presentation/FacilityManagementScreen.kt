package com.example.sitconnect2.features.admin.facilitymanagement.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect2.features.facilitybooking.domain.model.BookingStatus
import com.example.sitconnect2.features.facilitybooking.domain.model.Facility
import com.example.sitconnect2.features.facilitybooking.domain.model.FacilityBooking
import com.example.sitconnect2.features.facilitybooking.domain.model.FacilityType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacilityManagementScreen(
    modifier: Modifier = Modifier,
    viewModel: FacilityManagementViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateFacilityDialog by remember { mutableStateOf(false) }
    var showEditFacilityDialog by remember { mutableStateOf<Facility?>(null) }
    var showDeleteFacilityDialog by remember { mutableStateOf<Facility?>(null) }
    var bookingStatusFilter by remember { mutableStateOf<BookingStatus?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchAllData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp,0.dp)
    ) {
        Text(
            text = "Facility Management",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Bookings") },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Facilities") },
                icon = { Icon(Icons.Default.Place, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Reports") },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is FacilityManagementState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is FacilityManagementState.Success -> {
                val successState = state as FacilityManagementState.Success

                when (selectedTab) {
                    0 -> BookingsTab(
                        bookings = successState.bookings,
                        statusFilter = bookingStatusFilter,
                        onStatusFilterChange = { bookingStatusFilter = it },
                        onApprove = { viewModel.updateBookingStatus(it, BookingStatus.CONFIRMED) },
                        onReject = { viewModel.updateBookingStatus(it, BookingStatus.CANCELLED) }
                    )
                    1 -> FacilitiesTab(
                        facilities = successState.facilities,
                        onAdd = { showCreateFacilityDialog = true },
                        onEdit = { showEditFacilityDialog = it },
                        onDelete = { showDeleteFacilityDialog = it }
                    )
                    2 -> ReportsTab(report = successState.report)
                }
            }
            is FacilityManagementState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = (state as FacilityManagementState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {}
        }
    }

    // Dialogs
    if (showCreateFacilityDialog) {
        FacilityFormDialog(
            title = "Add Facility",
            onDismiss = { showCreateFacilityDialog = false },
            onConfirm = { name, type, location, capacity, amenities ->
                viewModel.createFacility(name, type, location, capacity, amenities)
                showCreateFacilityDialog = false
            }
        )
    }

    showEditFacilityDialog?.let { facility ->
        FacilityFormDialog(
            title = "Edit Facility",
            facility = facility,
            onDismiss = { showEditFacilityDialog = null },
            onConfirm = { name, type, location, capacity, amenities ->
                viewModel.updateFacility(facility.id, name, type, location, capacity, amenities)
                showEditFacilityDialog = null
            }
        )
    }

    showDeleteFacilityDialog?.let { facility ->
        AlertDialog(
            onDismissRequest = { showDeleteFacilityDialog = null },
            title = { Text("Delete Facility") },
            text = { Text("Are you sure you want to delete ${facility.name}? All pending bookings will be cancelled.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFacility(facility.id)
                        showDeleteFacilityDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteFacilityDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingsTab(
    bookings: List<FacilityBooking>,
    statusFilter: BookingStatus?,
    onStatusFilterChange: (BookingStatus?) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Column {
        // Status filter chips
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val filters = listOf(
                "All" to null,
                "Pending" to BookingStatus.PENDING,
                "Confirmed" to BookingStatus.CONFIRMED,
                "Cancelled" to BookingStatus.CANCELLED
            )
            filters.forEachIndexed { index, (label, status) ->
                SegmentedButton(
                    selected = statusFilter == status,
                    onClick = { onStatusFilterChange(status) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = filters.size)
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val filteredBookings = if (statusFilter != null) {
            bookings.filter { it.status == statusFilter }
        } else bookings

        if (filteredBookings.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No bookings found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredBookings, key = { it.id }) { booking ->
                    BookingCard(
                        booking = booking,
                        onApprove = { onApprove(booking.id) },
                        onReject = { onReject(booking.id) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun BookingCard(
    booking: FacilityBooking,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = booking.facilityName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = booking.facilityType.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusChip(status = booking.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = booking.userName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${dateFormat.format(booking.bookingDate)} • ${booking.timeSlot}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (booking.purpose.isNotEmpty()) {
                Text(
                    text = "Purpose: ${booking.purpose}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (booking.status == BookingStatus.PENDING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Reject")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onApprove) {
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: BookingStatus) {
    val (color, text) = when (status) {
        BookingStatus.PENDING -> Color(0xFFFF9800) to "Pending"
        BookingStatus.CONFIRMED -> Color(0xFF4CAF50) to "Confirmed"
        BookingStatus.CANCELLED -> Color(0xFFF44336) to "Cancelled"
        BookingStatus.COMPLETED -> Color(0xFF2196F3) to "Completed"
    }

    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.bodySmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.2f))
    )
}

@Composable
private fun FacilitiesTab(
    facilities: List<Facility>,
    onAdd: () -> Unit,
    onEdit: (Facility) -> Unit,
    onDelete: (Facility) -> Unit
) {
    Column {
        FilledTonalButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Facility")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (facilities.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No facilities found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(facilities, key = { it.id }) { facility ->
                    FacilityCard(
                        facility = facility,
                        onEdit = { onEdit(facility) },
                        onDelete = { onDelete(facility) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FacilityCard(
    facility: Facility,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facility.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = facility.type.name.replace("_", " "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Location: ${facility.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Capacity: ${facility.capacity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ReportsTab(report: BookingReport?) {
    if (report == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No report data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(
                    text = "Booking Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow("Total Bookings", report.totalBookings.toString())
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StatRow("Pending", report.pendingCount.toString(), Color(0xFFFF9800))
                        StatRow("Confirmed", report.confirmedCount.toString(), Color(0xFF4CAF50))
                        StatRow("Cancelled", report.cancelledCount.toString(), Color(0xFFF44336))
                        StatRow("Completed", report.completedCount.toString(), Color(0xFF2196F3))
                    }
                }
            }

            item {
                Text(
                    text = "Bookings by Facility Type",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        report.bookingsByFacilityType.forEach { (type, count) ->
                            StatRow(type.name.replace("_", " "), count.toString())
                        }
                        if (report.bookingsByFacilityType.isEmpty()) {
                            Text(
                                text = "No booking data",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, color: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacilityFormDialog(
    title: String,
    facility: Facility? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: FacilityType, location: String, capacity: Int, amenities: List<String>) -> Unit
) {
    var name by remember { mutableStateOf(facility?.name ?: "") }
    var selectedType by remember { mutableStateOf(facility?.type ?: FacilityType.DISCUSSION_ROOM) }
    var location by remember { mutableStateOf(facility?.location ?: "") }
    var capacity by remember { mutableStateOf(facility?.capacity?.toString() ?: "") }
    var amenitiesText by remember { mutableStateOf(facility?.amenities?.joinToString(", ") ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Facility Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        FacilityType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace("_", " ")) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it.filter { c -> c.isDigit() } },
                    label = { Text("Capacity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amenitiesText,
                    onValueChange = { amenitiesText = it },
                    label = { Text("Amenities (comma separated)") },
                    placeholder = { Text("e.g., Projector, Whiteboard, AC") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amenities = amenitiesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onConfirm(name, selectedType, location, capacity.toIntOrNull() ?: 0, amenities)
                },
                enabled = name.isNotBlank() && location.isNotBlank()
            ) {
                Text(if (facility == null) "Create" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

