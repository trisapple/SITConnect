package com.example.sitconnect2.features.calendar.presentation

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect2.AuthState
import com.example.sitconnect2.AuthViewModel
import com.example.sitconnect2.UserDataState
import com.example.sitconnect2.UserViewModel
import com.example.sitconnect2.features.calendar.domain.model.CalendarEvent
import com.example.sitconnect2.features.calendar.domain.model.EventType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel()
) {
    val calendarState by viewModel.calendarState.collectAsState()
    val operationState by viewModel.operationState.collectAsState()
    val selectedTrimester by viewModel.selectedTrimester.collectAsState()
    val selectedEventType by viewModel.selectedEventType.collectAsState()

    val authState by authViewModel.authState.collectAsState()
    val userDataState by userViewModel.userDataState.collectAsState()

    val currentUser = (authState as? AuthState.Success)?.user
    val isAdmin = (userDataState as? UserDataState.Success)?.userData?.roles?.admin == true

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<CalendarEvent?>(null) }
    var showDeleteDialog by remember { mutableStateOf<CalendarEvent?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Fetch user data to check admin status
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Handle operation state
    LaunchedEffect(operationState) {
        when (operationState) {
            is CalendarOperationState.Success -> {
                showCreateDialog = false
                showEditDialog = null
                showDeleteDialog = null
                snackbarHostState.showSnackbar("Operation successful!")
                viewModel.resetOperationState()
            }
            is CalendarOperationState.Error -> {
                snackbarHostState.showSnackbar(
                    "Error: ${(operationState as CalendarOperationState.Error).message}"
                )
                viewModel.resetOperationState()
            }
            else -> {}
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp, 0.dp)
        ) {
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
                                if (isAdmin) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Tap + to add an event",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(events) { event ->
                                CalendarEventCard(
                                    event = event,
                                    isAdmin = isAdmin,
                                    onEdit = { showEditDialog = event },
                                    onDelete = { showDeleteDialog = event }
                                )
                            }
                            // Add space after all items
                            item { Spacer(modifier = Modifier.height(4.dp)) }
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

    // Create Event Dialog
    if (showCreateDialog) {
        CalendarEventFormDialog(
            title = "Add Calendar Event",
            defaultTrimester = selectedTrimester,
            isLoading = operationState is CalendarOperationState.Loading,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, description, startDate, endDate, eventType, trimester ->
                viewModel.createCalendarEvent(title, description, startDate, endDate, eventType, trimester)
            }
        )
    }

    // Edit Event Dialog
    showEditDialog?.let { event ->
        CalendarEventFormDialog(
            title = "Edit Calendar Event",
            event = event,
            defaultTrimester = selectedTrimester,
            isLoading = operationState is CalendarOperationState.Loading,
            onDismiss = { showEditDialog = null },
            onConfirm = { title, description, startDate, endDate, eventType, trimester ->
                viewModel.updateCalendarEvent(event.id, title, description, startDate, endDate, eventType, trimester)
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { event ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete \"${event.title}\"?") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCalendarEvent(event.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = operationState !is CalendarOperationState.Loading
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = null },
                    enabled = operationState !is CalendarOperationState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CalendarEventCard(
    event: CalendarEvent,
    isAdmin: Boolean = false,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    var showMenu by remember { mutableStateOf(false) }

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

            if (isAdmin) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = textColor
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarEventFormDialog(
    title: String,
    event: CalendarEvent? = null,
    defaultTrimester: Int = 1,
    isLoading: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Date, Date, EventType, Int) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    var eventTitle by remember { mutableStateOf(event?.title ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var startDate by remember { mutableStateOf(event?.startDate ?: Date()) }
    var endDate by remember { mutableStateOf(event?.endDate ?: Date()) }
    var selectedEventType by remember { mutableStateOf(event?.eventType ?: EventType.LECTURE_START) }
    var selectedTrimester by remember { mutableStateOf(event?.trimester ?: defaultTrimester) }

    var eventTypeExpanded by remember { mutableStateOf(false) }
    var trimesterExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = eventTitle,
                    onValueChange = { eventTitle = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                // Event Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = eventTypeExpanded,
                    onExpandedChange = { if (!isLoading) eventTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedEventType.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Event Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = eventTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = eventTypeExpanded,
                        onDismissRequest = { eventTypeExpanded = false }
                    ) {
                        EventType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.replace("_", " ")) },
                                onClick = {
                                    selectedEventType = type
                                    eventTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Trimester Dropdown
                ExposedDropdownMenuBox(
                    expanded = trimesterExpanded,
                    onExpandedChange = { if (!isLoading) trimesterExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "Trimester $selectedTrimester",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Trimester") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = trimesterExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        enabled = !isLoading
                    )
                    ExposedDropdownMenu(
                        expanded = trimesterExpanded,
                        onDismissRequest = { trimesterExpanded = false }
                    ) {
                        listOf(1, 2, 3).forEach { tri ->
                            DropdownMenuItem(
                                text = { Text("Trimester $tri") },
                                onClick = {
                                    selectedTrimester = tri
                                    trimesterExpanded = false
                                }
                            )
                        }
                    }
                }

                // Start Date
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        calendar.time = startDate
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, day)
                                startDate = newCal.time
                                if (endDate.before(startDate)) {
                                    endDate = startDate
                                }
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Start: ${dateFormat.format(startDate)}")
                }

                // End Date
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        calendar.time = endDate
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val newCal = Calendar.getInstance()
                                newCal.set(year, month, day)
                                endDate = newCal.time
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("End: ${dateFormat.format(endDate)}")
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (eventTitle.isNotBlank()) {
                        onConfirm(eventTitle, description, startDate, endDate, selectedEventType, selectedTrimester)
                    }
                },
                enabled = eventTitle.isNotBlank() && !isLoading
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

