package com.example.sitconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

data class UserRoles(
    val student: Boolean = false,
    val lecturer: Boolean = false,
    val admin: Boolean = false
)

data class UserData(
    val name: String? = null,
    val email: String? = null,
    val roles: UserRoles? = null
)

sealed class UserDataState {
    object Idle : UserDataState()
    object Loading : UserDataState()
    data class Success(val userData: UserData) : UserDataState()
    data class Error(val message: String) : UserDataState()
}

sealed class UpdateNameState {
    object Idle : UpdateNameState()
    object Loading : UpdateNameState()
    object Success : UpdateNameState()
    data class Error(val message: String) : UpdateNameState()
}

class UserViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _userDataState = MutableStateFlow<UserDataState>(UserDataState.Idle)
    val userDataState: StateFlow<UserDataState> = _userDataState

    private val _updateNameState = MutableStateFlow<UpdateNameState>(UpdateNameState.Idle)
    val updateNameState: StateFlow<UpdateNameState> = _updateNameState

    fun fetchUserData(uid: String) {
        viewModelScope.launch {
            try {
                _userDataState.value = UserDataState.Loading
                
                // Add timeout to prevent hanging indefinitely
                withTimeout(10000L) { // 10 second timeout
                    val document = firestore.collection("users").document(uid).get().await()

                    if (document.exists()) {
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
                        _userDataState.value = UserDataState.Success(UserData(name = name, email = email, roles = roles))
                    } else {
                        _userDataState.value = UserDataState.Error("User data not found")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _userDataState.value = UserDataState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _userDataState.value = UserDataState.Error(e.message ?: "Failed to fetch user data")
            }
        }
    }

    fun updateUserName(uid: String, newName: String) {
        viewModelScope.launch {
            try {
                _updateNameState.value = UpdateNameState.Loading

                withTimeout(10000L) {
                    firestore.collection("users").document(uid)
                        .update("name", newName)
                        .await()

                    _updateNameState.value = UpdateNameState.Success
                }
            } catch (e: TimeoutCancellationException) {
                _updateNameState.value = UpdateNameState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _updateNameState.value = UpdateNameState.Error(e.message ?: "Failed to update name")
            }
        }
    }

    fun resetUpdateNameState() {
        _updateNameState.value = UpdateNameState.Idle
    }

    fun resetUserDataState() {
        _userDataState.value = UserDataState.Idle
    }
}

