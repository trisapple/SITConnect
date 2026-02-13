package com.example.sitconnect.features.calendar.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.calendar.domain.model.CalendarEvent
import com.example.sitconnect.features.calendar.domain.model.EventType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

sealed class CalendarState {
    object Idle : CalendarState()
    object Loading : CalendarState()
    data class Success(val events: List<CalendarEvent>) : CalendarState()
    data class Error(val message: String) : CalendarState()
}

class CalendarViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Idle)
    val calendarState: StateFlow<CalendarState> = _calendarState

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

                _calendarState.value = if (events.isEmpty()) {
                    CalendarState.Success(getSampleEvents())
                } else {
                    CalendarState.Success(events)
                }
            } catch (e: Exception) {
                _calendarState.value = CalendarState.Success(getSampleEvents())
            }
        }
    }

    private fun getSampleEvents(): List<CalendarEvent> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val year = Calendar.getInstance().get(Calendar.YEAR)

        return listOf(
            CalendarEvent(
                id = "1",
                title = "Trimester 1 Begins",
                description = "Start of academic trimester",
                startDate = dateFormat.parse("$year-01-13") ?: Date(),
                endDate = dateFormat.parse("$year-01-13") ?: Date(),
                eventType = EventType.LECTURE_START,
                trimester = 1
            ),
            CalendarEvent(
                id = "2",
                title = "Chinese New Year",
                description = "Public Holiday - No classes",
                startDate = dateFormat.parse("$year-01-29") ?: Date(),
                endDate = dateFormat.parse("$year-01-30") ?: Date(),
                eventType = EventType.HOLIDAY,
                trimester = 1
            ),
            CalendarEvent(
                id = "3",
                title = "Recess Week",
                description = "Mid-trimester break",
                startDate = dateFormat.parse("$year-02-24") ?: Date(),
                endDate = dateFormat.parse("$year-02-28") ?: Date(),
                eventType = EventType.RECESS,
                trimester = 1
            ),
            CalendarEvent(
                id = "4",
                title = "Good Friday",
                description = "Public Holiday",
                startDate = dateFormat.parse("$year-03-29") ?: Date(),
                endDate = dateFormat.parse("$year-03-29") ?: Date(),
                eventType = EventType.HOLIDAY,
                trimester = 1
            ),
            CalendarEvent(
                id = "5",
                title = "Final Examinations",
                description = "End of trimester examinations",
                startDate = dateFormat.parse("$year-04-14") ?: Date(),
                endDate = dateFormat.parse("$year-04-25") ?: Date(),
                eventType = EventType.EXAM,
                trimester = 1
            ),
            CalendarEvent(
                id = "6",
                title = "Trimester 1 Ends",
                description = "End of academic trimester",
                startDate = dateFormat.parse("$year-04-25") ?: Date(),
                endDate = dateFormat.parse("$year-04-25") ?: Date(),
                eventType = EventType.LECTURE_END,
                trimester = 1
            ),
            CalendarEvent(
                id = "7",
                title = "Trimester 2 Begins",
                description = "Start of academic trimester",
                startDate = dateFormat.parse("$year-05-05") ?: Date(),
                endDate = dateFormat.parse("$year-05-05") ?: Date(),
                eventType = EventType.LECTURE_START,
                trimester = 2
            ),
            CalendarEvent(
                id = "8",
                title = "Recess Week",
                description = "Mid-trimester break",
                startDate = dateFormat.parse("$year-06-09") ?: Date(),
                endDate = dateFormat.parse("$year-06-13") ?: Date(),
                eventType = EventType.RECESS,
                trimester = 2
            ),
            CalendarEvent(
                id = "9",
                title = "National Day",
                description = "Public Holiday",
                startDate = dateFormat.parse("$year-08-09") ?: Date(),
                endDate = dateFormat.parse("$year-08-09") ?: Date(),
                eventType = EventType.HOLIDAY,
                trimester = 2
            ),
            CalendarEvent(
                id = "10",
                title = "Final Examinations",
                description = "End of trimester examinations",
                startDate = dateFormat.parse("$year-08-11") ?: Date(),
                endDate = dateFormat.parse("$year-08-22") ?: Date(),
                eventType = EventType.EXAM,
                trimester = 2
            ),
            CalendarEvent(
                id = "11",
                title = "Trimester 3 Begins",
                description = "Start of academic trimester",
                startDate = dateFormat.parse("$year-09-01") ?: Date(),
                endDate = dateFormat.parse("$year-09-01") ?: Date(),
                eventType = EventType.LECTURE_START,
                trimester = 3
            ),
            CalendarEvent(
                id = "12",
                title = "Deepavali",
                description = "Public Holiday",
                startDate = dateFormat.parse("$year-11-01") ?: Date(),
                endDate = dateFormat.parse("$year-11-01") ?: Date(),
                eventType = EventType.HOLIDAY,
                trimester = 3
            ),
            CalendarEvent(
                id = "13",
                title = "Recess Week",
                description = "Mid-trimester break",
                startDate = dateFormat.parse("$year-10-13") ?: Date(),
                endDate = dateFormat.parse("$year-10-17") ?: Date(),
                eventType = EventType.RECESS,
                trimester = 3
            ),
            CalendarEvent(
                id = "14",
                title = "Final Examinations",
                description = "End of trimester examinations",
                startDate = dateFormat.parse("$year-12-01") ?: Date(),
                endDate = dateFormat.parse("$year-12-12") ?: Date(),
                eventType = EventType.EXAM,
                trimester = 3
            ),
            CalendarEvent(
                id = "15",
                title = "Christmas Day",
                description = "Public Holiday",
                startDate = dateFormat.parse("$year-12-25") ?: Date(),
                endDate = dateFormat.parse("$year-12-25") ?: Date(),
                eventType = EventType.HOLIDAY,
                trimester = 3
            )
        ).filter { it.trimester == _selectedTrimester.value }
    }
}

