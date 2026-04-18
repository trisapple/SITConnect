package com.example.sitconnect2.features.schedule.domain.model

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
    val lecturerId: String = "",
    val lecturerName: String = "",
    val enrolledStudents: List<String> = emptyList(), // List of student UIDs
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 100,
    val attendanceCode: String = "" // Code for attendance verification
)

