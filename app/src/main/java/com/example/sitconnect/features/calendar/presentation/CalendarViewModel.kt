package com.example.sitconnect.features.calendar.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.calendar.domain.model.CalendarEvent
import com.example.sitconnect.features.calendar.domain.model.EventType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import java.util.*

sealed class CalendarState {
    object Idle : CalendarState()
    object Loading : CalendarState()
    data class Success(val events: List<CalendarEvent>) : CalendarState()
    data class Error(val message: String) : CalendarState()
}

sealed class CalendarOperationState {
    object Idle : CalendarOperationState()
    object Loading : CalendarOperationState()
    object Success : CalendarOperationState()
    data class Error(val message: String) : CalendarOperationState()
}

class CalendarViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Idle)
    val calendarState: StateFlow<CalendarState> = _calendarState

    private val _operationState = MutableStateFlow<CalendarOperationState>(CalendarOperationState.Idle)
    val operationState: StateFlow<CalendarOperationState> = _operationState

    private val _selectedTrimester = MutableStateFlow(getCurrentTrimester())
    val selectedTrimester: StateFlow<Int> = _selectedTrimester

    private val _selectedEventType = MutableStateFlow<EventType?>(null)
    val selectedEventType: StateFlow<EventType?> = _selectedEventType

    init {
        fetchCalendarEvents()
    }

    // Determine current trimester based on current date
    private fun getCurrentTrimester(): Int {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) // 0-indexed
        return when (month) {
            in 0..3 -> 1   // Jan-Apr: Trimester 1
            in 4..7 -> 2   // May-Aug: Trimester 2
            else -> 3      // Sep-Dec: Trimester 3
        }
    }

    fun setSelectedTrimester(trimester: Int) {
        _selectedTrimester.value = trimester
        fetchCalendarEvents()
    }

    fun setSelectedEventType(eventType: EventType?) {
        _selectedEventType.value = eventType
    }

    fun fetchCalendarEvents() {
        viewModelScope.launch {
            try {
                _calendarState.value = CalendarState.Loading

                withTimeout(15000L) { // 15 second timeout
                    val querySnapshot = firestore.collection("calendar_events")
                        .whereEqualTo("trimester", _selectedTrimester.value)
                        .get()
                        .await()

                    val events = querySnapshot.documents.mapNotNull { document ->
                        try {
                            CalendarEvent(
                                id = document.id,
                                title = document.getString("title") ?: "",
                                description = document.getString("description") ?: "",
                                startDate = document.getTimestamp("startDate")?.toDate() ?: Date(),
                                endDate = document.getTimestamp("endDate")?.toDate() ?: Date(),
                                eventType = EventType.valueOf(document.getString("eventType") ?: "LECTURE_START"),
                                trimester = document.getLong("trimester")?.toInt() ?: 1
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }.sortedBy { it.startDate }

                    _calendarState.value = CalendarState.Success(events)
                }
            } catch (e: TimeoutCancellationException) {
                _calendarState.value = CalendarState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _calendarState.value = CalendarState.Error(e.message ?: "Failed to fetch calendar events")
            }
        }
    }

    fun createCalendarEvent(
        title: String,
        description: String,
        startDate: Date,
        endDate: Date,
        eventType: EventType,
        trimester: Int
    ) {
        viewModelScope.launch {
            try {
                _operationState.value = CalendarOperationState.Loading

                withTimeout(10000L) {
                    val eventData = hashMapOf(
                        "title" to title,
                        "description" to description,
                        "startDate" to com.google.firebase.Timestamp(startDate),
                        "endDate" to com.google.firebase.Timestamp(endDate),
                        "eventType" to eventType.name,
                        "trimester" to trimester
                    )

                    firestore.collection("calendar_events")
                        .add(eventData)
                        .await()

                    _operationState.value = CalendarOperationState.Success
                    fetchCalendarEvents()
                }
            } catch (e: TimeoutCancellationException) {
                _operationState.value = CalendarOperationState.Error("Request timed out.")
            } catch (e: Exception) {
                _operationState.value = CalendarOperationState.Error(e.message ?: "Failed to create event")
            }
        }
    }

    fun updateCalendarEvent(
        eventId: String,
        title: String,
        description: String,
        startDate: Date,
        endDate: Date,
        eventType: EventType,
        trimester: Int
    ) {
        viewModelScope.launch {
            try {
                _operationState.value = CalendarOperationState.Loading

                withTimeout(10000L) {
                    val updates = mapOf(
                        "title" to title,
                        "description" to description,
                        "startDate" to com.google.firebase.Timestamp(startDate),
                        "endDate" to com.google.firebase.Timestamp(endDate),
                        "eventType" to eventType.name,
                        "trimester" to trimester
                    )

                    firestore.collection("calendar_events")
                        .document(eventId)
                        .update(updates)
                        .await()

                    _operationState.value = CalendarOperationState.Success
                    fetchCalendarEvents()
                }
            } catch (e: TimeoutCancellationException) {
                _operationState.value = CalendarOperationState.Error("Request timed out.")
            } catch (e: Exception) {
                _operationState.value = CalendarOperationState.Error(e.message ?: "Failed to update event")
            }
        }
    }

    fun deleteCalendarEvent(eventId: String) {
        viewModelScope.launch {
            try {
                _operationState.value = CalendarOperationState.Loading

                withTimeout(10000L) {
                    firestore.collection("calendar_events")
                        .document(eventId)
                        .delete()
                        .await()

                    _operationState.value = CalendarOperationState.Success
                    fetchCalendarEvents()
                }
            } catch (e: TimeoutCancellationException) {
                _operationState.value = CalendarOperationState.Error("Request timed out.")
            } catch (e: Exception) {
                _operationState.value = CalendarOperationState.Error(e.message ?: "Failed to delete event")
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = CalendarOperationState.Idle
    }
}

fun Context.LPG(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val coarse = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    return fine && coarse && background
}