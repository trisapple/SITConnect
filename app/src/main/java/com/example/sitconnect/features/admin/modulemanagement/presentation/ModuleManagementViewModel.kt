package com.example.sitconnect.features.admin.modulemanagement.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.admin.modulemanagement.domain.model.AdminModule
import com.example.sitconnect.features.admin.modulemanagement.domain.model.LecturerInfo
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
        val message: String? = null
    ) : ModuleManagementState()
    data class Error(val message: String) : ModuleManagementState()
}

class ModuleManagementViewModel : ViewModel() {
    private val firestore = FirebaseFirestore.getInstance()

    private val _state = MutableStateFlow<ModuleManagementState>(ModuleManagementState.Idle)
    val state: StateFlow<ModuleManagementState> = _state

    private var cachedLecturers: List<LecturerInfo> = emptyList()

    fun fetchAllModules() {
        viewModelScope.launch {
            try {
                _state.value = ModuleManagementState.Loading

                withTimeout(15000L) {
                    // Fetch modules and lecturers in parallel
                    val modulesDeferred = firestore.collection("modules").get()
                    val lecturersDeferred = firestore.collection("users").get()

                    val modulesSnapshot = modulesDeferred.await()
                    val usersSnapshot = lecturersDeferred.await()

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

                    _state.value = ModuleManagementState.Success(
                        modules = modules,
                        lecturers = cachedLecturers
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
}

