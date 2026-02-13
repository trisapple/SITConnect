package com.example.sitconnect.features.classmanagement.domain.model

data class Module(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val lecturerId: String = "",
    val lecturerName: String = ""
)

data class EnrolledStudent(
    val id: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val studentId: String = "",
    val moduleId: String = ""
)

