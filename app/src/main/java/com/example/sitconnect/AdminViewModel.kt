package com.example.sitconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserListItem(
    val uid: String = "",
    val name: String? = null,
    val email: String? = null,
    val roles: UserRoles? = null
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

class AdminViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _usersListState = MutableStateFlow<UsersListState>(UsersListState.Idle)
    val usersListState: StateFlow<UsersListState> = _usersListState

    private val _userUpdateState = MutableStateFlow<UserUpdateState>(UserUpdateState.Idle)
    val userUpdateState: StateFlow<UserUpdateState> = _userUpdateState

    fun fetchAllUsers() {
        viewModelScope.launch {
            try {
                _usersListState.value = UsersListState.Loading
                val querySnapshot = firestore.collection("users").get().await()

                val users = querySnapshot.documents.mapNotNull { document ->
                    val uid = document.id
                    val name = document.getString("name")
                    val email = document.getString("email")
                    val rolesMap = document.get("roles") as? Map<*, *>
                    val roles = rolesMap?.let {
                        UserRoles(
                            student = it["student"] as? Boolean ?: false,
                            lecturer = it["lecturer"] as? Boolean ?: false,
                            admin = it["admin"] as? Boolean ?: false
                        )
                    }
                    UserListItem(uid = uid, name = name, email = email, roles = roles)
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
}

