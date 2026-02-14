package com.example.sitconnect.features.admin.modulemanagement.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.features.admin.modulemanagement.domain.model.AdminModule
import com.example.sitconnect.features.admin.modulemanagement.domain.model.LecturerInfo

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
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.fetchAllModules()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Module Management",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = { showCreateDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("")
            }
        }

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
                            ModuleCard(
                                module = module,
                                onEdit = { showEditDialog = module },
                                onDelete = { showDeleteDialog = module },
                                onAssignLecturer = { showAssignDialog = module }
                            )
                        }
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

    // Dialogs
    val lecturers = (state as? ModuleManagementState.Success)?.lecturers ?: emptyList()

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
}

@Composable
private fun ModuleCard(
    module: AdminModule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAssignLecturer: () -> Unit
) {
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
                        text = module.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onAssignLecturer) {
                        Icon(Icons.Default.Person, contentDescription = "Assign Lecturer")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(module.trimester.ifEmpty { "No Trimester" }) },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${module.enrolledStudents.size} Students") },
                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            if (module.lecturerName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lecturer: ${module.lecturerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

