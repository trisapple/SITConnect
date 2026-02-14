package com.example.sitconnect.features.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.attendance.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

sealed class AttendanceState {
    object Idle : AttendanceState()
    object Loading : AttendanceState()
    data class Success(
        val activeSessions: List<AttendanceSession>,
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

                // Fetch active sessions
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }.time

                val sessionsSnapshot = firestore.collection("attendance_sessions")
                    .get()
                    .await()

                val sessions = sessionsSnapshot.documents.mapNotNull { document ->
                    try {
                        AttendanceSession(
                            id = document.id,
                            moduleCode = document.getString("moduleCode") ?: "",
                            moduleName = document.getString("moduleName") ?: "",
                            sessionType = AttendanceType.valueOf(document.getString("sessionType") ?: "LAB"),
                            sessionDate = document.getTimestamp("sessionDate")?.toDate() ?: Date(),
                            startTime = document.getString("startTime") ?: "",
                            endTime = document.getString("endTime") ?: "",
                            venue = document.getString("venue") ?: "",
                            qrCode = document.getString("qrCode") ?: "",
                            latitude = document.getDouble("latitude") ?: 0.0,
                            longitude = document.getDouble("longitude") ?: 0.0,
                            radiusMeters = document.getLong("radiusMeters")?.toInt() ?: 100
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

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

                // Use sample data if no sessions found
                val finalSessions = if (sessions.isEmpty()) getSampleSessions() else sessions
                val summaries = calculateSummaries(finalSessions, records)

                _attendanceState.value = AttendanceState.Success(
                    activeSessions = finalSessions.filter { isSessionActive(it) },
                    records = records,
                    summaries = summaries
                )
            } catch (e: Exception) {
                val sampleSessions = getSampleSessions()
                _attendanceState.value = AttendanceState.Success(
                    activeSessions = sampleSessions.filter { isSessionActive(it) },
                    records = emptyList(),
                    summaries = calculateSummaries(sampleSessions, emptyList())
                )
            }
        }
    }

    fun markAttendanceWithQR(sessionId: String, qrCode: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                _markAttendanceState.value = MarkAttendanceState.Loading

                // Verify QR code and location
                val state = _attendanceState.value
                if (state is AttendanceState.Success) {
                    val session = state.activeSessions.find { it.id == sessionId }
                    if (session == null) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Session not found")
                        return@launch
                    }

                    // Check QR code
                    if (session.qrCode.isNotEmpty() && session.qrCode != qrCode) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Invalid QR code")
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
                        "studentId" to currentStudentId,
                        "status" to AttendanceStatus.PRESENT.name,
                        "markedAt" to com.google.firebase.Timestamp.now(),
                        "markedVia" to "QR+GPS",
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

    fun markAttendanceWithGPSOnly(sessionId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                _markAttendanceState.value = MarkAttendanceState.Loading

                val state = _attendanceState.value
                if (state is AttendanceState.Success) {
                    val session = state.activeSessions.find { it.id == sessionId }
                    if (session == null) {
                        _markAttendanceState.value = MarkAttendanceState.Error("Session not found")
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
                        "studentId" to currentStudentId,
                        "status" to AttendanceStatus.PRESENT.name,
                        "markedAt" to com.google.firebase.Timestamp.now(),
                        "markedVia" to "GPS",
                        "latitude" to latitude,
                        "longitude" to longitude
                    )

                    firestore.collection("attendance_records")
                        .add(recordData)
                        .await()

                    _markAttendanceState.value = MarkAttendanceState.Success
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

    private fun isSessionActive(session: AttendanceSession): Boolean {
        val today = Calendar.getInstance()
        val sessionCal = Calendar.getInstance().apply { time = session.sessionDate }

        return today.get(Calendar.YEAR) == sessionCal.get(Calendar.YEAR) &&
                today.get(Calendar.DAY_OF_YEAR) == sessionCal.get(Calendar.DAY_OF_YEAR)
    }

    private fun calculateSummaries(
        sessions: List<AttendanceSession>,
        records: List<AttendanceRecord>
    ): List<StudentAttendanceSummary> {
        return sessions.groupBy { it.moduleCode }.map { (moduleCode, moduleSessions) ->
            val moduleRecords = records.filter { record ->
                moduleSessions.any { it.id == record.sessionId }
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

    private fun getSampleSessions(): List<AttendanceSession> {
        val today = Date()
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // Generate time slots relative to current time
        fun formatTime(hour: Int): String = String.format("%02d:00", hour)

        // Create sessions - one ongoing, one upcoming, one past
        return listOf(
            AttendanceSession(
                id = "1",
                moduleCode = "ICT2207",
                moduleName = "Mobile Security",
                sessionType = AttendanceType.LAB,
                sessionDate = today,
                startTime = formatTime(maxOf(8, currentHour - 1)), // Started 1 hour ago or 8am
                endTime = formatTime(maxOf(11, currentHour + 2)), // Ends in 2 hours or 11am
                venue = "SIT Punggol Campus Lab 4A",
                qrCode = "MSL-2207-LAB4A",
                latitude = 1.4136, // SIT Punggol Campus coordinates
                longitude = 103.9123,
                radiusMeters = 500 // Increased radius for testing
            ),
            AttendanceSession(
                id = "2",
                moduleCode = "ICT2205",
                moduleName = "Web Security",
                sessionType = AttendanceType.TUTORIAL,
                sessionDate = today,
                startTime = formatTime(minOf(20, currentHour + 2)), // Upcoming - 2 hours from now
                endTime = formatTime(minOf(22, currentHour + 4)),
                venue = "SIT Punggol Campus Tutorial Room 3",
                qrCode = "WS-2205-TUT3",
                latitude = 1.4136,
                longitude = 103.9123,
                radiusMeters = 500
            ),
            AttendanceSession(
                id = "3",
                moduleCode = "ICT2104",
                moduleName = "Software Engineering",
                sessionType = AttendanceType.LECTURE,
                sessionDate = today,
                startTime = formatTime(minOf(21, currentHour + 4)), // Later session
                endTime = formatTime(minOf(23, currentHour + 6)),
                venue = "SIT Punggol Campus LT1",
                qrCode = "SE-2104-LT1",
                latitude = 1.4136,
                longitude = 103.9123,
                radiusMeters = 500
            )
        )
    }
}

