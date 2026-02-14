package com.example.sitconnect.features.admin.modulemanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.admin.modulemanagement.domain.model.AdminModule
import com.example.sitconnect.features.admin.modulemanagement.domain.model.LecturerInfo
import com.example.sitconnect.features.admin.modulemanagement.domain.model.ModuleSchedule
import com.example.sitconnect.features.admin.modulemanagement.domain.model.ScheduleClassType
import com.example.sitconnect.features.admin.modulemanagement.domain.model.StudentInfo
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

sealed class ModuleManagementState {
    object Idle : ModuleManagementState()
    object Loading : ModuleManagementState()
    data class Success(
        val modules: List<AdminModule> = emptyList(),
        val lecturers: List<LecturerInfo> = emptyList(),
        val allStudents: List<StudentInfo> = emptyList(),
        val allSchedules: List<ModuleSchedule> = emptyList(),
        val message: String? = null
    ) : ModuleManagementState()
    data class Error(val message: String) : ModuleManagementState()
}

class ModuleManagementViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ModuleManagementState>(ModuleManagementState.Idle)
    val state: StateFlow<ModuleManagementState> = _state

    private var cachedLecturers: List<LecturerInfo> = emptyList()
    private var cachedStudents: List<StudentInfo> = emptyList()
    private var cachedSchedules: List<ModuleSchedule> = emptyList()

    fun fetchAllModules() {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(15000L) {
                    // Fetch modules, users, and schedules
                    val modulesDeferred = firestore.collection("modules").get()
                    val usersDeferred = firestore.collection("users").get()
                    val schedulesDeferred = firestore.collection("schedules").get()

                    val modulesSnapshot = modulesDeferred.await()
                    val usersSnapshot = usersDeferred.await()
                    val schedulesSnapshot = schedulesDeferred.await()

                    val modules = modulesSnapshot.documents.mapNotNull { doc ->
                        try {
                            AdminModule(
                                id = doc.id,
                                code = doc.getString("code") ?: "",
                                name = doc.getString("name") ?: "",
                                description = doc.getString("description") ?: "",
                                trimester = doc.getString("trimester") ?: "",
                                lecturerId = doc.getString("lecturerId") ?: "",
                                lecturerName = doc.getString("lecturerName") ?: "",
                                enrolledStudents = (doc.get("enrolledStudents") as? List<*>)
                                    ?.mapNotNull { it as? String } ?: emptyList()
                            )
                        } catch (e: Exception) { null }
                    }.sortedBy { it.code }

                    // Extract lecturers and students from users
                    cachedLecturers = usersSnapshot.documents.mapNotNull { doc ->
                        val roles = doc.get("roles") as? Map<*, *>
                        if (roles?.get("lecturer") == true) {
                            LecturerInfo(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                email = doc.getString("email") ?: ""
                            )
                        } else null
                    }.sortedBy { it.name }

                    cachedStudents = usersSnapshot.documents.mapNotNull { doc ->
                        val roles = doc.get("roles") as? Map<*, *>
                        if (roles?.get("student") == true) {
                            StudentInfo(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                email = doc.getString("email") ?: "",
                                studentId = doc.getString("studentId") ?: ""
                            )
                        } else null
                    }.sortedBy { it.name }

                    // Parse schedules
                    cachedSchedules = schedulesSnapshot.documents.mapNotNull { doc ->
                        try {
                            ModuleSchedule(
                                id = doc.id,
                                moduleId = doc.getString("moduleId") ?: "",
                                moduleCode = doc.getString("moduleCode") ?: "",
                                moduleName = doc.getString("moduleName") ?: "",
                                classType = try {
                                    ScheduleClassType.valueOf(doc.getString("classType") ?: "LECTURE")
                                } catch (e: Exception) { ScheduleClassType.LECTURE },
                                dayOfWeek = doc.getLong("dayOfWeek")?.toInt() ?: 1,
                                startTime = doc.getString("startTime") ?: "",
                                endTime = doc.getString("endTime") ?: "",
                                venue = doc.getString("venue") ?: "",
                                lecturerId = doc.getString("lecturerId") ?: "",
                                lecturerName = doc.getString("lecturerName") ?: "",
                                enrolledStudents = (doc.get("enrolledStudents") as? List<*>)
                                    ?.mapNotNull { it as? String } ?: emptyList()
                            )
                        } catch (e: Exception) { null }
                    }

                    _state.value = ModuleManagementState.Success(
                        modules = modules,
                        lecturers = cachedLecturers,
                        allStudents = cachedStudents,
                        allSchedules = cachedSchedules
                    )
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to fetch modules")
            }
        }
    }

    fun createModule(
        code: String,
        name: String,
        description: String,
        trimester: String,
        lecturerId: String,
        lecturerName: String
    ) {
        viewModelScope.launch {
            try {
                val currentState = _state.value as? ModuleManagementState.Success
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    val moduleData = hashMapOf(
                        "code" to code,
                        "name" to name,
                        "description" to description,
                        "trimester" to trimester,
                        "lecturerId" to lecturerId,
                        "lecturerName" to lecturerName,
                        "enrolledStudents" to emptyList<String>()
                    )
                    firestore.collection("modules").add(moduleData).await()

                    // Refresh modules list
                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to create module")
            }
        }
    }

    fun updateModule(
        moduleId: String,
        code: String,
        name: String,
        description: String,
        trimester: String,
        lecturerId: String,
        lecturerName: String
    ) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    val updates = mapOf(
                        "code" to code,
                        "name" to name,
                        "description" to description,
                        "trimester" to trimester,
                        "lecturerId" to lecturerId,
                        "lecturerName" to lecturerName
                    )
                    firestore.collection("modules").document(moduleId).update(updates).await()

                    // Also update schedules that reference this module
                    val schedulesSnapshot = firestore.collection("schedules")
                        .whereEqualTo("moduleId", moduleId)
                        .get()
                        .await()

                    schedulesSnapshot.documents.forEach { doc ->
                        firestore.collection("schedules").document(doc.id).update(
                            mapOf(
                                "moduleCode" to code,
                                "moduleName" to name,
                                "lecturerId" to lecturerId,
                                "lecturerName" to lecturerName
                            )
                        ).await()
                    }

                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to update module")
            }
        }
    }

    fun deleteModule(moduleId: String) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    // Delete associated schedules first
                    val schedulesSnapshot = firestore.collection("schedules")
                        .whereEqualTo("moduleId", moduleId)
                        .get()
                        .await()

                    schedulesSnapshot.documents.forEach { doc ->
                        firestore.collection("schedules").document(doc.id).delete().await()
                    }

                    // Delete the module
                    firestore.collection("modules").document(moduleId).delete().await()

                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to delete module")
            }
        }
    }

    fun assignLecturer(moduleId: String, lecturerId: String, lecturerName: String) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    firestore.collection("modules").document(moduleId).update(
                        mapOf(
                            "lecturerId" to lecturerId,
                            "lecturerName" to lecturerName
                        )
                    ).await()

                    // Update schedules as well
                    val schedulesSnapshot = firestore.collection("schedules")
                        .whereEqualTo("moduleId", moduleId)
                        .get()
                        .await()

                    schedulesSnapshot.documents.forEach { doc ->
                        firestore.collection("schedules").document(doc.id).update(
                            mapOf(
                                "lecturerId" to lecturerId,
                                "lecturerName" to lecturerName
                            )
                        ).await()
                    }

                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to assign lecturer")
            }
        }
    }

    fun enrollStudent(moduleId: String, studentId: String) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    // Add student to module's enrolledStudents array
                    firestore.collection("modules").document(moduleId)
                        .update("enrolledStudents", FieldValue.arrayUnion(studentId))
                        .await()

                    // Also update schedules to include this student
                    val schedulesSnapshot = firestore.collection("schedules")
                        .whereEqualTo("moduleId", moduleId)
                        .get()
                        .await()

                    schedulesSnapshot.documents.forEach { doc ->
                        firestore.collection("schedules").document(doc.id)
                            .update("enrolledStudents", FieldValue.arrayUnion(studentId))
                            .await()
                    }

                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to enroll student")
            }
        }
    }

    fun unenrollStudent(moduleId: String, studentId: String) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    // Remove student from module's enrolledStudents array
                    firestore.collection("modules").document(moduleId)
                        .update("enrolledStudents", FieldValue.arrayRemove(studentId))
                        .await()

                    // Also update schedules to remove this student
                    val schedulesSnapshot = firestore.collection("schedules")
                        .whereEqualTo("moduleId", moduleId)
                        .get()
                        .await()

                    schedulesSnapshot.documents.forEach { doc ->
                        firestore.collection("schedules").document(doc.id)
                            .update("enrolledStudents", FieldValue.arrayRemove(studentId))
                            .await()
                    }

                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to unenroll student")
            }
        }
    }

    // Schedule Management Functions

    fun getSchedulesForModule(moduleId: String): List<ModuleSchedule> {
        return cachedSchedules.filter { it.moduleId == moduleId }
    }

    /**
     * Check for scheduling conflicts
     * Returns list of conflicting schedules if any exist
     */
    fun checkForConflicts(
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        venue: String,
        lecturerId: String,
        excludeScheduleId: String? = null
    ): List<ModuleSchedule> {
        val conflicts = mutableListOf<ModuleSchedule>()

        for (schedule in cachedSchedules) {
            // Skip if this is the schedule being edited
            if (excludeScheduleId != null && schedule.id == excludeScheduleId) continue

            // Skip if different day
            if (schedule.dayOfWeek != dayOfWeek) continue

            // Check time overlap
            if (hasTimeOverlap(startTime, endTime, schedule.startTime, schedule.endTime)) {
                // Venue conflict
                if (schedule.venue.equals(venue, ignoreCase = true)) {
                    conflicts.add(schedule)
                }
                // Lecturer conflict
                else if (schedule.lecturerId == lecturerId) {
                    conflicts.add(schedule)
                }
            }
        }

        return conflicts
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
            (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
        } else 0
    }

    /**
     * Get available time slots for a specific day, venue, and lecturer
     * Filters out conflicting times
     */
    fun getOccupiedSlots(dayOfWeek: Int, venue: String?, lecturerId: String?): List<Pair<String, String>> {
        return cachedSchedules
            .filter { schedule ->
                schedule.dayOfWeek == dayOfWeek &&
                (venue?.let { schedule.venue.equals(it, ignoreCase = true) } ?: false ||
                 lecturerId?.let { schedule.lecturerId == it } ?: false)
            }
            .map { Pair(it.startTime, it.endTime) }
    }

    fun createSchedule(
        module: AdminModule,
        classType: ScheduleClassType,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        venue: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Check for conflicts first
                val conflicts = checkForConflicts(
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue,
                    lecturerId = module.lecturerId
                )

                if (conflicts.isNotEmpty()) {
                    val conflictInfo = conflicts.joinToString("\n") {
                        "• ${it.moduleCode} (${it.startTime}-${it.endTime}) - ${if (it.venue.equals(venue, ignoreCase = true)) "Same venue" else "Same lecturer"}"
                    }
                    onError("Schedule conflicts detected:\n$conflictInfo")
                    return@launch
                }

                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    val scheduleData = hashMapOf(
                        "moduleId" to module.id,
                        "moduleCode" to module.code,
                        "moduleName" to module.name,
                        "classType" to classType.name,
                        "dayOfWeek" to dayOfWeek,
                        "startTime" to startTime,
                        "endTime" to endTime,
                        "venue" to venue,
                        "lecturerId" to module.lecturerId,
                        "lecturerName" to module.lecturerName,
                        "enrolledStudents" to module.enrolledStudents,
                        "latitude" to 1.4136, // SIT Punggol default
                        "longitude" to 103.9123,
                        "radiusMeters" to 500,
                        "attendanceCode" to ""
                    )

                    firestore.collection("schedules").add(scheduleData).await()
                    fetchAllModules()
                    onSuccess()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to create schedule")
                onError(e.message ?: "Failed to create schedule")
            }
        }
    }

    fun updateSchedule(
        scheduleId: String,
        module: AdminModule,
        classType: ScheduleClassType,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        venue: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                // Check for conflicts (excluding current schedule)
                val conflicts = checkForConflicts(
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime,
                    venue = venue,
                    lecturerId = module.lecturerId,
                    excludeScheduleId = scheduleId
                )

                if (conflicts.isNotEmpty()) {
                    val conflictInfo = conflicts.joinToString("\n") {
                        "• ${it.moduleCode} (${it.startTime}-${it.endTime}) - ${if (it.venue.equals(venue, ignoreCase = true)) "Same venue" else "Same lecturer"}"
                    }
                    onError("Schedule conflicts detected:\n$conflictInfo")
                    return@launch
                }

                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    val updates = mapOf(
                        "classType" to classType.name,
                        "dayOfWeek" to dayOfWeek,
                        "startTime" to startTime,
                        "endTime" to endTime,
                        "venue" to venue
                    )

                    firestore.collection("schedules").document(scheduleId)
                        .update(updates)
                        .await()

                    fetchAllModules()
                    onSuccess()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to update schedule")
                onError(e.message ?: "Failed to update schedule")
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(10000L) {
                    firestore.collection("schedules").document(scheduleId).delete().await()
                    fetchAllModules()
                }
            } catch (e: Exception) {
                _state.value = ModuleManagementState.Error(e.message ?: "Failed to delete schedule")
            }
        }
    }
}
