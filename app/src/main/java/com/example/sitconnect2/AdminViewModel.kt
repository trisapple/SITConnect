package com.example.sitconnect2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserListItem(
    val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val roles: UserRoles? = null,
    val isOnboarded: Boolean = true
)

sealed class UsersListState {
    object Idle : UsersListState()
    object Loading : UsersListState()
    data class Success(val users: List<UserListItem>) : UsersListState()
    data class Error(val message: String) : UsersListState()
}

sealed class UserUpdateState {
    object Idle : UserUpdateState()
    object Loading : UserUpdateState()
    object Success : UserUpdateState()
    data class Error(val message: String) : UserUpdateState()
}

sealed class UserCreateState {
    object Idle : UserCreateState()
    object Loading : UserCreateState()
    object Success : UserCreateState()
    data class Error(val message: String) : UserCreateState()
}

sealed class SeedDataState {
    object Idle : SeedDataState()
    object Loading : SeedDataState()
    data class Success(val message: String) : SeedDataState()
    data class Error(val message: String) : SeedDataState()
}

sealed class PasswordResetState {
    object Idle : PasswordResetState()
    object Loading : PasswordResetState()
    object Success : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}

sealed class UserDeleteState {
    object Idle : UserDeleteState()
    object Loading : UserDeleteState()
    object Success : UserDeleteState()
    data class Error(val message: String) : UserDeleteState()
}

class AdminViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    private val _usersListState = MutableStateFlow<UsersListState>(UsersListState.Idle)
    val usersListState: StateFlow<UsersListState> = _usersListState

    private val _userUpdateState = MutableStateFlow<UserUpdateState>(UserUpdateState.Idle)
    val userUpdateState: StateFlow<UserUpdateState> = _userUpdateState

    private val _userCreateState = MutableStateFlow<UserCreateState>(UserCreateState.Idle)
    val userCreateState: StateFlow<UserCreateState> = _userCreateState

    private val _seedDataState = MutableStateFlow<SeedDataState>(SeedDataState.Idle)
    val seedDataState: StateFlow<SeedDataState> = _seedDataState

    private val _passwordResetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val passwordResetState: StateFlow<PasswordResetState> = _passwordResetState

    private val _userDeleteState = MutableStateFlow<UserDeleteState>(UserDeleteState.Idle)
    val userDeleteState: StateFlow<UserDeleteState> = _userDeleteState

    fun fetchAllUsers() {
        viewModelScope.launch {
            try {
                _usersListState.value = UsersListState.Loading
                val querySnapshot = firestore.collection("users").get().await()

                val users = querySnapshot.documents.mapNotNull { document ->
                    val uid = document.id
                    val name = document.getString("name")
                    val email = document.getString("email")
                    val isOnboarded = document.getBoolean("isOnboarded") ?: true
                    val rolesMap = document.get("roles") as? Map<*, *>
                    val roles = rolesMap?.let {
                        UserRoles(
                            student = it["student"] as? Boolean ?: false,
                            lecturer = it["lecturer"] as? Boolean ?: false,
                            admin = it["admin"] as? Boolean ?: false
                        )
                    }
                    UserListItem(uid = uid, name = name, email = email, roles = roles, isOnboarded = isOnboarded)
                }

                _usersListState.value = UsersListState.Success(users)
            } catch (e: Exception) {
                _usersListState.value = UsersListState.Error(e.message ?: "Failed to fetch users")
            }
        }
    }

    fun updateUserRoles(uid: String, roles: UserRoles) {
        viewModelScope.launch {
            try {
                _userUpdateState.value = UserUpdateState.Loading

                val rolesMap = mapOf(
                    "student" to roles.student,
                    "lecturer" to roles.lecturer,
                    "admin" to roles.admin
                )

                firestore.collection("users")
                    .document(uid)
                    .update("roles", rolesMap)
                    .await()

                _userUpdateState.value = UserUpdateState.Success

                // Refresh the users list
                fetchAllUsers()
            } catch (e: Exception) {
                _userUpdateState.value = UserUpdateState.Error(e.message ?: "Failed to update user roles")
            }
        }
    }

    fun resetUpdateState() {
        _userUpdateState.value = UserUpdateState.Idle
    }

    fun createUser(email: String, password: String, name: String, roles: UserRoles) {
        viewModelScope.launch {
            try {
                _userCreateState.value = UserCreateState.Loading

                // Prepare data for Cloud Function
                val data = hashMapOf(
                    "email" to email,
                    "password" to password,
                    "name" to name,
                    "roles" to mapOf(
                        "student" to roles.student,
                        "lecturer" to roles.lecturer,
                        "admin" to roles.admin
                    )
                )

                // Call the Cloud Function
                functions
                    .getHttpsCallable("createUser")
                    .call(data)
                    .await()

                _userCreateState.value = UserCreateState.Success

                // Refresh the users list
                fetchAllUsers()
            } catch (e: Exception) {
                _userCreateState.value = UserCreateState.Error(e.message ?: "Failed to create user")
            }
        }
    }

    fun resetCreateState() {
        _userCreateState.value = UserCreateState.Idle
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                _passwordResetState.value = PasswordResetState.Loading

                // Call Cloud Function to send password reset email
                val data = hashMapOf("email" to email)
                functions
                    .getHttpsCallable("sendPasswordReset")
                    .call(data)
                    .await()

                _passwordResetState.value = PasswordResetState.Success
            } catch (e: Exception) {
                _passwordResetState.value = PasswordResetState.Error(e.message ?: "Failed to send password reset email")
            }
        }
    }

    fun resetPasswordState() {
        _passwordResetState.value = PasswordResetState.Idle
    }

    fun deleteUser(uid: String) {
        viewModelScope.launch {
            try {
                _userDeleteState.value = UserDeleteState.Loading

                // Call Cloud Function to delete user from Auth and Firestore
                val data = hashMapOf("uid" to uid)
                functions
                    .getHttpsCallable("deleteUser")
                    .call(data)
                    .await()

                _userDeleteState.value = UserDeleteState.Success

                // Refresh the users list
                fetchAllUsers()
            } catch (e: Exception) {
                _userDeleteState.value = UserDeleteState.Error(e.message ?: "Failed to delete user")
            }
        }
    }

    fun resetDeleteState() {
        _userDeleteState.value = UserDeleteState.Idle
    }

    fun updateUserName(uid: String, newName: String) {
        viewModelScope.launch {
            try {
                _userUpdateState.value = UserUpdateState.Loading

                firestore.collection("users")
                    .document(uid)
                    .update("name", newName)
                    .await()

                _userUpdateState.value = UserUpdateState.Success
                fetchAllUsers()
            } catch (e: Exception) {
                _userUpdateState.value = UserUpdateState.Error(e.message ?: "Failed to update user name")
            }
        }
    }

    /**
     * Seeds the database with sample modules and schedules.
     * Uses existing lecturers and students from the users collection.
     */
    fun seedDatabase() {
        viewModelScope.launch {
            try {
                _seedDataState.value = SeedDataState.Loading

                // SIT Punggol Campus coordinates
                val sitLatitude = 1.4136
                val sitLongitude = 103.9123
                val radiusMeters = 500

                // Fetch all users to get lecturers and students
                val usersSnapshot = firestore.collection("users").get().await()
                val users = usersSnapshot.documents.mapNotNull { doc ->
                    val rolesMap = doc.get("roles") as? Map<*, *>
                    val roles = UserRoles(
                        student = rolesMap?.get("student") as? Boolean ?: false,
                        lecturer = rolesMap?.get("lecturer") as? Boolean ?: false,
                        admin = rolesMap?.get("admin") as? Boolean ?: false
                    )
                    Triple(doc.id, doc.getString("name") ?: "", roles)
                }

                val lecturers = users.filter { it.third.lecturer }
                val students = users.filter { it.third.student }
                val studentUids = students.map { it.first }

                if (lecturers.isEmpty()) {
                    _seedDataState.value = SeedDataState.Error("No lecturers found. Please create lecturer accounts first.")
                    return@launch
                }

                if (students.isEmpty()) {
                    _seedDataState.value = SeedDataState.Error("No students found. Please create student accounts first.")
                    return@launch
                }

                // Use first lecturer (or second if available for variety)
                val lecturer1 = lecturers[0]
                val lecturer2 = if (lecturers.size > 1) lecturers[1] else lecturers[0]

                // Check if data already exists
                val existingModules = firestore.collection("modules").limit(1).get().await()
                if (!existingModules.isEmpty) {
                    _seedDataState.value = SeedDataState.Error("Seed data already exists. Delete existing modules and schedules first if you want to re-seed.")
                    return@launch
                }

                // Create modules
                val modules = listOf(
                    hashMapOf(
                        "code" to "ICT2207",
                        "name" to "Mobile Security",
                        "description" to "Learn mobile application security fundamentals including Android and iOS security models, secure coding practices, and vulnerability assessment.",
                        "trimester" to "T2 2025-2026",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids
                    ),
                    hashMapOf(
                        "code" to "ICT2205",
                        "name" to "Web Security",
                        "description" to "Study web application security including OWASP Top 10, XSS, CSRF, SQL injection, and secure development practices.",
                        "trimester" to "T2 2025-2026",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids
                    ),
                    hashMapOf(
                        "code" to "ICT2104",
                        "name" to "Software Engineering",
                        "description" to "Software development methodologies, design patterns, testing strategies, and project management.",
                        "trimester" to "T2 2025-2026",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids
                    ),
                    hashMapOf(
                        "code" to "ICT2112",
                        "name" to "Network Security",
                        "description" to "Network security fundamentals including firewalls, IDS/IPS, VPNs, and network monitoring.",
                        "trimester" to "T2 2025-2026",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids
                    )
                )

                val moduleIds = mutableMapOf<String, String>()
                for (module in modules) {
                    val docRef = firestore.collection("modules").add(module).await()
                    moduleIds[module["code"] as String] = docRef.id
                }

                // Create schedules
                val schedules = listOf(
                    // ICT2207 - Mobile Security
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2207"],
                        "moduleCode" to "ICT2207",
                        "moduleName" to "Mobile Security",
                        "classType" to "LECTURE",
                        "dayOfWeek" to 1, // Monday
                        "startTime" to "09:00",
                        "endTime" to "11:00",
                        "venue" to "SIT Punggol Campus LT1",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2207"],
                        "moduleCode" to "ICT2207",
                        "moduleName" to "Mobile Security",
                        "classType" to "LAB",
                        "dayOfWeek" to 3, // Wednesday
                        "startTime" to "14:00",
                        "endTime" to "17:00",
                        "venue" to "SIT Punggol Campus Lab 4A",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    // ICT2205 - Web Security
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2205"],
                        "moduleCode" to "ICT2205",
                        "moduleName" to "Web Security",
                        "classType" to "LECTURE",
                        "dayOfWeek" to 2, // Tuesday
                        "startTime" to "09:00",
                        "endTime" to "11:00",
                        "venue" to "SIT Punggol Campus LT2",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2205"],
                        "moduleCode" to "ICT2205",
                        "moduleName" to "Web Security",
                        "classType" to "TUTORIAL",
                        "dayOfWeek" to 4, // Thursday
                        "startTime" to "10:00",
                        "endTime" to "12:00",
                        "venue" to "SIT Punggol Campus Tutorial Room 3",
                        "lecturerId" to lecturer1.first,
                        "lecturerName" to lecturer1.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    // ICT2104 - Software Engineering
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2104"],
                        "moduleCode" to "ICT2104",
                        "moduleName" to "Software Engineering",
                        "classType" to "LECTURE",
                        "dayOfWeek" to 1, // Monday
                        "startTime" to "14:00",
                        "endTime" to "16:00",
                        "venue" to "SIT Punggol Campus LT3",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2104"],
                        "moduleCode" to "ICT2104",
                        "moduleName" to "Software Engineering",
                        "classType" to "TUTORIAL",
                        "dayOfWeek" to 5, // Friday
                        "startTime" to "09:00",
                        "endTime" to "11:00",
                        "venue" to "SIT Punggol Campus Tutorial Room 1",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    // ICT2112 - Network Security
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2112"],
                        "moduleCode" to "ICT2112",
                        "moduleName" to "Network Security",
                        "classType" to "LECTURE",
                        "dayOfWeek" to 2, // Tuesday
                        "startTime" to "14:00",
                        "endTime" to "16:00",
                        "venue" to "SIT Punggol Campus LT1",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    ),
                    hashMapOf(
                        "moduleId" to moduleIds["ICT2112"],
                        "moduleCode" to "ICT2112",
                        "moduleName" to "Network Security",
                        "classType" to "LAB",
                        "dayOfWeek" to 4, // Thursday
                        "startTime" to "14:00",
                        "endTime" to "17:00",
                        "venue" to "SIT Punggol Campus Network Lab",
                        "lecturerId" to lecturer2.first,
                        "lecturerName" to lecturer2.second,
                        "enrolledStudents" to studentUids,
                        "latitude" to sitLatitude,
                        "longitude" to sitLongitude,
                        "radiusMeters" to radiusMeters,
                        "attendanceCode" to ""
                    )
                )

                for (schedule in schedules) {
                    firestore.collection("schedules").add(schedule).await()
                }

                val message = "Created ${modules.size} modules and ${schedules.size} schedules. " +
                        "${studentUids.size} student(s) enrolled in all modules."
                _seedDataState.value = SeedDataState.Success(message)

            } catch (e: Exception) {
                _seedDataState.value = SeedDataState.Error(e.message ?: "Failed to seed database")
            }
        }
    }

    fun resetSeedState() {
        _seedDataState.value = SeedDataState.Idle
    }
}
