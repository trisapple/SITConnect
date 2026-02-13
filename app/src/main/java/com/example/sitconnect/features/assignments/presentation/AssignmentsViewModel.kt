package com.example.sitconnect.features.assignments.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.assignments.domain.model.Assignment
import com.example.sitconnect.features.assignments.domain.model.AssignmentSubmission
import com.example.sitconnect.features.assignments.domain.model.SubmissionStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

sealed class AssignmentsState {
    object Idle : AssignmentsState()
    object Loading : AssignmentsState()
    data class Success(val assignments: List<Pair<Assignment, AssignmentSubmission?>>) : AssignmentsState()
    data class Error(val message: String) : AssignmentsState()
}

sealed class SubmitAssignmentState {
    object Idle : SubmitAssignmentState()
    object Loading : SubmitAssignmentState()
    object Success : SubmitAssignmentState()
    data class Error(val message: String) : SubmitAssignmentState()
}

class AssignmentsViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _assignmentsState = MutableStateFlow<AssignmentsState>(AssignmentsState.Idle)
    val assignmentsState: StateFlow<AssignmentsState> = _assignmentsState

    private val _submitState = MutableStateFlow<SubmitAssignmentState>(SubmitAssignmentState.Idle)
    val submitState: StateFlow<SubmitAssignmentState> = _submitState

    private var currentStudentId: String = ""

    fun fetchAssignments(studentId: String) {
        currentStudentId = studentId
        viewModelScope.launch {
            try {
                _assignmentsState.value = AssignmentsState.Loading

                // Fetch assignments
                val assignmentsSnapshot = firestore.collection("assignments")
                    .get()
                    .await()

                val assignments = assignmentsSnapshot.documents.mapNotNull { document ->
                    try {
                        Assignment(
                            id = document.id,
                            moduleCode = document.getString("moduleCode") ?: "",
                            moduleName = document.getString("moduleName") ?: "",
                            title = document.getString("title") ?: "",
                            description = document.getString("description") ?: "",
                            dueDate = document.getTimestamp("dueDate")?.toDate() ?: Date(),
                            maxScore = document.getLong("maxScore")?.toInt() ?: 100,
                            allowedFileTypes = (document.get("allowedFileTypes") as? List<*>)?.mapNotNull { it as? String } ?: listOf("pdf", "zip")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Fetch submissions for this student
                val submissionsSnapshot = firestore.collection("assignment_submissions")
                    .whereEqualTo("studentId", studentId)
                    .get()
                    .await()

                val submissions = submissionsSnapshot.documents.mapNotNull { document ->
                    try {
                        AssignmentSubmission(
                            id = document.id,
                            assignmentId = document.getString("assignmentId") ?: "",
                            studentId = document.getString("studentId") ?: "",
                            fileName = document.getString("fileName") ?: "",
                            fileUrl = document.getString("fileUrl") ?: "",
                            submittedAt = document.getTimestamp("submittedAt")?.toDate() ?: Date(),
                            status = SubmissionStatus.valueOf(document.getString("status") ?: "NOT_SUBMITTED"),
                            score = document.getLong("score")?.toInt(),
                            feedback = document.getString("feedback")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                // Combine assignments with their submissions
                val assignmentsWithSubmissions = if (assignments.isEmpty()) {
                    getSampleAssignments().map { assignment ->
                        assignment to submissions.find { it.assignmentId == assignment.id }
                    }
                } else {
                    assignments.map { assignment ->
                        assignment to submissions.find { it.assignmentId == assignment.id }
                    }
                }.sortedBy { it.first.dueDate }

                _assignmentsState.value = AssignmentsState.Success(assignmentsWithSubmissions)
            } catch (e: Exception) {
                _assignmentsState.value = AssignmentsState.Success(
                    getSampleAssignments().map { it to null }
                )
            }
        }
    }

    fun submitAssignment(assignmentId: String, fileName: String) {
        viewModelScope.launch {
            try {
                _submitState.value = SubmitAssignmentState.Loading

                val submissionData = hashMapOf(
                    "assignmentId" to assignmentId,
                    "studentId" to currentStudentId,
                    "fileName" to fileName,
                    "fileUrl" to "", // Would be populated after actual file upload
                    "submittedAt" to com.google.firebase.Timestamp.now(),
                    "status" to SubmissionStatus.SUBMITTED.name
                )

                firestore.collection("assignment_submissions")
                    .add(submissionData)
                    .await()

                _submitState.value = SubmitAssignmentState.Success

                // Refresh assignments
                fetchAssignments(currentStudentId)
            } catch (e: Exception) {
                _submitState.value = SubmitAssignmentState.Error(e.message ?: "Failed to submit assignment")
            }
        }
    }

    fun resetSubmitState() {
        _submitState.value = SubmitAssignmentState.Idle
    }

    private fun getSampleAssignments(): List<Assignment> {
        val calendar = Calendar.getInstance()

        // Create dates relative to current date
        fun daysFromNow(days: Int): Date {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, days)
            return cal.time
        }

        return listOf(
            Assignment(
                id = "1",
                moduleCode = "ICT2207",
                moduleName = "Mobile Security",
                title = "Assignment 1 - Secure Android App",
                description = "Develop a secure Android application implementing authentication and data encryption.",
                dueDate = daysFromNow(-5), // Past due (5 days ago)
                maxScore = 100,
                allowedFileTypes = listOf("pdf", "zip")
            ),
            Assignment(
                id = "2",
                moduleCode = "ICT2207",
                moduleName = "Mobile Security",
                title = "Assignment 2 - Penetration Testing Report",
                description = "Perform security testing on a mobile app and document vulnerabilities found.",
                dueDate = daysFromNow(7), // Due in 7 days
                maxScore = 100,
                allowedFileTypes = listOf("pdf")
            ),
            Assignment(
                id = "3",
                moduleCode = "ICT2205",
                moduleName = "Web Security",
                title = "Project - Secure Web Application",
                description = "Build a secure web application with proper input validation and session management.",
                dueDate = daysFromNow(2), // Due in 2 days (urgent)
                maxScore = 100,
                allowedFileTypes = listOf("pdf", "zip")
            ),
            Assignment(
                id = "4",
                moduleCode = "ICT2104",
                moduleName = "Software Engineering",
                title = "Group Project - Software Design Document",
                description = "Submit your team's software design document including UML diagrams.",
                dueDate = daysFromNow(14), // Due in 2 weeks
                maxScore = 50,
                allowedFileTypes = listOf("pdf")
            )
        )
    }
}

