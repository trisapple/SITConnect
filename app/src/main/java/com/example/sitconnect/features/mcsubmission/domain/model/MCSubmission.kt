package com.example.sitconnect.features.mcsubmission.domain.model

import java.util.Date

enum class MCStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class MCSubmission(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(),
    val reason: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val status: MCStatus = MCStatus.PENDING,
    val submittedAt: Date = Date(),
    val reviewedBy: String? = null,
    val reviewedAt: Date? = null,
    val remarks: String? = null
)

