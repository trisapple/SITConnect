package com.example.sitconnect.features.classmanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.classmanagement.domain.model.EnrolledStudent
import com.example.sitconnect.features.classmanagement.domain.model.Module
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
        val students: List<EnrolledStudent> = emptyList()
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
                                lecturerId = document.getString("lecturerId") ?: "",
                                lecturerName = document.getString("lecturerName") ?: ""
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

                // Fetch students enrolled in this module
                withTimeout(15000L) {
                    val enrollmentsSnapshot = firestore.collection("module_enrollments")
                        .whereEqualTo("moduleId", module.id)
                        .get()
                        .await()

                    val students = enrollmentsSnapshot.documents.mapNotNull { document ->
                        try {
                            EnrolledStudent(
                                id = document.id,
                                uid = document.getString("studentUid") ?: "",
                                name = document.getString("studentName") ?: "",
                                email = document.getString("studentEmail") ?: "",
                                studentId = document.getString("studentId") ?: "",
                                moduleId = document.getString("moduleId") ?: ""
                            )
                        } catch (e: Exception) {
                            null
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
            _classState.value = currentState.copy(selectedModule = null, students = emptyList())
        }
    }
}

