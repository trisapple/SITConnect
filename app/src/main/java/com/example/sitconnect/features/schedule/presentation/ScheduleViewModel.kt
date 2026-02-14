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

    fun fetchAttendanceRecords(scheduleId: String, enrolledStudents: List<String>) {
        viewModelScope.launch {
            try {
                _attendanceRecordsState.value = AttendanceRecordsState.Loading

                // Fetch attendance records for this schedule
                val recordsSnapshot = firestore.collection("attendance_records")
                    .whereEqualTo("scheduleId", scheduleId)
                    .get()
                    .await()

                val presentStudentIds = recordsSnapshot.documents.mapNotNull { it.getString("studentId") }.toSet()

                // Fetch all enrolled students' details
                val attendanceList = mutableListOf<StudentAttendanceInfo>()
                for (studentUid in enrolledStudents) {
                    try {
                        val userDoc = firestore.collection("users")
                            .document(studentUid)
                            .get()
                            .await()

                        if (userDoc.exists()) {
                            val record = recordsSnapshot.documents.find { it.getString("studentId") == studentUid }
                            attendanceList.add(
                                StudentAttendanceInfo(
                                    studentId = studentUid,
                                    studentName = userDoc.getString("name") ?: "",
                                    studentEmail = userDoc.getString("email") ?: "",
                                    isPresent = presentStudentIds.contains(studentUid),
                                    markedAt = record?.getTimestamp("markedAt")?.toDate(),
                                    markedVia = record?.getString("markedVia") ?: ""
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Skip this student
                    }
                }

                _attendanceRecordsState.value = AttendanceRecordsState.Success(
                    records = attendanceList.sortedBy { it.studentName },
                    presentCount = presentStudentIds.size,
                    totalCount = enrolledStudents.size
                )
            } catch (e: Exception) {
                _attendanceRecordsState.value = AttendanceRecordsState.Error(e.message ?: "Failed to fetch attendance records")
            }
        }
    }

    fun clearAttendanceRecords() {
        _attendanceRecordsState.value = AttendanceRecordsState.Idle
    }
}

sealed class AttendanceRecordsState {
    object Idle : AttendanceRecordsState()
    object Loading : AttendanceRecordsState()
    data class Success(
        val records: List<StudentAttendanceInfo>,
        val presentCount: Int,
        val totalCount: Int
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
