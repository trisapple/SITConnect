package com.example.sitconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserData(
    val name: String? = null,
    val email: String? = null
)

sealed class UserDataState {
    object Idle : UserDataState()
    object Loading : UserDataState()
    data class Success(val userData: UserData) : UserDataState()
    data class Error(val message: String) : UserDataState()
}

class UserViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val _userDataState = MutableStateFlow<UserDataState>(UserDataState.Idle)
    val userDataState: StateFlow<UserDataState> = _userDataState

    fun fetchUserData(uid: String) {
        viewModelScope.launch {
            try {
                _userDataState.value = UserDataState.Loading
                val document = firestore.collection("users").document(uid).get().await()

                if (document.exists()) {
                    val name = document.getString("name")
                    val email = document.getString("email")
                    _userDataState.value = UserDataState.Success(UserData(name = name, email = email))
                } else {
                    _userDataState.value = UserDataState.Error("User data not found")
                }
            } catch (e: Exception) {
                _userDataState.value = UserDataState.Error(e.message ?: "Failed to fetch user data")
            }
        }
    }

    fun resetUserDataState() {
        _userDataState.value = UserDataState.Idle
    }
}

