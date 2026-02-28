package com.example.sitconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser?) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class EmailActionState {
    object Idle : EmailActionState()
    object Loading : EmailActionState()
    data class Success(val message: String) : EmailActionState()
    data class Error(val message: String) : EmailActionState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _emailActionState = MutableStateFlow<EmailActionState>(EmailActionState.Idle)
    val emailActionState: StateFlow<EmailActionState> = _emailActionState

    init {
        // Check if user is already logged in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            _authState.value = AuthState.Success(currentUser)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Add timeout to prevent hanging indefinitely
                withTimeout(15000L) { // 15 second timeout
                    val result = auth.signInWithEmailAndPassword(email, password).await()
                    _authState.value = AuthState.Success(result.user)
                }
            } catch (e: TimeoutCancellationException) {
                _authState.value = AuthState.Error("Login timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // Add timeout to prevent hanging indefinitely
                withTimeout(15000L) { // 15 second timeout
                    val result = auth.createUserWithEmailAndPassword(email, password).await()
                    _authState.value = AuthState.Success(result.user)
                }
            } catch (e: TimeoutCancellationException) {
                _authState.value = AuthState.Error("Sign up timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Sign up failed")
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            try {
                _emailActionState.value = EmailActionState.Loading
                withTimeout(15000L) {
                    auth.sendPasswordResetEmail(email).await()
                    _emailActionState.value = EmailActionState.Success("Password reset email sent to $email")
                }
            } catch (e: TimeoutCancellationException) {
                _emailActionState.value = EmailActionState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _emailActionState.value = EmailActionState.Error(e.message ?: "Failed to send password reset email")
            }
        }
    }

    fun sendVerificationEmail() {
        viewModelScope.launch {
            try {
                _emailActionState.value = EmailActionState.Loading
                withTimeout(15000L) {
                    auth.currentUser?.sendEmailVerification()?.await()
                    _emailActionState.value = EmailActionState.Success("Verification email sent. Please check your inbox.")
                }
            } catch (e: TimeoutCancellationException) {
                _emailActionState.value = EmailActionState.Error("Request timed out. Please check your internet connection.")
            } catch (e: Exception) {
                _emailActionState.value = EmailActionState.Error(e.message ?: "Failed to send verification email")
            }
        }
    }

    fun resetEmailActionState() {
        _emailActionState.value = EmailActionState.Idle
    }

    fun logout() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }
}
