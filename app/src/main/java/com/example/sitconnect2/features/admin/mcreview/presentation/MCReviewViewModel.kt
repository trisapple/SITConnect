package com.example.sitconnect2.features.admin.mcreview.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect2.features.mcsubmission.domain.model.MCStatus
import com.example.sitconnect2.features.mcsubmission.domain.model.MCSubmission
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date

data class MCStatistics(
    val totalSubmissions: Int,
    val pendingCount: Int,
    val approvedCount: Int,
    val rejectedCount: Int,
    val averageProcessingDays: Float,
    val submissionsByMonth: Map<String, Int>
)

sealed class MCReviewState {
    object Idle : MCReviewState()
    object Loading : MCReviewState()
    data class Success(
        val submissions: List<MCSubmission> = emptyList(),
        val statistics: MCStatistics? = null,
        val message: String? = null
    ) : MCReviewState()
    data class Error(val message: String) : MCReviewState()
}

class MCReviewViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<MCReviewState>(MCReviewState.Idle)
    val state: StateFlow<MCReviewState> = _state

    fun fetchAllMCSubmissions() {
        viewModelScope.launch {
            try {
                _state.value = MCReviewState.Loading

                withTimeout(15000L) {
                    val snapshot = firestore.collection("mc_submissions").get().await()

                    val submissions = snapshot.documents.mapNotNull { doc ->
                        try {
                            MCSubmission(
                                id = doc.id,
                                studentId = doc.getString("studentId") ?: "",
                                studentName = doc.getString("studentName") ?: "",
                                startDate = doc.getTimestamp("startDate")?.toDate() ?: Date(),
                                endDate = doc.getTimestamp("endDate")?.toDate() ?: Date(),
                                reason = doc.getString("reason") ?: "",
                                fileName = doc.getString("fileName") ?: "",
                                fileUrl = doc.getString("fileUrl") ?: "",
                                status = try {
                                    MCStatus.valueOf(doc.getString("status") ?: "PENDING")
                                } catch (e: Exception) { MCStatus.PENDING },
                                submittedAt = doc.getTimestamp("submittedAt")?.toDate() ?: Date(),
                                reviewedBy = doc.getString("reviewedBy"),
                                reviewedAt = doc.getTimestamp("reviewedAt")?.toDate(),
                                remarks = doc.getString("remarks")
                            )
                        } catch (e: Exception) { null }
                    }.sortedByDescending { it.submittedAt }

                    val statistics = generateStatistics(submissions)

                    _state.value = MCReviewState.Success(
                        submissions = submissions,
                        statistics = statistics
                    )
                }
            } catch (e: Exception) {
                _state.value = MCReviewState.Error(e.message ?: "Failed to fetch MC submissions")
            }
        }
    }

    private fun generateStatistics(submissions: List<MCSubmission>): MCStatistics {
        val calendar = java.util.Calendar.getInstance()
        val monthFormat = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())

        // Calculate average processing time for reviewed submissions
        val reviewedSubmissions = submissions.filter { it.reviewedAt != null }
        val avgDays = if (reviewedSubmissions.isNotEmpty()) {
            reviewedSubmissions.map { submission ->
                val diff = (submission.reviewedAt!!.time - submission.submittedAt.time)
                diff / (1000 * 60 * 60 * 24f)
            }.average().toFloat()
        } else 0f

        // Group by month
        val byMonth = submissions.groupBy { submission ->
            calendar.time = submission.submittedAt
            monthFormat.format(submission.submittedAt)
        }.mapValues { it.value.size }

        return MCStatistics(
            totalSubmissions = submissions.size,
            pendingCount = submissions.count { it.status == MCStatus.PENDING },
            approvedCount = submissions.count { it.status == MCStatus.APPROVED },
            rejectedCount = submissions.count { it.status == MCStatus.REJECTED },
            averageProcessingDays = avgDays,
            submissionsByMonth = byMonth
        )
    }

    fun reviewMC(submissionId: String, approved: Boolean, reviewerName: String, remarks: String = "") {
        viewModelScope.launch {
            try {
                _state.value = MCReviewState.Loading

                withTimeout(10000L) {
                    val updates = hashMapOf<String, Any>(
                        "status" to if (approved) MCStatus.APPROVED.name else MCStatus.REJECTED.name,
                        "reviewedBy" to reviewerName,
                        "reviewedAt" to com.google.firebase.Timestamp.now()
                    )
                    if (remarks.isNotBlank()) {
                        updates["remarks"] = remarks
                    }

                    firestore.collection("mc_submissions").document(submissionId)
                        .update(updates)
                        .await()

                    fetchAllMCSubmissions()
                }
            } catch (e: Exception) {
                _state.value = MCReviewState.Error(e.message ?: "Failed to review MC")
            }
        }
    }
}
