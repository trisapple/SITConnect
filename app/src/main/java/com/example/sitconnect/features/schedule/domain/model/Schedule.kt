package com.example.sitconnect.features.schedule.domain.model

import java.util.Date

enum class ClassType {
    LECTURE,
    TUTORIAL,
    LAB
}

data class ScheduleEntry(
    val id: String = "",
    val moduleId: String = "",
    val moduleCode: String = "",
    val moduleName: String = "",
    val classType: ClassType = ClassType.LECTURE,
    val dayOfWeek: Int = 1, // 1 = Monday, 7 = Sunday
    val startTime: String = "",
    val endTime: String = "",
    val venue: String = "",
    val lecturerId: String = ""
)

