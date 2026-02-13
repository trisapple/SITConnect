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

sealed class ScheduleState {
    object Idle : ScheduleState()
    object Loading : ScheduleState()
    data class Success(val schedule: List<ScheduleEntry>) : ScheduleState()
    data class Error(val message: String) : ScheduleState()
}

class ScheduleViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _scheduleState = MutableStateFlow<ScheduleState>(ScheduleState.Idle)
    val scheduleState: StateFlow<ScheduleState> = _scheduleState

    fun fetchLecturerSchedule(lecturerId: String) {
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
                                lecturerId = document.getString("lecturerId") ?: ""
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
}

