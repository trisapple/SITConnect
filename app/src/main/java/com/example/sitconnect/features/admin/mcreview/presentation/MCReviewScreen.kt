package com.example.sitconnect.features.admin.mcreview.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
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
import com.example.sitconnect.features.mcsubmission.domain.model.MCStatus
import com.example.sitconnect.features.mcsubmission.domain.model.MCSubmission
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCReviewScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    mcReviewViewModel: MCReviewViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val state by mcReviewViewModel.state.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user

    var selectedTab by remember { mutableStateOf(0) }
    var statusFilter by remember { mutableStateOf<MCStatus?>(MCStatus.PENDING) }
    var showReviewDialog by remember { mutableStateOf<MCSubmission?>(null) }

    LaunchedEffect(Unit) {
        mcReviewViewModel.fetchAllMCSubmissions()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp,0.dp)
    ) {
        Text(
            text = "MC Review",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Submissions") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Statistics") },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is MCReviewState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is MCReviewState.Success -> {
                val successState = state as MCReviewState.Success

                when (selectedTab) {
                    0 -> MCSubmissionsTab(
                        submissions = successState.submissions,
                        statusFilter = statusFilter,
                        onStatusFilterChange = { statusFilter = it },
                        onReview = { showReviewDialog = it }
                    )
                    1 -> MCStatisticsTab(statistics = successState.statistics)
                }
            }
            is MCReviewState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = (state as MCReviewState.Error).message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            else -> {}
        }
    }

    // Review Dialog
    showReviewDialog?.let { submission ->
        ReviewMCDialog(
            submission = submission,
            onDismiss = { showReviewDialog = null },
            onApprove = { remarks ->
                mcReviewViewModel.reviewMC(
                    submissionId = submission.id,
                    approved = true,
                    reviewerName = currentUser?.displayName ?: currentUser?.email ?: "Admin",
                    remarks = remarks
                )
                showReviewDialog = null
            },
            onReject = { remarks ->
                mcReviewViewModel.reviewMC(
                    submissionId = submission.id,
                    approved = false,
                    reviewerName = currentUser?.displayName ?: currentUser?.email ?: "Admin",
                    remarks = remarks
                )
                showReviewDialog = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MCSubmissionsTab(
    submissions: List<MCSubmission>,
    statusFilter: MCStatus?,
    onStatusFilterChange: (MCStatus?) -> Unit,
    onReview: (MCSubmission) -> Unit
) {
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val filters = listOf(
                "All" to null,
                "Pending" to MCStatus.PENDING,
                "Approved" to MCStatus.APPROVED,
                "Rejected" to MCStatus.REJECTED
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

        val filteredSubmissions = if (statusFilter != null) {
            submissions.filter { it.status == statusFilter }
        } else submissions

        if (filteredSubmissions.isEmpty()) {
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
                        text = if (statusFilter == MCStatus.PENDING) "No pending submissions!" else "No submissions found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredSubmissions, key = { it.id }) { submission ->
                    MCSubmissionCard(
                        submission = submission,
                        onReview = { onReview(submission) }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun MCSubmissionCard(
    submission: MCSubmission,
    onReview: () -> Unit
) {
    val context = LocalContext.current
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
                        text = submission.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${submission.studentId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MCStatusChip(status = submission.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Date range
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${dateFormat.format(submission.startDate)} - ${dateFormat.format(submission.endDate)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Reason
            Text(
                text = "Reason: ${submission.reason}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Submitted at
            Text(
                text = "Submitted: ${dateFormat.format(submission.submittedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // File attachment
            if (submission.fileName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (submission.fileUrl.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(submission.fileUrl))
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View: ${submission.fileName}")
                }
            }

            // Review info if already reviewed
            if (submission.reviewedBy != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reviewed by: ${submission.reviewedBy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (submission.reviewedAt != null) {
                    Text(
                        text = "Reviewed on: ${dateFormat.format(submission.reviewedAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!submission.remarks.isNullOrBlank()) {
                    Text(
                        text = "Remarks: ${submission.remarks}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Review button for pending
            if (submission.status == MCStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Review")
                }
            }
        }
    }
}

@Composable
private fun MCStatusChip(status: MCStatus) {
    val (color, text) = when (status) {
        MCStatus.PENDING -> Color(0xFFFF9800) to "Pending"
        MCStatus.APPROVED -> Color(0xFF4CAF50) to "Approved"
        MCStatus.REJECTED -> Color(0xFFF44336) to "Rejected"
    }

    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.bodySmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color.copy(alpha = 0.2f))
    )
}

@Composable
private fun MCStatisticsTab(statistics: MCStatistics?) {
    if (statistics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No statistics available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        StatRow("Total Submissions", statistics.totalSubmissions.toString())
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StatRow("Pending", statistics.pendingCount.toString(), Color(0xFFFF9800))
                        StatRow("Approved", statistics.approvedCount.toString(), Color(0xFF4CAF50))
                        StatRow("Rejected", statistics.rejectedCount.toString(), Color(0xFFF44336))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        StatRow(
                            "Avg. Processing Time",
                            if (statistics.averageProcessingDays > 0) "%.1f days".format(statistics.averageProcessingDays) else "N/A"
                        )
                    }
                }
            }

            // Approval rate
            if (statistics.totalSubmissions > 0) {
                item {
                    val approvalRate = (statistics.approvedCount.toFloat() / statistics.totalSubmissions * 100)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Approval Rate",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { approvalRate / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = Color(0xFF4CAF50),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.1f%%".format(approvalRate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (statistics.submissionsByMonth.isNotEmpty()) {
                item {
                    Text(
                        text = "Submissions by Month",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            statistics.submissionsByMonth.entries.sortedByDescending { it.key }.forEach { (month, count) ->
                                StatRow(month, count.toString())
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
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

@Composable
private fun ReviewMCDialog(
    submission: MCSubmission,
    onDismiss: () -> Unit,
    onApprove: (remarks: String) -> Unit,
    onReject: (remarks: String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var remarks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review MC Submission") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Student: ${submission.studentName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Period: ${dateFormat.format(submission.startDate)} - ${dateFormat.format(submission.endDate)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Reason: ${submission.reason}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = remarks,
                    onValueChange = { remarks = it },
                    label = { Text("Remarks (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(remarks) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Approve")
            }
        },
        dismissButton = {
            Row {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onReject(remarks) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reject")
                }
            }
        }
    )
}



