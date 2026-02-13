package com.example.sitconnect.features.gradebook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sitconnect.features.gradebook.domain.model.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class GradebookState {
    object Idle : GradebookState()
    object Loading : GradebookState()
    data class Success(
        val modules: List<ModuleGrade>,
        val summary: GradeSummary
    ) : GradebookState()
    data class Error(val message: String) : GradebookState()
}

class GradebookViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _gradebookState = MutableStateFlow<GradebookState>(GradebookState.Idle)
    val gradebookState: StateFlow<GradebookState> = _gradebookState

    fun fetchGrades(studentId: String) {
        viewModelScope.launch {
            try {
                _gradebookState.value = GradebookState.Loading

                val gradesSnapshot = firestore.collection("student_grades")
                    .whereEqualTo("studentId", studentId)
                    .get()
                    .await()

                val modules = gradesSnapshot.documents.mapNotNull { document ->
                    try {
                        val assessmentsList = (document.get("assessments") as? List<*>)?.mapNotNull { item ->
                            val map = item as? Map<*, *>
                            if (map != null) {
                                Assessment(
                                    id = map["id"] as? String ?: "",
                                    name = map["name"] as? String ?: "",
                                    weightage = (map["weightage"] as? Number)?.toFloat() ?: 0f,
                                    maxScore = (map["maxScore"] as? Number)?.toFloat() ?: 100f,
                                    score = (map["score"] as? Number)?.toFloat(),
                                    graded = map["graded"] as? Boolean ?: false
                                )
                            } else null
                        } ?: emptyList()

                        ModuleGrade(
                            moduleCode = document.getString("moduleCode") ?: "",
                            moduleName = document.getString("moduleName") ?: "",
                            credits = document.getLong("credits")?.toInt() ?: 5,
                            assessments = assessmentsList,
                            currentGrade = document.getString("currentGrade"),
                            finalGrade = document.getString("finalGrade")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                val finalModules = if (modules.isEmpty()) getSampleGrades() else modules
                val summary = calculateSummary(finalModules)

                _gradebookState.value = GradebookState.Success(
                    modules = finalModules,
                    summary = summary
                )
            } catch (e: Exception) {
                val sampleModules = getSampleGrades()
                _gradebookState.value = GradebookState.Success(
                    modules = sampleModules,
                    summary = calculateSummary(sampleModules)
                )
            }
        }
    }

    private fun calculateSummary(modules: List<ModuleGrade>): GradeSummary {
        var totalGradePoints = 0f
        var totalCredits = 0

        modules.forEach { module ->
            val grade = module.currentGrade ?: module.finalGrade
            if (grade != null) {
                totalGradePoints += calculateGradePoint(grade) * module.credits
                totalCredits += module.credits
            }
        }

        val currentGPA = if (totalCredits > 0) totalGradePoints / totalCredits else 0f

        return GradeSummary(
            currentGPA = currentGPA,
            cumulativeGPA = currentGPA, // Simplified - would need historical data
            totalCreditsEarned = totalCredits,
            totalCreditsAttempted = modules.sumOf { it.credits },
            currentTrimesterCredits = modules.sumOf { it.credits }
        )
    }

    private fun getSampleGrades(): List<ModuleGrade> {
        return listOf(
            ModuleGrade(
                moduleCode = "ICT2207",
                moduleName = "Mobile Security",
                credits = 5,
                assessments = listOf(
                    Assessment("1", "Quiz 1", 10f, 100f, 85f, true),
                    Assessment("2", "Quiz 2", 10f, 100f, 78f, true),
                    Assessment("3", "Assignment 1", 20f, 100f, 88f, true),
                    Assessment("4", "Assignment 2", 20f, 100f, null, false),
                    Assessment("5", "Final Exam", 40f, 100f, null, false)
                ),
                currentGrade = "B+",
                finalGrade = null
            ),
            ModuleGrade(
                moduleCode = "ICT2205",
                moduleName = "Web Security",
                credits = 5,
                assessments = listOf(
                    Assessment("1", "Lab Assessment 1", 15f, 100f, 90f, true),
                    Assessment("2", "Lab Assessment 2", 15f, 100f, 85f, true),
                    Assessment("3", "Project", 30f, 100f, 82f, true),
                    Assessment("4", "Final Exam", 40f, 100f, null, false)
                ),
                currentGrade = "A-",
                finalGrade = null
            ),
            ModuleGrade(
                moduleCode = "ICT2104",
                moduleName = "Software Engineering",
                credits = 5,
                assessments = listOf(
                    Assessment("1", "Quiz", 10f, 100f, 75f, true),
                    Assessment("2", "Group Project Phase 1", 20f, 100f, 80f, true),
                    Assessment("3", "Group Project Phase 2", 30f, 100f, 78f, true),
                    Assessment("4", "Final Exam", 40f, 100f, null, false)
                ),
                currentGrade = "B",
                finalGrade = null
            ),
            ModuleGrade(
                moduleCode = "ICT2101",
                moduleName = "Introduction to Data Science",
                credits = 5,
                assessments = listOf(
                    Assessment("1", "Assignment 1", 20f, 100f, 92f, true),
                    Assessment("2", "Assignment 2", 20f, 100f, 88f, true),
                    Assessment("3", "Midterm", 20f, 100f, 85f, true),
                    Assessment("4", "Final Project", 40f, 100f, null, false)
                ),
                currentGrade = "A",
                finalGrade = null
            )
        )
    }
}

