package com.example.sitconnect.features.mcsubmission.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.mcsubmission.domain.model.MCStatus
import com.example.sitconnect.features.mcsubmission.domain.model.MCSubmission
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

sealed class MCSubmissionState {
    object Idle : MCSubmissionState()
    object Loading : MCSubmissionState()
    data class Success(val submissions: List<MCSubmission>) : MCSubmissionState()
    data class Error(val message: String) : MCSubmissionState()
}

sealed class MCFormState {
    object Idle : MCFormState()
    object Loading : MCFormState()
    object Success : MCFormState()
    data class Error(val message: String) : MCFormState()
}

class MCSubmissionViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _mcSubmissionState = MutableStateFlow<MCSubmissionState>(MCSubmissionState.Idle)
    val mcSubmissionState: StateFlow<MCSubmissionState> = _mcSubmissionState

    private val _mcFormState = MutableStateFlow<MCFormState>(MCFormState.Idle)
    val mcFormState: StateFlow<MCFormState> = _mcFormState

    fun fetchMCSubmissions(studentId: String) {
        viewModelScope.launch {
            try {
                _mcSubmissionState.value = MCSubmissionState.Loading
                
                // Fetch without orderBy to avoid requiring composite index
                // Sort locally instead
                val querySnapshot = firestore.collection("mc_submissions")
                    .whereEqualTo("studentId", studentId)
                    .get()
                    .await()

                val submissions = querySnapshot.documents.mapNotNull { document ->
                    try {
                        MCSubmission(
                            id = document.id,
                            studentId = document.getString("studentId") ?: "",
                            studentName = document.getString("studentName") ?: "",
                            startDate = document.getTimestamp("startDate")?.toDate() ?: Date(),
                            endDate = document.getTimestamp("endDate")?.toDate() ?: Date(),
                            reason = document.getString("reason") ?: "",
                            fileName = document.getString("fileName") ?: "",
                            fileUrl = document.getString("fileUrl") ?: "",
                            status = MCStatus.valueOf(document.getString("status") ?: "PENDING"),
                            submittedAt = document.getTimestamp("submittedAt")?.toDate() ?: Date(),
                            reviewedBy = document.getString("reviewedBy"),
                            reviewedAt = document.getTimestamp("reviewedAt")?.toDate(),
                            remarks = document.getString("remarks")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.submittedAt } // Sort locally by submittedAt descending

                _mcSubmissionState.value = MCSubmissionState.Success(submissions)
            } catch (e: Exception) {
                _mcSubmissionState.value = MCSubmissionState.Error(e.message ?: "Failed to fetch MC submissions")
            }
        }
    }

    fun submitMC(
        studentId: String,
        studentName: String,
        startDate: Date,
        endDate: Date,
        reason: String,
        fileName: String
    ) {
        viewModelScope.launch {
            try {
                _mcFormState.value = MCFormState.Loading

                val mcData = hashMapOf(
                    "studentId" to studentId,
                    "studentName" to studentName,
                    "startDate" to com.google.firebase.Timestamp(startDate),
                    "endDate" to com.google.firebase.Timestamp(endDate),
                    "reason" to reason,
                    "fileName" to fileName,
                    "fileUrl" to "", // Would be populated after actual file upload
                    "status" to MCStatus.PENDING.name,
                    "submittedAt" to com.google.firebase.Timestamp.now()
                )

                firestore.collection("mc_submissions")
                    .add(mcData)
                    .await()

                _mcFormState.value = MCFormState.Success

                // Refresh the list
                fetchMCSubmissions(studentId)
            } catch (e: Exception) {
                _mcFormState.value = MCFormState.Error(e.message ?: "Failed to submit MC")
            }
        }
    }

    fun resetFormState() {
        _mcFormState.value = MCFormState.Idle
    }
}

