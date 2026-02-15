package com.example.sitconnect.features.admin.scheduleoverview.presentation

import androidx.compose.foundation.background
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
import com.example.sitconnect.features.schedule.domain.model.ClassType
import com.example.sitconnect.features.schedule.domain.model.ScheduleEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleOverviewScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleOverviewViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchAllSchedules()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Schedule Overview",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("All") },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("By Lecturer") },
                icon = { Icon(Icons.Default.Person, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is ScheduleOverviewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ScheduleOverviewState.Success -> {
                val successState = state as ScheduleOverviewState.Success

                when (selectedTab) {
                    0 -> AllSchedulesTab(
                        schedules = successState.schedules,
                        selectedDay = selectedDay,
                        onDaySelected = { selectedDay = it }
                    )
                    1 -> ByLecturerTab(lecturerGroups = successState.lecturerGroups)
                }
            }
            is ScheduleOverviewState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = (state as ScheduleOverviewState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun AllSchedulesTab(
    schedules: List<ScheduleEntry>,
    selectedDay: Int?,
    onDaySelected: (Int?) -> Unit
) {
    Column {
        // Day filter
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val days = listOf("All" to null, "Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5)
            days.forEachIndexed { index, (label, day) ->
                SegmentedButton(
                    selected = selectedDay == day,
                    onClick = { onDaySelected(day) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = days.size)
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val filteredSchedules = if (selectedDay != null) {
            schedules.filter { it.dayOfWeek == selectedDay }
        } else schedules

        if (filteredSchedules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No schedules found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val grouped = filteredSchedules.groupBy { it.dayOfWeek }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (day, entries) ->
                    item {
                        Text(
                            text = getDayName(day),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(entries, key = { it.id }) { entry ->
                        ScheduleEntryCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryCard(entry: ScheduleEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                Text(
                    text = entry.startTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.endTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Divider
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .background(
                        color = when (entry.classType) {
                            ClassType.LECTURE -> Color(0xFF2196F3)
                            ClassType.TUTORIAL -> Color(0xFF4CAF50)
                            ClassType.LAB -> Color(0xFFFF9800)
                        },
                        shape = MaterialTheme.shapes.small
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.moduleCode} - ${entry.moduleName}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${entry.classType.name} • ${entry.venue}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Lecturer: ${entry.lecturerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Student count
            AssistChip(
                onClick = {},
                label = { Text("${entry.enrolledStudents.size}") },
                leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
private fun ByLecturerTab(lecturerGroups: List<LecturerScheduleGroup>) {
    var expandedLecturerId by remember { mutableStateOf<String?>(null) }

    if (lecturerGroups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No schedules found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lecturerGroups, key = { it.lecturerId }) { group ->
                LecturerScheduleCard(
                    group = group,
                    isExpanded = expandedLecturerId == group.lecturerId,
                    onToggle = {
                        expandedLecturerId = if (expandedLecturerId == group.lecturerId) null else group.lecturerId
                    }
                )
            }
        }
    }
}

@Composable
private fun LecturerScheduleCard(
    group: LecturerScheduleGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Header - always visible
            Surface(
                onClick = onToggle,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = group.lecturerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${group.schedules.size} classes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            // Expanded content
            if (isExpanded) {
                HorizontalDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    // Group by day
                    val byDay = group.schedules.groupBy { it.dayOfWeek }
                    byDay.forEach { (day, schedules) ->
                        Text(
                            text = getDayName(day),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        schedules.forEach { schedule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(40.dp)
                                        .background(
                                            color = when (schedule.classType) {
                                                ClassType.LECTURE -> Color(0xFF2196F3)
                                                ClassType.TUTORIAL -> Color(0xFF4CAF50)
                                                ClassType.LAB -> Color(0xFFFF9800)
                                            },
                                            shape = MaterialTheme.shapes.small
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${schedule.moduleCode} - ${schedule.moduleName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${schedule.startTime} - ${schedule.endTime} • ${schedule.venue}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(schedule.classType.name) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomUtilizationTab(roomUtilization: List<RoomUtilization>) {
    if (roomUtilization.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No room data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(roomUtilization) { room ->
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = room.venue,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "${room.bookedSlots} slots",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LinearProgressIndicator(
                            progress = { room.utilizationPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = when {
                                room.utilizationPercent > 80 -> MaterialTheme.colorScheme.error
                                room.utilizationPercent > 50 -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "%.1f%% utilization".format(room.utilizationPercent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictsTab(conflicts: List<ScheduleConflict>) {
    if (conflicts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No scheduling conflicts detected!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(conflicts) { conflict ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Schedule Conflict",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Venue: ${conflict.venue}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Day: ${getDayName(conflict.dayOfWeek)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Time: ${conflict.timeSlot}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        conflict.conflictingEntries.forEach { entry ->
                            Text(
                                text = "• ${entry.moduleCode}: ${entry.moduleName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getDayName(day: Int): String = when (day) {
    1 -> "Monday"
    2 -> "Tuesday"
    3 -> "Wednesday"
    4 -> "Thursday"
    5 -> "Friday"
    6 -> "Saturday"
    7 -> "Sunday"
    else -> "Unknown"
}




