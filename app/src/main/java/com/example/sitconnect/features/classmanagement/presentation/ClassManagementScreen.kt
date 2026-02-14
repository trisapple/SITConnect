package com.example.sitconnect.features.classmanagement.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.classmanagement.domain.model.EnrolledStudent
import com.example.sitconnect.features.classmanagement.domain.model.Module

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassManagementScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    classViewModel: ClassManagementViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val classState by classViewModel.classState.collectAsState()

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            classViewModel.fetchLecturerModules(uid)
        }
    }

    val selectedModule = (classState as? ClassManagementState.Success)?.selectedModule

    if (selectedModule != null) {
        StudentListScreen(
            module = selectedModule,
            students = (classState as? ClassManagementState.Success)?.students ?: emptyList(),
            availableStudents = (classState as? ClassManagementState.Success)?.availableStudents ?: emptyList(),
            onBack = { classViewModel.clearSelectedModule() },
            onEnrollStudent = { studentUid -> classViewModel.enrollStudent(selectedModule.id, studentUid) },
            onUnenrollStudent = { studentUid -> classViewModel.unenrollStudent(selectedModule.id, studentUid) },
            onFetchAvailable = { classViewModel.fetchAvailableStudents(selectedModule.id, selectedModule.enrolledStudents) }
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Class Management",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "View students in your modules",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (classState) {
                is ClassManagementState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is ClassManagementState.Success -> {
                    val modules = (classState as ClassManagementState.Success).modules

                    if (modules.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No modules assigned",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Contact admin to be assigned to modules",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(modules) { module ->
                                ModuleCard(
                                    module = module,
                                    onClick = { classViewModel.selectModule(module) }
                                )
                            }
                        }
                    }
                }
                is ClassManagementState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: ${(classState as ClassManagementState.Error).message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is ClassManagementState.Idle -> {
                    // Initial state
                }
            }
        }
    }
}

@Composable
fun ModuleCard(
    module: Module,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = module.code,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = module.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (module.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap to view enrolled students →",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentListScreen(
    module: Module,
    students: List<EnrolledStudent>,
    availableStudents: List<EnrolledStudent>,
    onBack: () -> Unit,
    onEnrollStudent: (String) -> Unit,
    onUnenrollStudent: (String) -> Unit,
    onFetchAvailable: () -> Unit
) {
    var showEnrollDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onFetchAvailable()
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.code,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            // Add student button
            IconButton(onClick = { showEnrollDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Enroll Student"
                )
            }
        }

        HorizontalDivider()

        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No students enrolled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showEnrollDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enroll Students")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text(
                        text = "${students.size} student${if (students.size != 1) "s" else ""} enrolled",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(students) { student ->
                    StudentCard(
                        student = student,
                        onUnenroll = { onUnenrollStudent(student.uid) }
                    )
                }
            }
        }
    }

    // Enroll Student Dialog
    if (showEnrollDialog) {
        EnrollStudentDialog(
            availableStudents = availableStudents,
            onDismiss = { showEnrollDialog = false },
            onEnroll = { studentUid ->
                onEnrollStudent(studentUid)
                showEnrollDialog = false
            }
        )
    }
}

@Composable
fun EnrollStudentDialog(
    availableStudents: List<EnrolledStudent>,
    onDismiss: () -> Unit,
    onEnroll: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll Student") },
        text = {
            if (availableStudents.isEmpty()) {
                Text("No students available to enroll")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(availableStudents) { student ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEnroll(student.uid) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = student.name.firstOrNull()?.uppercase() ?: "?",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = student.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = student.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun StudentCard(
    student: EnrolledStudent,
    onUnenroll: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = student.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (student.studentId.isNotEmpty()) {
                    Text(
                        text = student.studentId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = student.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Unenroll button
            IconButton(onClick = { showConfirmDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Unenroll",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Unenroll Student") },
            text = { Text("Are you sure you want to unenroll ${student.name} from this module?") },
            confirmButton = {
                Button(
                    onClick = {
                        onUnenroll()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Unenroll")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
