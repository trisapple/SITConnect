package com.example.sitconnect2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Onboarding screen for first-time login (students and lecturers).
 *
 * Step 1 – Reset password: user enters the admin-set password and picks a new one.
 * Step 2 – Profile details: user enters their real name and contact number.
 *
 * On completion the user is logged out so they must sign in with their new password
 * and will then be routed to their normal home screen.
 */

data class PasswordValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

fun validatePassword(password: String): PasswordValidationResult {
    val errors = mutableListOf<String>()

    if (password.length < 6) {
        errors.add("Password must be at least 6 characters")
    }

    if (password.length > 128) {
        errors.add("Password must not exceed 128 characters")
    }

    if (!password.any { it.isUpperCase() }) {
        errors.add("Password must contain at least one uppercase letter")
    }

    if (!password.any { !it.isLetterOrDigit() }) {
        errors.add("Password must contain at least one special character (!@#\$%^&*)")
    }

    return PasswordValidationResult(
        isValid = errors.isEmpty(),
        errors = errors
    )
}

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    onOnboardingComplete: () -> Unit = {}
) {
    val onboardingState by userViewModel.onboardingState.collectAsState()

    // 0 = reset password, 1 = profile details
    var step by remember { mutableStateOf(0) }

    // Step 1 state
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    // Step 2 state
    var name by remember { mutableStateOf("") }
    var contactNumber by remember { mutableStateOf("") }

    // Captured from step 1 to use in final call
    var capturedCurrentPassword by remember { mutableStateOf("") }
    var capturedNewPassword by remember { mutableStateOf("") }

    // Field error states
    var currentPasswordError by remember { mutableStateOf<String?>(null) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var contactError by remember { mutableStateOf<String?>(null) }

    // When onboarding is complete, clear stale data, sign out and navigate back to login
    LaunchedEffect(onboardingState) {
        if (onboardingState is OnboardingState.Success) {
            userViewModel.resetOnboardingState()
            userViewModel.resetUserDataState() // clear stale isOnboarded=false from memory
            authViewModel.logout()
            onOnboardingComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Welcome to SIT Connect",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (step == 0)
                    "Step 1 of 2 – Set your new password"
                else
                    "Step 2 of 2 – Complete your profile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicator(stepNumber = 1, isActive = step == 0, isDone = step > 0)
                HorizontalDivider(modifier = Modifier.width(32.dp))
                StepIndicator(stepNumber = 2, isActive = step == 1, isDone = false)
            }

            HorizontalDivider()

            // ── Step 1: Reset Password ──────────────────────────────────────
            if (step == 0) {
                Text(
                    text = "Your account was created by an admin. Please set a new password to continue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        currentPasswordError = null
                    },
                    label = { Text("Current Password (set by admin)") },
                    isError = currentPasswordError != null,
                    supportingText = currentPasswordError?.let { { Text(it) } },
                    visualTransformation = if (showCurrentPassword)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                            Text(if (showCurrentPassword) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        newPasswordError = null
                    },
                    label = { Text("New Password") },
                    isError = newPasswordError != null,
                    supportingText = newPasswordError?.let { { Text(it) } },
                    visualTransformation = if (showNewPassword)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showNewPassword = !showNewPassword }) {
                            Text(if (showNewPassword) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmPasswordError = null
                    },
                    label = { Text("Confirm New Password") },
                    isError = confirmPasswordError != null,
                    supportingText = confirmPasswordError?.let { { Text(it) } },
                    visualTransformation = if (showConfirmPassword)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                            Text(if (showConfirmPassword) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        var valid = true
                        if (currentPassword.isBlank()) {
                            currentPasswordError = "Please enter your current password"
                            valid = false
                        }

                        val pwResult = validatePassword(newPassword)

                        if (pwResult.errors.isNotEmpty()) {
                            newPasswordError = pwResult.errors.joinToString("\n")
                            valid = false
                        }
                        else if (newPassword == currentPassword) {
                            newPasswordError = "New password must be different from the current one"
                            valid = false
                        } else {
                            newPasswordError = null
                        }

                        if (newPassword != confirmPassword) {
                            confirmPasswordError = "Passwords do not match"
                            valid = false
                        }

                        if (valid) {
                            capturedCurrentPassword = currentPassword
                            capturedNewPassword = newPassword
                            step = 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Next")
                }
            }

            // ── Step 2: Profile Details ─────────────────────────────────────
            if (step == 1) {
                Text(
                    text = "Please enter your name and contact number so others can reach you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Full Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = contactNumber,
                    onValueChange = {
                        contactNumber = it
                        contactError = null
                    },
                    label = { Text("Contact Number") },
                    isError = contactError != null,
                    supportingText = contactError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Show error from onboarding state
                if (onboardingState is OnboardingState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (onboardingState as OnboardingState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // If the error looks like a wrong-password error, allow going back
                    OutlinedButton(
                        onClick = {
                            userViewModel.resetOnboardingState()
                            step = 0
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Go Back")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { step = 0 },
                        modifier = Modifier.weight(1f),
                        enabled = onboardingState !is OnboardingState.Loading
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = {
                            var valid = true
                            if (name.isBlank()) {
                                nameError = "Name is required"
                                valid = false
                            }
                            if (contactNumber.isBlank()) {
                                contactError = "Contact number is required"
                                valid = false
                            }
                            if (valid) {
                                userViewModel.completeOnboarding(
                                    currentPassword = capturedCurrentPassword,
                                    newPassword = capturedNewPassword,
                                    name = name.trim(),
                                    contactNumber = contactNumber.trim()
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = onboardingState !is OnboardingState.Loading
                    ) {
                        if (onboardingState is OnboardingState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Complete Setup")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    stepNumber: Int,
    isActive: Boolean,
    isDone: Boolean
) {
    val containerColor = when {
        isDone -> MaterialTheme.colorScheme.primary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isDone || isActive -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = stepNumber.toString(),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

