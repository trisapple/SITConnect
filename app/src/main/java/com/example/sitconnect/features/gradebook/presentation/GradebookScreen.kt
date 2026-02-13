package com.example.sitconnect.features.gradebook.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.example.sitconnect.features.gradebook.domain.model.Assessment
import com.example.sitconnect.features.gradebook.domain.model.GradeSummary
import com.example.sitconnect.features.gradebook.domain.model.ModuleGrade

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradebookScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    gradebookViewModel: GradebookViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val gradebookState by gradebookViewModel.gradebookState.collectAsState()

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            gradebookViewModel.fetchGrades(uid)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Gradebook",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "View your GPA and module grades",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (gradebookState) {
            is GradebookState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is GradebookState.Success -> {
                val state = gradebookState as GradebookState.Success

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        GPASummaryCard(summary = state.summary)
                    }

                    item {
                        Text(
                            text = "Current Modules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(state.modules) { module ->
                        ModuleGradeCard(module = module)
                    }
                }
            }
            is GradebookState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: ${(gradebookState as GradebookState.Error).message}",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            is GradebookState.Idle -> {
                // Initial state
            }
        }
    }
}

@Composable
fun GPASummaryCard(summary: GradeSummary) {
    val gpaColor = when {
        summary.currentGPA >= 4.0 -> Color(0xFF4CAF50)
        summary.currentGPA >= 3.0 -> Color(0xFF2196F3)
        summary.currentGPA >= 2.0 -> Color(0xFFFFA000)
        else -> Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "GPA Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GPAStatItem(
                    label = "Current GPA",
                    value = String.format("%.2f", summary.currentGPA),
                    color = gpaColor,
                    isMain = true
                )
                GPAStatItem(
                    label = "Cumulative GPA",
                    value = String.format("%.2f", summary.cumulativeGPA),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CreditStatItem(
                    label = "Credits Earned",
                    value = summary.totalCreditsEarned.toString()
                )
                CreditStatItem(
                    label = "Credits Attempted",
                    value = summary.totalCreditsAttempted.toString()
                )
                CreditStatItem(
                    label = "This Trimester",
                    value = summary.currentTrimesterCredits.toString()
                )
            }
        }
    }
}

@Composable
fun GPAStatItem(
    label: String,
    value: String,
    color: Color,
    isMain: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = if (isMain) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun CreditStatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ModuleGradeCard(module: ModuleGrade) {
    var expanded by remember { mutableStateOf(false) }

    val gradeColor = when (module.currentGrade?.firstOrNull()) {
        'A' -> Color(0xFF4CAF50)
        'B' -> Color(0xFF2196F3)
        'C' -> Color(0xFFFFA000)
        'D' -> Color(0xFFFF9800)
        'F' -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Calculate current percentage
    val gradedAssessments = module.assessments.filter { it.graded && it.score != null }
    val currentPercentage = if (gradedAssessments.isNotEmpty()) {
        val weightedSum = gradedAssessments.sumOf { ((it.score!! / it.maxScore) * it.weightage).toDouble() }
        val totalWeight = gradedAssessments.sumOf { it.weightage.toDouble() }
        if (totalWeight > 0) (weightedSum / totalWeight * 100).toFloat() else 0f
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.moduleCode,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = module.moduleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${module.credits} Credits",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = gradeColor.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = module.currentGrade ?: "-",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = gradeColor
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", currentPercentage)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Assessments",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                module.assessments.forEach { assessment ->
                    AssessmentRow(assessment = assessment)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun AssessmentRow(assessment: Assessment) {
    val scoreColor = if (assessment.graded && assessment.score != null) {
        val percentage = (assessment.score / assessment.maxScore) * 100
        when {
            percentage >= 80 -> Color(0xFF4CAF50)
            percentage >= 60 -> Color(0xFF2196F3)
            percentage >= 40 -> Color(0xFFFFA000)
            else -> Color(0xFFF44336)
        }
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = assessment.name,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Weight: ${assessment.weightage.toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (assessment.graded && assessment.score != null) {
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${assessment.score.toInt()}/${assessment.maxScore.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                Text(
                    text = "${String.format("%.1f", (assessment.score / assessment.maxScore) * 100)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = scoreColor
                )
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Pending",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

