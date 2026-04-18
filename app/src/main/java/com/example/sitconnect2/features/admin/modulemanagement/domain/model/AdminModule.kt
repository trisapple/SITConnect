package com.example.sitconnect2.features.admin.modulemanagement.domain.model

data class AdminModule(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val trimester: String = "",
    val lecturerId: String = "",
    val lecturerName: String = "",
    val enrolledStudents: List<String> = emptyList()
)

data class LecturerInfo(
    val id: String = "",
    val name: String = "",
    val email: String = ""
)

data class StudentInfo(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val studentId: String = ""
)

enum class ScheduleClassType {
    LECTURE,
    TUTORIAL,
    LAB
}

data class ModuleSchedule(
    val id: String = "",
    val moduleId: String = "",
    val moduleCode: String = "",
    val moduleName: String = "",
    val classType: ScheduleClassType = ScheduleClassType.LECTURE,
    val dayOfWeek: Int = 1, // 1 = Monday, 5 = Friday
    val startTime: String = "",
    val endTime: String = "",
    val venue: String = "",
    val lecturerId: String = "",
    val lecturerName: String = "",
    val enrolledStudents: List<String> = emptyList()
)

