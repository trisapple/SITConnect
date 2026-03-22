package com.example.sitconnect.features.schedule.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.schedule.domain.model.ClassType
import com.example.sitconnect.features.schedule.domain.model.ScheduleEntry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.random.Random

import java.util.*

sealed class ScheduleState {
    object Idle : ScheduleState()
    object Loading : ScheduleState()
    data class Success(val schedule: List<ScheduleEntry>) : ScheduleState()
    data class Error(val message: String) : ScheduleState()
}

sealed class AttendanceCodeState {
    object Idle : AttendanceCodeState()
    object Loading : AttendanceCodeState()
    data class Success(val code: String) : AttendanceCodeState()
    data class Error(val message: String) : AttendanceCodeState()
}

class ScheduleViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _scheduleState = MutableStateFlow<ScheduleState>(ScheduleState.Idle)
    val scheduleState: StateFlow<ScheduleState> = _scheduleState

    private val _attendanceCodeState = MutableStateFlow<AttendanceCodeState>(AttendanceCodeState.Idle)
    val attendanceCodeState: StateFlow<AttendanceCodeState> = _attendanceCodeState

    private var currentLecturerId: String = ""

    fun fetchLecturerSchedule(lecturerId: String) {
        currentLecturerId = lecturerId
        viewModelScope.launch {
            try {
                _scheduleState.value = ScheduleState.Loading

                withTimeout(15000L) {
                    val scheduleSnapshot = firestore.collection("schedules")
                        .whereEqualTo("lecturerId", lecturerId)
                        .get()
                        .await()

                    val schedule = scheduleSnapshot.documents.mapNotNull { document ->
                        try {
                            ScheduleEntry(
                                id = document.id,
                                moduleId = document.getString("moduleId") ?: "",
                                moduleCode = document.getString("moduleCode") ?: "",
                                moduleName = document.getString("moduleName") ?: "",
                                classType = try {
                                    ClassType.valueOf(document.getString("classType") ?: "LECTURE")
                                } catch (e: Exception) {
                                    ClassType.LECTURE
                                },
                                dayOfWeek = document.getLong("dayOfWeek")?.toInt() ?: 1,
                                startTime = document.getString("startTime") ?: "",
                                endTime = document.getString("endTime") ?: "",
                                venue = document.getString("venue") ?: "",
                                lecturerId = document.getString("lecturerId") ?: "",
                                lecturerName = document.getString("lecturerName") ?: "",
                                enrolledStudents = (document.get("enrolledStudents") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                latitude = document.getDouble("latitude") ?: 0.0,
                                longitude = document.getDouble("longitude") ?: 0.0,
                                radiusMeters = document.getLong("radiusMeters")?.toInt() ?: 100,
                                attendanceCode = document.getString("attendanceCode") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

                    _scheduleState.value = ScheduleState.Success(schedule = schedule)
                }
            } catch (e: TimeoutCancellationException) {
                _scheduleState.value = ScheduleState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _scheduleState.value = ScheduleState.Error(e.message ?: "Failed to fetch schedule")
            }
        }
    }

    fun fetchStudentSchedule(studentId: String) {
        viewModelScope.launch {
            try {
                _scheduleState.value = ScheduleState.Loading

                withTimeout(15000L) {
                    val scheduleSnapshot = firestore.collection("schedules")
                        .whereArrayContains("enrolledStudents", studentId)
                        .get()
                        .await()

                    val schedule = scheduleSnapshot.documents.mapNotNull { document ->
                        try {
                            ScheduleEntry(
                                id = document.id,
                                moduleId = document.getString("moduleId") ?: "",
                                moduleCode = document.getString("moduleCode") ?: "",
                                moduleName = document.getString("moduleName") ?: "",
                                classType = try {
                                    ClassType.valueOf(document.getString("classType") ?: "LECTURE")
                                } catch (e: Exception) {
                                    ClassType.LECTURE
                                },
                                dayOfWeek = document.getLong("dayOfWeek")?.toInt() ?: 1,
                                startTime = document.getString("startTime") ?: "",
                                endTime = document.getString("endTime") ?: "",
                                venue = document.getString("venue") ?: "",
                                lecturerId = document.getString("lecturerId") ?: "",
                                lecturerName = document.getString("lecturerName") ?: "",
                                enrolledStudents = (document.get("enrolledStudents") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                latitude = document.getDouble("latitude") ?: 0.0,
                                longitude = document.getDouble("longitude") ?: 0.0,
                                radiusMeters = document.getLong("radiusMeters")?.toInt() ?: 100,
                                attendanceCode = document.getString("attendanceCode") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

                    _scheduleState.value = ScheduleState.Success(schedule = schedule)
                }
            } catch (e: TimeoutCancellationException) {
                _scheduleState.value = ScheduleState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _scheduleState.value = ScheduleState.Error(e.message ?: "Failed to fetch schedule")
            }
        }
    }

    /**
     * Generate a new attendance code for a session
     */
    fun generateAttendanceCode(scheduleId: String) {
        viewModelScope.launch {
            try {
                _attendanceCodeState.value = AttendanceCodeState.Loading

                // Generate a random 6-character alphanumeric code
                val code = generateRandomCode()

                // Update the schedule document with the new attendance code
                firestore.collection("schedules")
                    .document(scheduleId)
                    .update("attendanceCode", code)
                    .await()

                _attendanceCodeState.value = AttendanceCodeState.Success(code)

                // Refresh the schedule to show updated code
                if (currentLecturerId.isNotEmpty()) {
                    fetchLecturerSchedule(currentLecturerId)
                }
            } catch (e: Exception) {
                _attendanceCodeState.value = AttendanceCodeState.Error(e.message ?: "Failed to generate code")
            }
        }
    }

    /**
     * Clear the attendance code for a session (end attendance taking)
     */
    fun clearAttendanceCode(scheduleId: String) {
        viewModelScope.launch {
            try {
                _attendanceCodeState.value = AttendanceCodeState.Loading

                firestore.collection("schedules")
                    .document(scheduleId)
                    .update("attendanceCode", "")
                    .await()

                _attendanceCodeState.value = AttendanceCodeState.Idle

                // Refresh the schedule
                if (currentLecturerId.isNotEmpty()) {
                    fetchLecturerSchedule(currentLecturerId)
                }
            } catch (e: Exception) {
                _attendanceCodeState.value = AttendanceCodeState.Error(e.message ?: "Failed to clear code")
            }
        }
    }

    fun resetAttendanceCodeState() {
        _attendanceCodeState.value = AttendanceCodeState.Idle
    }

    private fun generateRandomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Excluded confusing chars like 0, O, 1, I
        return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    // Attendance records for lecturer view
    private val _attendanceRecordsState = MutableStateFlow<AttendanceRecordsState>(AttendanceRecordsState.Idle)
    val attendanceRecordsState: StateFlow<AttendanceRecordsState> = _attendanceRecordsState

    // Cache for grouped records so we don't re-fetch when switching weeks
    private var cachedWeeklyRecords: Map<String, List<WeeklyRecord>> = emptyMap()
    private var cachedStudentDetails: Map<String, Pair<String, String>> = emptyMap() // uid -> (name, email)
    private var cachedEnrolledStudents: List<String> = emptyList()

    fun fetchAttendanceRecords(scheduleId: String, enrolledStudents: List<String>) {
        viewModelScope.launch {
            try {
                _attendanceRecordsState.value = AttendanceRecordsState.Loading

                val currentWeek = getWeekLabel()

                // Fetch ALL attendance records for this schedule (all weeks)
                val recordsSnapshot = firestore.collection("attendance_records")
                    .whereEqualTo("scheduleId", scheduleId)
                    .get()
                    .await()

                // Parse records with their weekLabel
                val allRecords = recordsSnapshot.documents.mapNotNull { doc ->
                    try {
                        WeeklyRecord(
                            studentId = doc.getString("studentId") ?: "",
                            weekLabel = doc.getString("weekLabel") ?: "",
                            markedAt = doc.getTimestamp("markedAt")?.toDate(),
                            markedVia = doc.getString("markedVia") ?: ""
                        )
                    } catch (e: Exception) { null }
                }

                // Group by weekLabel
                cachedWeeklyRecords = allRecords.groupBy { it.weekLabel }
                cachedEnrolledStudents = enrolledStudents

                // Fetch all enrolled students' details and cache them
                val studentDetails = mutableMapOf<String, Pair<String, String>>()
                for (studentUid in enrolledStudents) {
                    try {
                        val userDoc = firestore.collection("users")
                            .document(studentUid)
                            .get()
                            .await()
                        if (userDoc.exists()) {
                            studentDetails[studentUid] = Pair(
                                userDoc.getString("name") ?: "",
                                userDoc.getString("email") ?: ""
                            )
                        }
                    } catch (e: Exception) {
                        // Skip this student
                    }
                }
                cachedStudentDetails = studentDetails

                // Get all available weeks sorted descending (newest first)
                val availableWeeks = cachedWeeklyRecords.keys
                    .filter { it.isNotEmpty() }
                    .sortedDescending()

                // Build attendance list for the current week (or first available)
                val selectedWeek = if (availableWeeks.contains(currentWeek)) currentWeek
                    else if (availableWeeks.isNotEmpty()) availableWeeks.first()
                    else currentWeek

                val attendanceList = buildAttendanceListForWeek(selectedWeek)

                _attendanceRecordsState.value = AttendanceRecordsState.Success(
                    records = attendanceList,
                    presentCount = attendanceList.count { it.isPresent },
                    totalCount = enrolledStudents.size,
                    weekLabel = selectedWeek,
                    availableWeeks = if (availableWeeks.contains(currentWeek)) availableWeeks
                        else (listOf(currentWeek) + availableWeeks).sortedDescending()
                )
            } catch (e: Exception) {
                _attendanceRecordsState.value = AttendanceRecordsState.Error(e.message ?: "Failed to fetch attendance records")
            }
        }
    }

    /**
     * Switch to a different week's attendance view without re-fetching from Firestore.
     */
    fun selectWeek(weekLabel: String) {
        val currentState = _attendanceRecordsState.value
        if (currentState is AttendanceRecordsState.Success) {
            val attendanceList = buildAttendanceListForWeek(weekLabel)
            _attendanceRecordsState.value = currentState.copy(
                records = attendanceList,
                presentCount = attendanceList.count { it.isPresent },
                weekLabel = weekLabel
            )
        }
    }

    private fun buildAttendanceListForWeek(weekLabel: String): List<StudentAttendanceInfo> {
        val weekRecords = cachedWeeklyRecords[weekLabel] ?: emptyList()
        val presentStudentIds = weekRecords.map { it.studentId }.toSet()

        return cachedEnrolledStudents.mapNotNull { studentUid ->
            val details = cachedStudentDetails[studentUid] ?: return@mapNotNull null
            val record = weekRecords.find { it.studentId == studentUid }
            StudentAttendanceInfo(
                studentId = studentUid,
                studentName = details.first,
                studentEmail = details.second,
                isPresent = presentStudentIds.contains(studentUid),
                markedAt = record?.markedAt,
                markedVia = record?.markedVia ?: ""
            )
        }.sortedBy { it.studentName }
    }

    fun clearAttendanceRecords() {
        _attendanceRecordsState.value = AttendanceRecordsState.Idle
        cachedWeeklyRecords = emptyMap()
        cachedStudentDetails = emptyMap()
        cachedEnrolledStudents = emptyList()
    }

    /**
     * Returns the ISO week label for the given date, e.g. "2026-W12".
     */
    private fun getWeekLabel(date: Date = Date()): String {
        val cal = Calendar.getInstance().apply {
            time = date
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4 // ISO 8601
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return String.format("%d-W%02d", year, week)
    }
}

data class WeeklyRecord(
    val studentId: String,
    val weekLabel: String,
    val markedAt: java.util.Date?,
    val markedVia: String
)

sealed class AttendanceRecordsState {
    object Idle : AttendanceRecordsState()
    object Loading : AttendanceRecordsState()
    data class Success(
        val records: List<StudentAttendanceInfo>,
        val presentCount: Int,
        val totalCount: Int,
        val weekLabel: String = "",
        val availableWeeks: List<String> = emptyList()
    ) : AttendanceRecordsState()
    data class Error(val message: String) : AttendanceRecordsState()
}

data class StudentAttendanceInfo(
    val studentId: String,
    val studentName: String,
    val studentEmail: String,
    val isPresent: Boolean,
    val markedAt: java.util.Date?,
    val markedVia: String
)
