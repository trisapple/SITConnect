package com.example.sitconnect.features.gradebook.domain.model

data class ModuleGrade(
    val moduleCode: String = "",
    val moduleName: String = "",
    val credits: Int = 5,
    val assessments: List<Assessment> = emptyList(),
    val currentGrade: String? = null,
    val finalGrade: String? = null
)

data class Assessment(
    val id: String = "",
    val name: String = "",
    val weightage: Float = 0f,
    val maxScore: Float = 100f,
    val score: Float? = null,
    val graded: Boolean = false
)

data class GradeSummary(
    val currentGPA: Float = 0f,
    val cumulativeGPA: Float = 0f,
    val totalCreditsEarned: Int = 0,
    val totalCreditsAttempted: Int = 0,
    val currentTrimesterCredits: Int = 0
)

fun calculateGradePoint(grade: String): Float {
    return when (grade.uppercase()) {
        "A+" -> 5.0f
        "A" -> 5.0f
        "A-" -> 4.5f
        "B+" -> 4.0f
        "B" -> 3.5f
        "B-" -> 3.0f
        "C+" -> 2.5f
        "C" -> 2.0f
        "D+" -> 1.5f
        "D" -> 1.0f
        "F" -> 0.0f
        else -> 0.0f
    }
}

fun percentageToGrade(percentage: Float): String {
    return when {
        percentage >= 85 -> "A+"
        percentage >= 80 -> "A"
        percentage >= 75 -> "A-"
        percentage >= 70 -> "B+"
        percentage >= 65 -> "B"
        percentage >= 60 -> "B-"
        percentage >= 55 -> "C+"
        percentage >= 50 -> "C"
        percentage >= 45 -> "D+"
        percentage >= 40 -> "D"
        else -> "F"
    }
}

