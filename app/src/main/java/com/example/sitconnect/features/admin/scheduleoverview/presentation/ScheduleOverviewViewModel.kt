package com.example.sitconnect.features.admin.scheduleoverview.presentation

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

data class RoomUtilization(
    val venue: String,
    val totalSlots: Int,
    val bookedSlots: Int,
    val utilizationPercent: Float
)

data class ScheduleConflict(
    val venue: String,
    val dayOfWeek: Int,
    val timeSlot: String,
    val conflictingEntries: List<ScheduleEntry>
)

data class LecturerScheduleGroup(
    val lecturerId: String,
    val lecturerName: String,
    val schedules: List<ScheduleEntry>
)

sealed class ScheduleOverviewState {
    object Idle : ScheduleOverviewState()
    object Loading : ScheduleOverviewState()
    data class Success(
        val schedules: List<ScheduleEntry> = emptyList(),
        val lecturerGroups: List<LecturerScheduleGroup> = emptyList(),
        val roomUtilization: List<RoomUtilization> = emptyList(),
        val conflicts: List<ScheduleConflict> = emptyList()
    ) : ScheduleOverviewState()
    data class Error(val message: String) : ScheduleOverviewState()
}

class ScheduleOverviewViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ScheduleOverviewState>(ScheduleOverviewState.Idle)
    val state: StateFlow<ScheduleOverviewState> = _state

    fun fetchAllSchedules() {
        viewModelScope.launch {
            try {
                _state.value = ScheduleOverviewState.Loading

                withTimeout(15000L) {
                    // Fetch schedules, modules, and users
                    val schedulesSnapshot = firestore.collection("schedules").get().await()
                    val modulesSnapshot = firestore.collection("modules").get().await()
                    val usersSnapshot = firestore.collection("users").get().await()

                    // Get all valid student IDs (users with student role)
                    val validStudentIds = usersSnapshot.documents.mapNotNull { doc ->
                        val roles = doc.get("roles") as? Map<*, *>
                        if (roles?.get("student") == true) doc.id else null
                    }.toSet()

                    // Build a map of moduleId -> enrolledStudents from modules (source of truth)
                    // Filter to only include valid student IDs
                    val moduleEnrollments = modulesSnapshot.documents.associate { doc ->
                        val moduleId = doc.id
                        val enrolledStudents = (doc.get("enrolledStudents") as? List<*>)
                            ?.mapNotNull { it as? String }
                            ?.filter { validStudentIds.contains(it) }  // Only count valid students
                            ?: emptyList()
                        moduleId to enrolledStudents
                    }

                    val schedules = schedulesSnapshot.documents.mapNotNull { doc ->
                        try {
                            val moduleId = doc.getString("moduleId") ?: ""
                            // Use student list from module (source of truth) - already filtered
                            val enrolledStudents = moduleEnrollments[moduleId] ?: emptyList()

                            ScheduleEntry(
                                id = doc.id,
                                moduleId = moduleId,
                                moduleCode = doc.getString("moduleCode") ?: "",
                                moduleName = doc.getString("moduleName") ?: "",
                                classType = try {
                                    ClassType.valueOf(doc.getString("classType") ?: "LECTURE")
                                } catch (e: Exception) { ClassType.LECTURE },
                                dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1,
                                startTime = doc.getString("startTime") ?: "",
                                endTime = doc.getString("endTime") ?: "",
                                venue = doc.getString("venue") ?: "",
                                lecturerId = doc.getString("lecturerId") ?: "",
                                lecturerName = doc.getString("lecturerName") ?: "",
                                enrolledStudents = enrolledStudents,
                                latitude = doc.getDouble("latitude") ?: 0.0,
                                longitude = doc.getDouble("longitude") ?: 0.0,
                                radiusMeters = doc.getLong("radiusMeters")?.toInt() ?: 100,
                                attendanceCode = doc.getString("attendanceCode") ?: ""
                            )
                        } catch (e: Exception) { null }
                    }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

                    val roomUtilization = calculateRoomUtilization(schedules)
                    val conflicts = detectConflicts(schedules)
                    val lecturerGroups = groupByLecturer(schedules)

                    _state.value = ScheduleOverviewState.Success(
                        schedules = schedules,
                        lecturerGroups = lecturerGroups,
                        roomUtilization = roomUtilization,
                        conflicts = conflicts
                    )
                }
            } catch (e: Exception) {
                _state.value = ScheduleOverviewState.Error(e.message ?: "Failed to fetch schedules")
            }
        }
    }

    private fun calculateRoomUtilization(schedules: List<ScheduleEntry>): List<RoomUtilization> {
        val venues = schedules.groupBy { it.venue }
        val totalPossibleSlots = 5 * 8 // 5 days * 8 time slots per day (approx)

        return venues.map { (venue, entries) ->
            RoomUtilization(
                venue = venue,
                totalSlots = totalPossibleSlots,
                bookedSlots = entries.size,
                utilizationPercent = (entries.size.toFloat() / totalPossibleSlots * 100).coerceIn(0f, 100f)
            )
        }.sortedByDescending { it.utilizationPercent }
    }

    private fun detectConflicts(schedules: List<ScheduleEntry>): List<ScheduleConflict> {
        val conflicts = mutableListOf<ScheduleConflict>()

        // Group by venue and day
        val grouped = schedules.groupBy { "${it.venue}_${it.dayOfWeek}" }

        grouped.forEach { (_, entries) ->
            if (entries.size > 1) {
                // Check for time overlaps
                for (i in entries.indices) {
                    for (j in i + 1 until entries.size) {
                        val e1 = entries[i]
                        val e2 = entries[j]

                        if (hasTimeOverlap(e1.startTime, e1.endTime, e2.startTime, e2.endTime)) {
                            conflicts.add(
                                ScheduleConflict(
                                    venue = e1.venue,
                                    dayOfWeek = e1.dayOfWeek,
                                    timeSlot = "${e1.startTime}-${e1.endTime} vs ${e2.startTime}-${e2.endTime}",
                                    conflictingEntries = listOf(e1, e2)
                                )
                            )
                        }
                    }
                }
            }
        }

        return conflicts
    }

    private fun groupByLecturer(schedules: List<ScheduleEntry>): List<LecturerScheduleGroup> {
        return schedules
            .groupBy { it.lecturerId }
            .map { (lecturerId, lecturerSchedules) ->
                LecturerScheduleGroup(
                    lecturerId = lecturerId,
                    lecturerName = lecturerSchedules.firstOrNull()?.lecturerName ?: "Unknown",
                    schedules = lecturerSchedules.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                )
            }
            .sortedBy { it.lecturerName }
    }

    private fun hasTimeOverlap(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = timeToMinutes(start1)
        val e1 = timeToMinutes(end1)
        val s2 = timeToMinutes(start2)
        val e2 = timeToMinutes(end2)

        return s1 < e2 && s2 < e1
    }

    private fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        return if (parts.size == 2) {
            parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
        } else 0
    }
}

