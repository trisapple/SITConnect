package com.example.sitconnect.features.attendance.domain.model

import java.util.Date

enum class AttendanceType {
    LAB,
    TUTORIAL,
    LECTURE
}

enum class AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    EXCUSED
}

data class AttendanceSession(
    val id: String = "",
    val moduleCode: String = "",
    val moduleName: String = "",
    val sessionType: AttendanceType = AttendanceType.LAB,
    val sessionDate: Date = Date(),
    val startTime: String = "",
    val endTime: String = "",
    val venue: String = "",
    val qrCode: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 100
)

data class AttendanceRecord(
    val id: String = "",
    val sessionId: String = "",
    val studentId: String = "",
    val status: AttendanceStatus = AttendanceStatus.ABSENT,
    val markedAt: Date? = null,
    val markedVia: String = "", // "QR" or "GPS" or "MANUAL"
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class StudentAttendanceSummary(
    val moduleCode: String = "",
    val moduleName: String = "",
    val totalSessions: Int = 0,
    val attended: Int = 0,
    val absent: Int = 0,
    val late: Int = 0,
    val excused: Int = 0,
    val attendancePercentage: Float = 0f
)

