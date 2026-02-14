package com.example.sitconnect.features.admin.modulemanagement.domain.model

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

