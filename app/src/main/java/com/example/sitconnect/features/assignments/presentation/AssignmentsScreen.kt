package com.example.sitconnect.features.assignments.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.AuthState
import com.example.sitconnect.AuthViewModel
import com.example.sitconnect.features.assignments.domain.model.Assignment
import com.example.sitconnect.features.assignments.domain.model.AssignmentSubmission
import com.example.sitconnect.features.assignments.domain.model.SubmissionStatus
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    assignmentsViewModel: AssignmentsViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val assignmentsState by assignmentsViewModel.assignmentsState.collectAsState()
    val submitState by assignmentsViewModel.submitState.collectAsState()

    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            assignmentsViewModel.fetchAssignments(uid)
        }
    }

    LaunchedEffect(submitState) {
        if (submitState is SubmitAssignmentState.Success) {
            selectedAssignment = null
            assignmentsViewModel.resetSubmitState()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Assignment Portal",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Upload your assignments for modules",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (assignmentsState) {
            is AssignmentsState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is AssignmentsState.Success -> {
                val assignments = (assignmentsState as AssignmentsState.Success).assignments

                if (assignments.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No assignments available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(assignments) { (assignment, submission) ->
                            AssignmentCard(
                                assignment = assignment,
                                submission = submission,
                                onSubmitClick = { selectedAssignment = assignment }
                            )
                        }
                    }
                }
            }
            is AssignmentsState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(assignmentsState as AssignmentsState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is AssignmentsState.Idle -> {
                // Initial state
            }
        }
    }

    selectedAssignment?.let { assignment ->
        SubmitAssignmentDialog(
            assignment = assignment,
            onDismiss = { selectedAssignment = null },
            onSubmit = { fileName ->
                assignmentsViewModel.submitAssignment(assignment.id, fileName)
            },
            isLoading = submitState is SubmitAssignmentState.Loading,
            error = (submitState as? SubmitAssignmentState.Error)?.message
        )
    }
}

@Composable
fun AssignmentCard(
    assignment: Assignment,
    submission: AssignmentSubmission?,
    onSubmitClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    val now = Date()
    val daysUntilDue = TimeUnit.MILLISECONDS.toDays(assignment.dueDate.time - now.time)
    val isOverdue = now.after(assignment.dueDate)

    val (statusColor, statusText, statusIcon) = when {
        submission?.status == SubmissionStatus.GRADED -> Triple(
            Color(0xFF4CAF50),
            "Graded: ${submission.score}/${assignment.maxScore}",
            Icons.Default.Check
        )
        submission?.status == SubmissionStatus.SUBMITTED -> Triple(
            Color(0xFF2196F3),
            "Submitted",
            Icons.Default.Check
        )
        isOverdue -> Triple(
            Color(0xFFF44336),
            "Overdue",
            Icons.Default.Warning
        )
        daysUntilDue <= 3 -> Triple(
            Color(0xFFFFA000),
            "Due soon ($daysUntilDue days)",
            Icons.Default.Warning
        )
        else -> Triple(
            Color(0xFF4CAF50),
            "Due in $daysUntilDue days",
            Icons.Default.Edit
        )
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
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = assignment.moduleCode,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = statusColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = assignment.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = assignment.moduleName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = assignment.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Due: ${dateFormat.format(assignment.dueDate)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOverdue) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Max Score: ${assignment.maxScore}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Allowed: ${assignment.allowedFileTypes.joinToString(", ").uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (submission?.status != SubmissionStatus.GRADED) {
                    Button(
                        onClick = onSubmitClick,
                        enabled = !isOverdue || submission == null
                    ) {
                        Text(if (submission == null) "Submit" else "Resubmit")
                    }
                }
            }

            submission?.let {
                if (it.feedback != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Feedback:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = it.feedback,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitAssignmentDialog(
    assignment: Assignment,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    isLoading: Boolean,
    error: String?
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Submit Assignment") },
        text = {
            Column {
                Text(
                    text = assignment.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = assignment.moduleCode,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

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

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    placeholder = { Text("e.g., assignment1.pdf") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    supportingText = {
                        Text("Allowed types: ${assignment.allowedFileTypes.joinToString(", ").uppercase()}")
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Note: File upload will be simulated. In production, you would select a file from your device.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(fileName) },
                enabled = !isLoading && fileName.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Submit")
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

