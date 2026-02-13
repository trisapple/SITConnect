package com.example.sitconnect.features.mcsubmission.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.mcsubmission.domain.model.MCStatus
import com.example.sitconnect.features.mcsubmission.domain.model.MCSubmission
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
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
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()

    private val _mcSubmissionState = MutableStateFlow<MCSubmissionState>(MCSubmissionState.Idle)
    val mcSubmissionState: StateFlow<MCSubmissionState> = _mcSubmissionState

    private val _mcFormState = MutableStateFlow<MCFormState>(MCFormState.Idle)
    val mcFormState: StateFlow<MCFormState> = _mcFormState

    fun fetchMCSubmissions(studentId: String) {
        viewModelScope.launch {
            try {
                _mcSubmissionState.value = MCSubmissionState.Loading
                
                // Add timeout to prevent hanging indefinitely
                withTimeout(15000L) { // 15 second timeout
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
                    }.sortedByDescending { it.submittedAt }

                    _mcSubmissionState.value = MCSubmissionState.Success(submissions)
                }
            } catch (e: TimeoutCancellationException) {
                _mcSubmissionState.value = MCSubmissionState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _mcSubmissionState.value = MCSubmissionState.Error(e.message ?: "Failed to fetch MC submissions")
            }
        }
    }

    fun submitMC(
        context: Context,
        studentId: String,
        studentName: String,
        startDate: Date,
        endDate: Date,
        reason: String,
        fileName: String,
        fileUri: Uri?
    ) {
        viewModelScope.launch {
            try {
                _mcFormState.value = MCFormState.Loading

                withTimeout(60000L) { // 60 second timeout for file upload
                    var fileUrl = ""
                    var finalFileName = fileName

                    // Upload file to Firebase Storage if URI is provided
                    if (fileUri != null) {
                        val storageRef = storage.reference
                        val timestamp = System.currentTimeMillis()
                        val fileExtension = getFileExtension(context, fileUri)
                        finalFileName = if (fileName.isNotEmpty()) fileName else "mc_${timestamp}.${fileExtension}"
                        val mcFileRef = storageRef.child("mc_submissions/$studentId/${timestamp}_$finalFileName")

                        // Upload file
                        mcFileRef.putFile(fileUri).await()

                        // Get download URL
                        fileUrl = mcFileRef.downloadUrl.await().toString()
                    }

                    val mcData = hashMapOf(
                        "studentId" to studentId,
                        "studentName" to studentName,
                        "startDate" to com.google.firebase.Timestamp(startDate),
                        "endDate" to com.google.firebase.Timestamp(endDate),
                        "reason" to reason,
                        "fileName" to finalFileName,
                        "fileUrl" to fileUrl,
                        "status" to MCStatus.PENDING.name,
                        "submittedAt" to com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("mc_submissions")
                        .add(mcData)
                        .await()

                    _mcFormState.value = MCFormState.Success

                    // Refresh the list
                    fetchMCSubmissions(studentId)
                }
            } catch (e: TimeoutCancellationException) {
                _mcFormState.value = MCFormState.Error("Upload timed out. Please try again with a smaller file.")
            } catch (e: Exception) {
                _mcFormState.value = MCFormState.Error(e.message ?: "Failed to submit MC")
            }
        }
    }

    private fun getFileExtension(context: Context, uri: Uri): String {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri)
        return when (mimeType) {
            "application/pdf" -> "pdf"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            else -> "file"
        }
    }

    fun resetFormState() {
        _mcFormState.value = MCFormState.Idle
    }
}

