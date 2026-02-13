package com.example.sitconnect.features.calendar.domain.model

import java.util.Date

enum class EventType {
    EXAM,
    RECESS,
    HOLIDAY,
    LECTURE_START,
    LECTURE_END
}

data class CalendarEvent(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(),
    val eventType: EventType = EventType.LECTURE_START,
    val trimester: Int = 1
)

