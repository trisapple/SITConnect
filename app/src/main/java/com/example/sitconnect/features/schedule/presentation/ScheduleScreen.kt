package com.example.sitconnect.features.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.schedule.domain.model.ClassType
import com.example.sitconnect.features.schedule.domain.model.ScheduleEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val scheduleState by scheduleViewModel.scheduleState.collectAsState()
    val attendanceCodeState by scheduleViewModel.attendanceCodeState.collectAsState()
    val attendanceRecordsState by scheduleViewModel.attendanceRecordsState.collectAsState()

    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var showAttendanceCodeDialog by remember { mutableStateOf<ScheduleEntry?>(null) }
    var showAttendanceRecordsDialog by remember { mutableStateOf<ScheduleEntry?>(null) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            scheduleViewModel.fetchLecturerSchedule(uid)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Schedule",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View your teaching timetable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Day filter chips
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val days = listOf("All" to null, "Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5)
            days.forEachIndexed { index, (label, day) ->
                SegmentedButton(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = days.size)
                ) {
                    Text(label)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (scheduleState) {
            is ScheduleState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is ScheduleState.Success -> {
                val allEntries = (scheduleState as ScheduleState.Success).schedule
                val filteredEntries = if (selectedDay != null) {
                    allEntries.filter { it.dayOfWeek == selectedDay }
                } else {
                    allEntries
                }

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedDay != null) "No classes on this day" else "No classes scheduled",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Group by day
                    val groupedByDay = filteredEntries.groupBy { it.dayOfWeek }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedByDay.forEach { (day, entries) ->
                            item {
                                Text(
                                    text = getDayName(day),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            items(entries) { entry ->
                                ScheduleEntryCard(
                                    entry = entry,
                                    onGenerateCode = { showAttendanceCodeDialog = entry },
                                    onViewAttendance = {
                                        showAttendanceRecordsDialog = entry
                                        scheduleViewModel.fetchAttendanceRecords(entry.id, entry.enrolledStudents)
                                    }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            is ScheduleState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(scheduleState as ScheduleState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is ScheduleState.Idle -> {
                // Initial state
            }
        }
    }

    // Attendance Code Dialog
    showAttendanceCodeDialog?.let { entry ->
        AttendanceCodeDialog(
            entry = entry,
            isLoading = attendanceCodeState is AttendanceCodeState.Loading,
            onGenerateCode = {
                scheduleViewModel.generateAttendanceCode(entry.id)
            },
            onClearCode = {
                scheduleViewModel.clearAttendanceCode(entry.id)
            },
            onDismiss = {
                showAttendanceCodeDialog = null
                scheduleViewModel.resetAttendanceCodeState()
            }
        )
    }

    // Attendance Records Dialog
    showAttendanceRecordsDialog?.let { entry ->
        AttendanceRecordsDialog(
            entry = entry,
            attendanceRecordsState = attendanceRecordsState,
            onSelectWeek = { week -> scheduleViewModel.selectWeek(week) },
            onDismiss = {
                showAttendanceRecordsDialog = null
                scheduleViewModel.clearAttendanceRecords()
            }
        )
    }
}

@Composable
fun ScheduleEntryCard(
    entry: ScheduleEntry,
    onGenerateCode: () -> Unit = {},
    onViewAttendance: () -> Unit = {}
) {
    val typeColor = when (entry.classType) {
        ClassType.LECTURE -> Color(0xFF2196F3)
        ClassType.TUTORIAL -> Color(0xFF4CAF50)
        ClassType.LAB -> Color(0xFF9C27B0)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max)
                    .background(typeColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.moduleCode,
                            style = MaterialTheme.typography.labelMedium,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = entry.moduleName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = typeColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = entry.classType.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${entry.startTime} - ${entry.endTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                        text = entry.venue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Attendance Code Section
                if (entry.attendanceCode.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE8F5E9)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Active Attendance Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = entry.attendanceCode,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                            }
                            IconButton(onClick = onGenerateCode) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate Code",
                                    tint = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onGenerateCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate Attendance Code")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // View Attendance Button
                OutlinedButton(
                    onClick = onViewAttendance,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Attendance (${entry.enrolledStudents.size} students)")
                }
            }
        }
    }
}

fun getDayName(day: Int): String {
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

@Composable
fun AttendanceCodeDialog(
    entry: ScheduleEntry,
    isLoading: Boolean,
    onGenerateCode: () -> Unit,
    onClearCode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Attendance Code") },
        text = {
            Column {
                Text(
                    text = "${entry.moduleCode} - ${entry.classType.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${getDayName(entry.dayOfWeek)}, ${entry.startTime} - ${entry.endTime}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (entry.attendanceCode.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Current Code",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = entry.attendanceCode,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Show this code to students",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No active code",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Generate a code to start taking attendance",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                } else {
                    Button(
                        onClick = onGenerateCode,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (entry.attendanceCode.isEmpty()) "Generate Code" else "Regenerate Code")
                    }

                    if (entry.attendanceCode.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onClearCode,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("End Attendance")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AttendanceRecordsDialog(
    entry: ScheduleEntry,
    attendanceRecordsState: AttendanceRecordsState,
    onSelectWeek: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showWeekDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Attendance Records")
                Text(
                    text = "${entry.moduleCode} - ${entry.classType.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Week selector
                if (attendanceRecordsState is AttendanceRecordsState.Success) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        Surface(
                            onClick = { showWeekDropdown = true },
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📅 ${attendanceRecordsState.weekLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (attendanceRecordsState.availableWeeks.size > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "▼",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showWeekDropdown,
                            onDismissRequest = { showWeekDropdown = false }
                        ) {
                            attendanceRecordsState.availableWeeks.forEach { week ->
                                val isSelected = week == attendanceRecordsState.weekLabel
                                // Determine if this is the current calendar week
                                val cal = java.util.Calendar.getInstance().apply {
                                    firstDayOfWeek = java.util.Calendar.MONDAY
                                    minimalDaysInFirstWeek = 4
                                }
                                val currentWeekStr = String.format(
                                    "%d-W%02d",
                                    cal.get(java.util.Calendar.YEAR),
                                    cal.get(java.util.Calendar.WEEK_OF_YEAR)
                                )
                                val isCurrentWeek = week == currentWeekStr

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = week,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (isCurrentWeek) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                                    shape = MaterialTheme.shapes.small
                                                ) {
                                                    Text(
                                                        text = "Current",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF4CAF50),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectWeek(week)
                                        showWeekDropdown = false
                                    },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        text = {
            when (attendanceRecordsState) {
                is AttendanceRecordsState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AttendanceRecordsState.Success -> {
                    Column {
                        // Summary
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${attendanceRecordsState.presentCount}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "Present",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${attendanceRecordsState.totalCount - attendanceRecordsState.presentCount}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF44336)
                                    )
                                    Text(
                                        text = "Absent",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${attendanceRecordsState.totalCount}",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Total",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Student list
                        if (attendanceRecordsState.records.isEmpty()) {
                            Text(
                                text = "No students enrolled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(attendanceRecordsState.records) { student ->
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (student.isPresent)
                                                Color(0xFFE8F5E9)
                                            else
                                                Color(0xFFFFEBEE)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = student.studentName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = student.studentEmail,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (student.isPresent && student.markedAt != null) {
                                                    val dateTimeFormat = java.text.SimpleDateFormat(
                                                        "dd MMM yyyy, HH:mm", java.util.Locale.getDefault()
                                                    )
                                                    Text(
                                                        text = "✓ ${dateTimeFormat.format(student.markedAt)} via ${student.markedVia}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF4CAF50)
                                                    )
                                                } else if (!student.isPresent) {
                                                    Text(
                                                        text = "Not marked this week",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFF44336)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (student.isPresent) "✓" else "✗",
                                                style = MaterialTheme.typography.titleLarge,
                                                color = if (student.isPresent) Color(0xFF4CAF50) else Color(0xFFF44336)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                is AttendanceRecordsState.Error -> {
                    Text(
                        text = "Error: ${attendanceRecordsState.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is AttendanceRecordsState.Idle -> {
                    // Initial state
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
