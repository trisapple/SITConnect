package com.example.sitconnect.features.assignments.domain.model

import java.util.Date

enum class SubmissionStatus {
    NOT_SUBMITTED,
    SUBMITTED,
    GRADED,
    LATE
}

data class Assignment(
    val id: String = "",
    val moduleCode: String = "",
    val moduleName: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: Date = Date(),
    val maxScore: Int = 100,
    val allowedFileTypes: List<String> = listOf("pdf", "zip")
)

data class AssignmentSubmission(
    val id: String = "",
    val assignmentId: String = "",
    val studentId: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val submittedAt: Date = Date(),
    val status: SubmissionStatus = SubmissionStatus.NOT_SUBMITTED,
    val score: Int? = null,
    val feedback: String? = null
)

