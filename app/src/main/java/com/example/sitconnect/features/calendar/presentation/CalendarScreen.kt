package com.example.sitconnect.features.calendar.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.features.calendar.domain.model.CalendarEvent
import com.example.sitconnect.features.calendar.domain.model.EventType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel()
) {
    val calendarState by viewModel.calendarState.collectAsState()
    val selectedTrimester by viewModel.selectedTrimester.collectAsState()
    val selectedEventType by viewModel.selectedEventType.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp, 0.dp)
    ) {
        Text(
            text = "Trimester Calendar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View exam dates, recess weeks, and holidays",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Trimester selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(1, 2, 3).forEach { trimester ->
                FilterChip(
                    selected = selectedTrimester == trimester,
                    onClick = { viewModel.setSelectedTrimester(trimester) },
                    label = { Text("Tri $trimester") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Event type filter
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            val options = listOf(null to "All") + EventType.entries.map { it to it.name.replace("_", " ") }
            options.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = selectedEventType == type,
                    onClick = { viewModel.setSelectedEventType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                ) {
                    Text(
                        text = when (label) {
                            "All" -> "All"
                            "EXAM" -> "📝"
                            "RECESS" -> "🏖️"
                            "HOLIDAY" -> "🎉"
                            "LECTURE START" -> "📚"
                            "LECTURE END" -> "🎓"
                            else -> label.take(3)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (calendarState) {
            is CalendarState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is CalendarState.Success -> {
                val events = (calendarState as CalendarState.Success).events
                    .filter { selectedEventType == null || it.eventType == selectedEventType }

                if (events.isEmpty()) {
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
                                text = "No events found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(events) { event ->
                            CalendarEventCard(event = event)
                        }
                    }
                }
            }
            is CalendarState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(calendarState as CalendarState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is CalendarState.Idle -> {
                // Initial state
            }
        }
    }
}

@Composable
fun CalendarEventCard(event: CalendarEvent) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    val (backgroundColor, textColor, emoji) = when (event.eventType) {
        EventType.EXAM -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFC62828),
            "📝"
        )
        EventType.RECESS -> Triple(
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
            "🏖️"
        )
        EventType.HOLIDAY -> Triple(
            Color(0xFFF3E5F5),
            Color(0xFF7B1FA2),
            "🎉"
        )
        EventType.LECTURE_START -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            "📚"
        )
        EventType.LECTURE_END -> Triple(
            Color(0xFFFFF3E0),
            Color(0xFFEF6C00),
            "🎓"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.8f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .background(
                            color = textColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (event.startDate == event.endDate) {
                            dateFormat.format(event.startDate)
                        } else {
                            "${dateFormat.format(event.startDate)} - ${dateFormat.format(event.endDate)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}

