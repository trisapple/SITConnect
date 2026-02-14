package com.example.sitconnect.features.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.attendance.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.*

sealed class AttendanceState {
    object Idle : AttendanceState()
    object Loading : AttendanceState()
    data class Success(
        val allSessions: List<AttendanceSession>, // All sessions grouped by day
        val records: List<AttendanceRecord>,
        val summaries: List<StudentAttendanceSummary>
    ) : AttendanceState()
    data class Error(val message: String) : AttendanceState()
}

sealed class MarkAttendanceState {
    object Idle : MarkAttendanceState()
    object Loading : MarkAttendanceState()
    object Success : MarkAttendanceState()
    data class Error(val message: String) : MarkAttendanceState()
}

class AttendanceViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _attendanceState = MutableStateFlow<AttendanceState>(AttendanceState.Idle)
    val attendanceState: StateFlow<AttendanceState> = _attendanceState

    private val _markAttendanceState = MutableStateFlow<MarkAttendanceState>(MarkAttendanceState.Idle)
    val markAttendanceState: StateFlow<MarkAttendanceState> = _markAttendanceState

    private var currentStudentId: String = ""

    fun fetchAttendance(studentId: String) {
        currentStudentId = studentId
        viewModelScope.launch {
            try {
                _attendanceState.value = AttendanceState.Loading

                // Query schedules where enrolledStudents array contains the current student's UID
                val schedulesSnapshot = firestore.collection("schedules")
                    .whereArrayContains("enrolledStudents", studentId)
                    .get()
                    .await()

                // Get all sessions with dayOfWeek, sorted by day and time
                val allSessions = schedulesSnapshot.documents.mapNotNull { document ->
                    try {
                        val classTypeStr = document.getString("classType") ?: "LECTURE"
                        val sessionType = when (classTypeStr.uppercase()) {
                            "LAB" -> AttendanceType.LAB
                            "TUTORIAL" -> AttendanceType.TUTORIAL
                            else -> AttendanceType.LECTURE
                        }
                        val dayOfWeek = document.getLong("dayOfWeek")?.toInt() ?: 1

                        AttendanceSession(
                            id = document.id,
                            scheduleId = document.id,
                            moduleCode = document.getString("moduleCode") ?: "",
                            moduleName = document.getString("moduleName") ?: "",
                            sessionType = sessionType,
                            dayOfWeek = dayOfWeek,
                            sessionDate = Date(),
                            startTime = document.getString("startTime") ?: "",
                            endTime = document.getString("endTime") ?: "",
                            venue = document.getString("venue") ?: "",
                            attendanceCode = document.getString("attendanceCode") ?: "",
                            latitude = document.getDouble("latitude") ?: 0.0,
                            longitude = document.getDouble("longitude") ?: 0.0,
                            radiusMeters = document.getLong("radiusMeters")?.toInt() ?: 100,
                            lecturerId = document.getString("lecturerId") ?: "",
                            lecturerName = document.getString("lecturerName") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

                // Fetch student's attendance records
                val recordsSnapshot = firestore.collection("attendance_records")
                    .whereEqualTo("studentId", studentId)
                    .get()
                    .await()

                val records = recordsSnapshot.documents.mapNotNull { document ->
                    try {
                        AttendanceRecord(
                            id = document.id,
                            sessionId = document.getString("sessionId") ?: "",
                            scheduleId = document.getString("scheduleId") ?: "",
                            studentId = document.getString("studentId") ?: "",
                            status = AttendanceStatus.valueOf(document.getString("status") ?: "ABSENT"),
                            markedAt = document.getTimestamp("markedAt")?.toDate(),
                            markedVia = document.getString("markedVia") ?: "",
                            latitude = document.getDouble("latitude"),
                            longitude = document.getDouble("longitude")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val summaries = calculateSummaries(allSessions, records)

                _attendanceState.value = AttendanceState.Success(
                    allSessions = allSessions,
                    records = records,
                    summaries = summaries
                )
            } catch (e: Exception) {
                _attendanceState.value = AttendanceState.Error(e.message ?: "Failed to fetch attendance")
            }
        }
    }

    fun markAttendanceWithCode(sessionId: String, enteredCode: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                _markAttendanceState.value = MarkAttendanceState.Loading

                val state = _attendanceState.value
                if (state is AttendanceState.Success) {
                    val session = state.allSessions.find { it.id == sessionId }
                    if (session == null) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Session not found")
                        return@launch
                    }

                    // Check if attendance code is set by lecturer
                    if (session.attendanceCode.isEmpty()) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Attendance code not yet generated by lecturer")
                        return@launch
                    }

                    // Validate the entered code
                    if (session.attendanceCode != enteredCode) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Invalid attendance code")
                        return@launch
                    }

                    // Check location
                    val distance = calculateDistance(
                        session.latitude, session.longitude,
                        latitude, longitude
                    )

                    if (session.latitude != 0.0 && distance > session.radiusMeters) {
                        _markAttendanceState.value = MarkAttendanceState.Error(
                            "You are too far from the venue (${distance.toInt()}m away, max ${session.radiusMeters}m)"
                        )
                        return@launch
                    }

                    // Mark attendance
                    val recordData = hashMapOf(
                        "sessionId" to sessionId,
                        "scheduleId" to session.scheduleId,
                        "studentId" to currentStudentId,
                        "status" to AttendanceStatus.PRESENT.name,
                        "markedAt" to com.google.firebase.Timestamp.now(),
                        "markedVia" to "CODE+GPS",
                        "latitude" to latitude,
                        "longitude" to longitude
                    )

                    firestore.collection("attendance_records")
                        .add(recordData)
                        .await()

                    _markAttendanceState.value = MarkAttendanceState.Success

                    // Refresh attendance
                    fetchAttendance(currentStudentId)
                }
            } catch (e: Exception) {
                _markAttendanceState.value = MarkAttendanceState.Error(e.message ?: "Failed to mark attendance")
            }
        }
    }

    fun resetMarkState() {
        _markAttendanceState.value = MarkAttendanceState.Idle
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }


    private fun calculateSummaries(
        sessions: List<AttendanceSession>,
        records: List<AttendanceRecord>
    ): List<StudentAttendanceSummary> {
        return sessions.groupBy { it.moduleCode }.map { (moduleCode, moduleSessions) ->
            val moduleRecords = records.filter { record ->
                moduleSessions.any { it.id == record.sessionId || it.scheduleId == record.scheduleId }
            }

            val attended = moduleRecords.count { it.status == AttendanceStatus.PRESENT }
            val late = moduleRecords.count { it.status == AttendanceStatus.LATE }
            val excused = moduleRecords.count { it.status == AttendanceStatus.EXCUSED }
            val absent = moduleSessions.size - attended - late - excused

            StudentAttendanceSummary(
                moduleCode = moduleCode,
                moduleName = moduleSessions.firstOrNull()?.moduleName ?: "",
                totalSessions = moduleSessions.size,
                attended = attended,
                absent = maxOf(0, absent),
                late = late,
                excused = excused,
                attendancePercentage = if (moduleSessions.isNotEmpty()) {
                    (attended + late + excused).toFloat() / moduleSessions.size * 100
                } else 0f
            )
        }
    }
}
