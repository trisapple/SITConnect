package com.example.sitconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
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
    val roles: UserRoles? = null,
    val contactNumber: String? = null,
    val isOnboarded: Boolean = true
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

sealed class OnboardingState {
    object Idle : OnboardingState()
    object Loading : OnboardingState()
    object Success : OnboardingState()
    data class Error(val message: String) : OnboardingState()
}

class UserViewModel : ViewModel() {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _userDataState = MutableStateFlow<UserDataState>(UserDataState.Idle)
    val userDataState: StateFlow<UserDataState> = _userDataState

    private val _updateNameState = MutableStateFlow<UpdateNameState>(UpdateNameState.Idle)
    val updateNameState: StateFlow<UpdateNameState> = _updateNameState

    private val _onboardingState = MutableStateFlow<OnboardingState>(OnboardingState.Idle)
    val onboardingState: StateFlow<OnboardingState> = _onboardingState

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
                        val contactNumber = document.getString("contactNumber")
                        val rolesMap = document.get("roles") as? Map<*, *>
                        val roles = rolesMap?.let {
                            UserRoles(
                                student = it["student"] as? Boolean ?: false,
                                lecturer = it["lecturer"] as? Boolean ?: false,
                                admin = it["admin"] as? Boolean ?: false
                            )
                        }
                        // If isOnboarded field exists in Firestore, use it.
                        // If it doesn't exist yet (older accounts), treat as not onboarded
                        // only for non-admin users whose name is still the default "Test".
                        val isOnboardedField = document.getBoolean("isOnboarded")
                        val isAdmin = roles?.admin == true
                        val isOnboarded = when {
                            isOnboardedField != null -> isOnboardedField
                            isAdmin -> true // admins never need onboarding
                            name.isNullOrBlank() || name == "Test" -> false // not set up yet
                            else -> true // has a real name, treat as onboarded
                        }
                        _userDataState.value = UserDataState.Success(
                            UserData(
                                name = name,
                                email = email,
                                roles = roles,
                                contactNumber = contactNumber,
                                isOnboarded = isOnboarded
                            )
                        )
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

    /**
     * Completes onboarding for the user:
     * 1. Re-authenticates with the current (admin-set) password
     * 2. Updates the Firebase Auth password to the new password
     * 3. Updates Firestore with name, contactNumber, and isOnboarded = true
     */
    fun completeOnboarding(
        currentPassword: String,
        newPassword: String,
        name: String,
        contactNumber: String
    ) {
        viewModelScope.launch {
            try {
                _onboardingState.value = OnboardingState.Loading

                val user = auth.currentUser
                    ?: throw Exception("No authenticated user found")
                val email = user.email
                    ?: throw Exception("User email not found")

                withTimeout(15000L) {
                    // Step 1: Re-authenticate with the admin-set password
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential).await()

                    // Step 2: Update the password
                    user.updatePassword(newPassword).await()

                    // Step 3: Update Firestore profile
                    firestore.collection("users").document(user.uid)
                        .update(
                            mapOf(
                                "name" to name,
                                "contactNumber" to contactNumber,
                                "isOnboarded" to true
                            )
                        )
                        .await()
                }

                _onboardingState.value = OnboardingState.Success
            } catch (e: TimeoutCancellationException) {
                _onboardingState.value = OnboardingState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _onboardingState.value = OnboardingState.Error(e.message ?: "Onboarding failed")
            }
        }
    }

    fun resetOnboardingState() {
        _onboardingState.value = OnboardingState.Idle
    }

    fun resetUpdateNameState() {
        _updateNameState.value = UpdateNameState.Idle
    }

    fun resetUserDataState() {
        _userDataState.value = UserDataState.Idle
    }
}

