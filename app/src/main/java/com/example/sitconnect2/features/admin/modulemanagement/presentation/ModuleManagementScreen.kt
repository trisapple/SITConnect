package com.example.sitconnect2.features.admin.modulemanagement.presentation

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect2.features.admin.modulemanagement.domain.model.AdminModule
import com.example.sitconnect2.features.admin.modulemanagement.domain.model.LecturerInfo
import com.example.sitconnect2.features.admin.modulemanagement.domain.model.ModuleSchedule
import com.example.sitconnect2.features.admin.modulemanagement.domain.model.ScheduleClassType
import com.example.sitconnect2.features.admin.modulemanagement.domain.model.StudentInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleManagementScreen(
    modifier: Modifier = Modifier,
    viewModel: ModuleManagementViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<AdminModule?>(null) }
    var showDeleteDialog by remember { mutableStateOf<AdminModule?>(null) }
    var showAssignDialog by remember { mutableStateOf<AdminModule?>(null) }
    var showEnrollmentDialog by remember { mutableStateOf<String?>(null) }  // Uses module ID for fresh data
    var showScheduleDialog by remember { mutableStateOf<AdminModule?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.fetchAllModules()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Module")
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
                text = "Module Management",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Manage modules, schedules, and enrollments",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search modules...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is ModuleManagementState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ModuleManagementState.Success -> {
                    val successState = state as ModuleManagementState.Success
                    val filteredModules = successState.modules.filter {
                        it.code.contains(searchQuery, ignoreCase = true) ||
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.lecturerName.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredModules.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No modules found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(filteredModules, key = { it.id }) { module ->
                                val scheduleCount = successState.allSchedules.count { it.moduleId == module.id }
                                // Calculate actual enrolled count by matching with existing students
                                val actualEnrolledCount = successState.allStudents.count { student ->
                                    module.enrolledStudents.contains(student.id)
                                }
                                ModuleCard(
                                    module = module,
                                    enrolledStudentCount = actualEnrolledCount,
                                    scheduleCount = scheduleCount,
                                    onEdit = { showEditDialog = module },
                                    onDelete = { showDeleteDialog = module },
                                    onAssignLecturer = { showAssignDialog = module },
                                    onManageStudents = { showEnrollmentDialog = module.id },
                                    onManageSchedule = { showScheduleDialog = module }
                                )
                            }
                            // Add space after all items
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                        }
                    }
                }
                is ModuleManagementState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Text(
                            text = (state as ModuleManagementState.Error).message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                else -> {}
            }
        }
    }

    // Dialogs
    val lecturers = (state as? ModuleManagementState.Success)?.lecturers ?: emptyList()
    val allStudents = (state as? ModuleManagementState.Success)?.allStudents ?: emptyList()
    val allSchedules = (state as? ModuleManagementState.Success)?.allSchedules ?: emptyList()

    if (showCreateDialog) {
        ModuleFormDialog(
            title = "Create Module",
            lecturers = lecturers,
            onDismiss = { showCreateDialog = false },
            onConfirm = { code, name, desc, trimester, lecId, lecName ->
                viewModel.createModule(code, name, desc, trimester, lecId, lecName)
                showCreateDialog = false
            }
        )
    }

    showEditDialog?.let { module ->
        ModuleFormDialog(
            title = "Edit Module",
            module = module,
            lecturers = lecturers,
            onDismiss = { showEditDialog = null },
            onConfirm = { code, name, desc, trimester, lecId, lecName ->
                viewModel.updateModule(module.id, code, name, desc, trimester, lecId, lecName)
                showEditDialog = null
            }
        )
    }

    showDeleteDialog?.let { module ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Module") },
            text = { Text("Are you sure you want to delete ${module.code} - ${module.name}? This will also delete all associated schedules.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteModule(module.id)
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showAssignDialog?.let { module ->
        AssignLecturerDialog(
            module = module,
            lecturers = lecturers,
            onDismiss = { showAssignDialog = null },
            onConfirm = { lecId, lecName ->
                viewModel.assignLecturer(module.id, lecId, lecName)
                showAssignDialog = null
            }
        )
    }

    showEnrollmentDialog?.let { moduleId ->
        // Get fresh module data from state to ensure student count is up to date
        val currentModule = (state as? ModuleManagementState.Success)?.modules?.find { it.id == moduleId }

        if (currentModule != null) {
            StudentEnrollmentDialog(
                module = currentModule,
                allStudents = allStudents,
                currentEnrolledStudentIds = currentModule.enrolledStudents,
                onDismiss = { showEnrollmentDialog = null },
                onEnroll = { studentId ->
                    viewModel.enrollStudent(currentModule.id, studentId)
                },
                onUnenroll = { studentId ->
                    viewModel.unenrollStudent(currentModule.id, studentId)
                }
            )
        }
    }

    showScheduleDialog?.let { module ->
        ScheduleManagementDialog(
            module = module,
            schedules = allSchedules.filter { it.moduleId == module.id },
            onDismiss = { showScheduleDialog = null },
            onCreateSchedule = { classType, dayOfWeek, startTime, endTime, venue ->
                viewModel.createSchedule(
                    module = module,
                    classType = classType,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue,
                    onSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Schedule created successfully")
                        }
                    },
                    onError = { error ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    }
                )
            },
            onUpdateSchedule = { scheduleId, classType, dayOfWeek, startTime, endTime, venue ->
                viewModel.updateSchedule(
                    scheduleId = scheduleId,
                    module = module,
                    classType = classType,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue,
                    onSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Schedule updated successfully")
                        }
                    },
                    onError = { error ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(error)
                        }
                    }
                )
            },
            onDeleteSchedule = { scheduleId ->
                viewModel.deleteSchedule(scheduleId)
            },
            checkConflicts = { dayOfWeek, startTime, endTime, venue, excludeId ->
                viewModel.checkForConflicts(
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue,
                    lecturerId = module.lecturerId,
                    excludeScheduleId = excludeId
                )
            }
        )
    }
}

@Composable
private fun ModuleCard(
    module: AdminModule,
    enrolledStudentCount: Int,
    scheduleCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignLecturer: () -> Unit,
    onManageStudents: () -> Unit,
    onManageSchedule: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (module.lecturerName.isNotEmpty()) {
                        Text(
                            text = "Lecturer: ${module.lecturerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = module.trimester.ifEmpty { "No Trimester" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Manage Schedule") },
                            onClick = { showMenu = false; onManageSchedule() },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Students") },
                            onClick = { showMenu = false; onManageStudents() },
                            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Assign Lecturer") },
                            onClick = { showMenu = false; onAssignLecturer() },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Edit Module") },
                            onClick = { showMenu = false; onEdit() },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AssistChip(
                    onClick = onManageStudents,
                    label = { Text("$enrolledStudentCount Students") },
                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onManageSchedule,
                    label = { Text("$scheduleCount Classes") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleFormDialog(
    title: String,
    module: AdminModule? = null,
    lecturers: List<LecturerInfo>,
    onDismiss: () -> Unit,
    onConfirm: (code: String, name: String, description: String, trimester: String, lecturerId: String, lecturerName: String) -> Unit
) {
    var code by remember { mutableStateOf(module?.code ?: "") }
    var name by remember { mutableStateOf(module?.name ?: "") }
    var description by remember { mutableStateOf(module?.description ?: "") }
    var trimester by remember { mutableStateOf(module?.trimester ?: "T2 2025-2026") }
    var selectedLecturerId by remember { mutableStateOf(module?.lecturerId ?: "") }
    var selectedLecturerName by remember { mutableStateOf(module?.lecturerName ?: "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    label = { Text("Module Code") },
                    placeholder = { Text("e.g., ICT2207") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Module Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trimester,
                    onValueChange = { trimester = it },
                    label = { Text("Trimester") },
                    placeholder = { Text("e.g., T2 2025-2026") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLecturerName.ifEmpty { "Select Lecturer" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lecturer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        lecturers.forEach { lecturer ->
                            DropdownMenuItem(
                                text = { Text("${lecturer.name} (${lecturer.email})") },
                                onClick = {
                                    selectedLecturerId = lecturer.id
                                    selectedLecturerName = lecturer.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code, name, description, trimester, selectedLecturerId, selectedLecturerName) },
                enabled = code.isNotBlank() && name.isNotBlank()
            ) {
                Text(if (module == null) "Create" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignLecturerDialog(
    module: AdminModule,
    lecturers: List<LecturerInfo>,
    onDismiss: () -> Unit,
    onConfirm: (lecturerId: String, lecturerName: String) -> Unit
) {
    var selectedLecturerId by remember { mutableStateOf(module.lecturerId) }
    var selectedLecturerName by remember { mutableStateOf(module.lecturerName) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Lecturer") },
        text = {
            Column {
                Text(
                    text = "Assign a lecturer to ${module.code} - ${module.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedLecturerName.ifEmpty { "Select Lecturer" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Lecturer") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        lecturers.forEach { lecturer ->
                            DropdownMenuItem(
                                text = { Text("${lecturer.name} (${lecturer.email})") },
                                onClick = {
                                    selectedLecturerId = lecturer.id
                                    selectedLecturerName = lecturer.name
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedLecturerId, selectedLecturerName) },
                enabled = selectedLecturerId.isNotBlank()
            ) {
                Text("Assign")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentEnrollmentDialog(
    module: AdminModule,
    allStudents: List<StudentInfo>,
    currentEnrolledStudentIds: List<String>,
    onDismiss: () -> Unit,
    onEnroll: (studentId: String) -> Unit,
    onUnenroll: (studentId: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    val enrolledStudents = allStudents.filter { currentEnrolledStudentIds.contains(it.id) }
    val availableStudents = allStudents.filter { !currentEnrolledStudentIds.contains(it.id) }

    val filteredEnrolled = enrolledStudents.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.email.contains(searchQuery, ignoreCase = true) ||
        it.studentId.contains(searchQuery, ignoreCase = true)
    }

    val filteredAvailable = availableStudents.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.email.contains(searchQuery, ignoreCase = true) ||
        it.studentId.contains(searchQuery, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Students - ${module.code}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search students...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Enrolled (${enrolledStudents.size})") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Available (${availableStudents.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (selectedTab == 0) {
                        if (filteredEnrolled.isEmpty()) {
                            item {
                                Text(
                                    "No enrolled students",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(filteredEnrolled, key = { it.id }) { student ->
                                StudentListItem(
                                    student = student,
                                    isEnrolled = true,
                                    onAction = { onUnenroll(student.id) }
                                )
                            }
                        }
                    } else {
                        if (filteredAvailable.isEmpty()) {
                            item {
                                Text(
                                    "No available students",
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(filteredAvailable, key = { it.id }) { student ->
                                StudentListItem(
                                    student = student,
                                    isEnrolled = false,
                                    onAction = { onEnroll(student.id) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {}
    )
}

@Composable
private fun StudentListItem(
    student: StudentInfo,
    isEnrolled: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnrolled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = student.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (student.studentId.isNotEmpty()) {
                    Text(
                        text = "ID: ${student.studentId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isEnrolled) {
                IconButton(onClick = onAction) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Unenroll",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onAction) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Enroll",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// Schedule Management Dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleManagementDialog(
    module: AdminModule,
    schedules: List<ModuleSchedule>,
    onDismiss: () -> Unit,
    onCreateSchedule: (ScheduleClassType, Int, String, String, String) -> Unit,
    onUpdateSchedule: (String, ScheduleClassType, Int, String, String, String) -> Unit,
    onDeleteSchedule: (String) -> Unit,
    checkConflicts: (Int, String, String, String, String?) -> List<ModuleSchedule>
) {
    var showAddScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ModuleSchedule?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<ModuleSchedule?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Schedule Management")
                Text(
                    text = "${module.code} - ${module.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                OutlinedButton(
                    onClick = { showAddScheduleDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Class Schedule")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (schedules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No schedules assigned yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(schedules.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime })), key = { it.id }) { schedule ->
                            ScheduleItemCard(
                                schedule = schedule,
                                onEdit = { editingSchedule = schedule },
                                onDelete = { showDeleteConfirmation = schedule }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {}
    )

    if (showAddScheduleDialog) {
        ScheduleFormDialog(
            title = "Add Class Schedule",
            module = module,
            checkConflicts = checkConflicts,
            onDismiss = { showAddScheduleDialog = false },
            onConfirm = { classType, dayOfWeek, startTime, endTime, venue ->
                onCreateSchedule(classType, dayOfWeek, startTime, endTime, venue)
                showAddScheduleDialog = false
            }
        )
    }

    editingSchedule?.let { schedule ->
        ScheduleFormDialog(
            title = "Edit Class Schedule",
            module = module,
            schedule = schedule,
            checkConflicts = checkConflicts,
            onDismiss = { editingSchedule = null },
            onConfirm = { classType, dayOfWeek, startTime, endTime, venue ->
                onUpdateSchedule(schedule.id, classType, dayOfWeek, startTime, endTime, venue)
                editingSchedule = null
            }
        )
    }

    showDeleteConfirmation?.let { schedule ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Delete Schedule") },
            text = {
                Text("Are you sure you want to delete the ${schedule.classType.name} class on ${getDayName(schedule.dayOfWeek)} (${schedule.startTime} - ${schedule.endTime})?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSchedule(schedule.id)
                        showDeleteConfirmation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScheduleItemCard(
    schedule: ModuleSchedule,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Class type indicator
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(
                            color = when (schedule.classType) {
                                ScheduleClassType.LECTURE -> Color(0xFF2196F3)
                                ScheduleClassType.TUTORIAL -> Color(0xFF4CAF50)
                                ScheduleClassType.LAB -> Color(0xFFFF9800)
                            },
                            shape = MaterialTheme.shapes.small
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${getDayName(schedule.dayOfWeek)} • ${schedule.startTime} - ${schedule.endTime}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${schedule.classType.name} • ${schedule.venue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleFormDialog(
    title: String,
    module: AdminModule,
    schedule: ModuleSchedule? = null,
    checkConflicts: (Int, String, String, String, String?) -> List<ModuleSchedule>,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleClassType, Int, String, String, String) -> Unit
) {
    var selectedClassType by remember { mutableStateOf(schedule?.classType ?: ScheduleClassType.LECTURE) }
    var selectedDay by remember { mutableStateOf(schedule?.dayOfWeek ?: 1) }
    var startTime by remember { mutableStateOf(schedule?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(schedule?.endTime ?: "12:00") }
    var venue by remember { mutableStateOf(schedule?.venue ?: "") }

    var classTypeExpanded by remember { mutableStateOf(false) }
    var dayExpanded by remember { mutableStateOf(false) }
    var startTimeExpanded by remember { mutableStateOf(false) }
    var endTimeExpanded by remember { mutableStateOf(false) }

    var conflicts by remember { mutableStateOf<List<ModuleSchedule>>(emptyList()) }

    LaunchedEffect(selectedDay, startTime, endTime, venue) {
        if (venue.isNotBlank()) {
            conflicts = checkConflicts(selectedDay, startTime, endTime, venue, schedule?.id)
        }
    }

    val timeSlots = listOf(
        "08:00", "09:00", "10:00", "11:00", "12:00", "13:00",
        "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Class Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = classTypeExpanded,
                    onExpandedChange = { classTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedClassType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Class Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = classTypeExpanded,
                        onDismissRequest = { classTypeExpanded = false }
                    ) {
                        ScheduleClassType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedClassType = type
                                    classTypeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Day Dropdown
                ExposedDropdownMenuBox(
                    expanded = dayExpanded,
                    onExpandedChange = { dayExpanded = it }
                ) {
                    OutlinedTextField(
                        value = getDayName(selectedDay),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Day") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = dayExpanded,
                        onDismissRequest = { dayExpanded = false }
                    ) {
                        (1..5).forEach { day ->
                            DropdownMenuItem(
                                text = { Text(getDayName(day)) },
                                onClick = {
                                    selectedDay = day
                                    dayExpanded = false
                                }
                            )
                        }
                    }
                }

                // Time Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Start Time
                    ExposedDropdownMenuBox(
                        expanded = startTimeExpanded,
                        onExpandedChange = { startTimeExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Start") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startTimeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = startTimeExpanded,
                            onDismissRequest = { startTimeExpanded = false }
                        ) {
                            timeSlots.forEach { time ->
                                DropdownMenuItem(
                                    text = { Text(time) },
                                    onClick = {
                                        startTime = time
                                        startTimeExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // End Time
                    ExposedDropdownMenuBox(
                        expanded = endTimeExpanded,
                        onExpandedChange = { endTimeExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endTimeExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = endTimeExpanded,
                            onDismissRequest = { endTimeExpanded = false }
                        ) {
                            timeSlots.filter { it > startTime }.forEach { time ->
                                DropdownMenuItem(
                                    text = { Text(time) },
                                    onClick = {
                                        endTime = time
                                        endTimeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = venue,
                    onValueChange = { venue = it },
                    label = { Text("Venue") },
                    placeholder = { Text("e.g., SIT Punggol Campus - LT1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Quick Select:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("LT1", "LT2", "Lab 4A", "TR1").forEach { shortVenue ->
                        FilterChip(
                            selected = venue.contains(shortVenue),
                            onClick = { venue = "SIT Punggol Campus - $shortVenue" },
                            label = { Text(shortVenue, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Conflict Warning
                if (conflicts.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Schedule Conflicts Detected!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            conflicts.forEach { conflict ->
                                Text(
                                    text = "• ${conflict.moduleCode}: ${conflict.startTime}-${conflict.endTime} (${if (conflict.venue.equals(venue, ignoreCase = true)) "Same venue" else "Same lecturer"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedClassType, selectedDay, startTime, endTime, venue) },
                enabled = venue.isNotBlank() && startTime < endTime && conflicts.isEmpty()
            ) {
                Text(if (schedule == null) "Create" else "Update")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
