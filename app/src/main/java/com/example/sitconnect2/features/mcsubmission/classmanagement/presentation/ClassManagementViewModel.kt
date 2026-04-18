package com.example.sitconnect2.features.classmanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect2.features.classmanagement.domain.model.EnrolledStudent
import com.example.sitconnect2.features.classmanagement.domain.model.Module
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

sealed class ClassManagementState {
    object Idle : ClassManagementState()
    object Loading : ClassManagementState()
    data class Success(
        val modules: List<Module>,
        val selectedModule: Module? = null,
        val students: List<EnrolledStudent> = emptyList(),
        val availableStudents: List<EnrolledStudent> = emptyList()
    ) : ClassManagementState()
    data class Error(val message: String) : ClassManagementState()
}

class ClassManagementViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _classState = MutableStateFlow<ClassManagementState>(ClassManagementState.Idle)
    val classState: StateFlow<ClassManagementState> = _classState

    private var currentLecturerId: String = ""

    fun fetchLecturerModules(lecturerId: String) {
        currentLecturerId = lecturerId
        viewModelScope.launch {
            try {
                _classState.value = ClassManagementState.Loading

                withTimeout(15000L) {
                    // Fetch modules where this lecturer is assigned
                    val modulesSnapshot = firestore.collection("modules")
                        .whereEqualTo("lecturerId", lecturerId)
                        .get()
                        .await()

                    val modules = modulesSnapshot.documents.mapNotNull { document ->
                        try {
                            Module(
                                id = document.id,
                                code = document.getString("code") ?: "",
                                name = document.getString("name") ?: "",
                                description = document.getString("description") ?: "",
                                trimester = document.getString("trimester") ?: "",
                                lecturerId = document.getString("lecturerId") ?: "",
                                lecturerName = document.getString("lecturerName") ?: "",
                                enrolledStudents = (document.get("enrolledStudents") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                    _classState.value = ClassManagementState.Success(modules = modules)
                }
            } catch (e: TimeoutCancellationException) {
                _classState.value = ClassManagementState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _classState.value = ClassManagementState.Error(e.message ?: "Failed to fetch modules")
            }
        }
    }

    fun selectModule(module: Module) {
        viewModelScope.launch {
            try {
                val currentState = _classState.value
                if (currentState is ClassManagementState.Success) {
                    _classState.value = currentState.copy(selectedModule = module, students = emptyList())
                }

                // Fetch students from the enrolledStudents array in the module
                withTimeout(15000L) {
                    if (module.enrolledStudents.isEmpty()) {
                        val state = _classState.value
                        if (state is ClassManagementState.Success) {
                            _classState.value = state.copy(students = emptyList())
                        }
                        return@withTimeout
                    }

                    // Fetch user details for each enrolled student
                    val students = mutableListOf<EnrolledStudent>()
                    for (studentUid in module.enrolledStudents) {
                        try {
                            val userDoc = firestore.collection("users")
                                .document(studentUid)
                                .get()
                                .await()

                            if (userDoc.exists()) {
                                students.add(
                                    EnrolledStudent(
                                        id = userDoc.id,
                                        uid = studentUid,
                                        name = userDoc.getString("name") ?: "",
                                        email = userDoc.getString("email") ?: "",
                                        studentId = userDoc.getString("studentId") ?: "",
                                        moduleId = module.id
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            // Skip this student if fetch fails
                        }
                    }

                    val state = _classState.value
                    if (state is ClassManagementState.Success) {
                        _classState.value = state.copy(students = students)
                    }
                }
            } catch (e: Exception) {
                // Keep current state, just log error
            }
        }
    }

    fun clearSelectedModule() {
        val currentState = _classState.value
        if (currentState is ClassManagementState.Success) {
            _classState.value = currentState.copy(selectedModule = null, students = emptyList(), availableStudents = emptyList())
        }
    }

    fun fetchAvailableStudents(moduleId: String, enrolledStudentIds: List<String>) {
        viewModelScope.launch {
            try {
                // Fetch all students who are not enrolled in this module
                val usersSnapshot = firestore.collection("users").get().await()
                val availableStudents = usersSnapshot.documents.mapNotNull { doc ->
                    val rolesMap = doc.get("roles") as? Map<*, *>
                    val isStudent = rolesMap?.get("student") as? Boolean ?: false

                    if (isStudent && !enrolledStudentIds.contains(doc.id)) {
                        EnrolledStudent(
                            id = doc.id,
                            uid = doc.id,
                            name = doc.getString("name") ?: "",
                            email = doc.getString("email") ?: "",
                            studentId = doc.getString("studentId") ?: "",
                            moduleId = moduleId
                        )
                    } else null
                }

                val state = _classState.value
                if (state is ClassManagementState.Success) {
                    _classState.value = state.copy(availableStudents = availableStudents)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun enrollStudent(moduleId: String, studentUid: String) {
        viewModelScope.launch {
            try {
                // Update module's enrolledStudents array
                firestore.collection("modules")
                    .document(moduleId)
                    .update("enrolledStudents", com.google.firebase.firestore.FieldValue.arrayUnion(studentUid))
                    .await()

                // Also update all schedules for this module
                val schedulesSnapshot = firestore.collection("schedules")
                    .whereEqualTo("moduleId", moduleId)
                    .get()
                    .await()

                for (scheduleDoc in schedulesSnapshot.documents) {
                    firestore.collection("schedules")
                        .document(scheduleDoc.id)
                        .update("enrolledStudents", com.google.firebase.firestore.FieldValue.arrayUnion(studentUid))
                        .await()
                }

                // Refresh modules
                fetchLecturerModules(currentLecturerId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun unenrollStudent(moduleId: String, studentUid: String) {
        viewModelScope.launch {
            try {
                // Update module's enrolledStudents array
                firestore.collection("modules")
                    .document(moduleId)
                    .update("enrolledStudents", com.google.firebase.firestore.FieldValue.arrayRemove(studentUid))
                    .await()

                // Also update all schedules for this module
                val schedulesSnapshot = firestore.collection("schedules")
                    .whereEqualTo("moduleId", moduleId)
                    .get()
                    .await()

                for (scheduleDoc in schedulesSnapshot.documents) {
                    firestore.collection("schedules")
                        .document(scheduleDoc.id)
                        .update("enrolledStudents", com.google.firebase.firestore.FieldValue.arrayRemove(studentUid))
                        .await()
                }

                // Refresh modules
                fetchLecturerModules(currentLecturerId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
