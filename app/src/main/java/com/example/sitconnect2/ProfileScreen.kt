package com.example.sitconnect2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect2.ui.theme.SITConnectTheme

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val user = (authState as? AuthState.Success)?.user
    val userDataState by userViewModel.userDataState.collectAsState()
    val updateNameState by userViewModel.updateNameState.collectAsState()
    val emailActionState by viewModel.emailActionState.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showVerifyEmailPrompt by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Fetch user data when user is available
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
        // Reload Firebase user so isEmailVerified is up-to-date without re-login
        viewModel.reloadUser()
    }

    // Handle name update success/error
    LaunchedEffect(updateNameState) {
        when (updateNameState) {
            is UpdateNameState.Success -> {
                showEditNameDialog = false
                snackbarHostState.showSnackbar("Name updated successfully!")
                userViewModel.resetUpdateNameState()
                // Refresh user data
                user?.uid?.let { userViewModel.fetchUserData(it) }
            }
            is UpdateNameState.Error -> {
                snackbarHostState.showSnackbar(
                    "Error: ${(updateNameState as UpdateNameState.Error).message}"
                )
                userViewModel.resetUpdateNameState()
            }
            else -> {}
        }
    }

    // Handle email action (password reset / verification) feedback
    LaunchedEffect(emailActionState) {
        when (emailActionState) {
            is EmailActionState.Success -> {
                snackbarHostState.showSnackbar((emailActionState as EmailActionState.Success).message)
                viewModel.resetEmailActionState()
            }
            is EmailActionState.Error -> {
                snackbarHostState.showSnackbar("Error: ${(emailActionState as EmailActionState.Error).message}")
                viewModel.resetEmailActionState()
            }
            else -> {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Profile Avatar Section
            Box(
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (userDataState) {
                is UserDataState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                }
                is UserDataState.Success -> {
                    val userData = (userDataState as UserDataState.Success).userData

                    // User name with edit button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = userData.name ?: "User",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { showEditNameDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Role badge
                    val role = when {
                        userData.roles?.admin == true -> "Admin"
                        userData.roles?.lecturer == true -> "Lecturer"
                        userData.roles?.student == true -> "Student"
                        else -> "User"
                    }
                    val roleColor = when (role) {
                        "Admin" -> MaterialTheme.colorScheme.error
                        "Lecturer" -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        color = roleColor.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = role,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = roleColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Account Info Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Account Information",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            ProfileInfoRow(
                                icon = Icons.Default.Email,
                                label = "Email",
                                value = user?.email ?: "Not available"
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            ProfileInfoRow(
                                icon = Icons.Default.Phone,
                                label = "Contact Number",
                                value = userData.contactNumber?.ifBlank { "Not set" } ?: "Not set"
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Email Verified",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (user?.isEmailVerified == true) "Yes" else "No",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Surface(
                                    color = if (user?.isEmailVerified == true)
                                        Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else
                                        MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = if (user?.isEmailVerified == true) "✓ Verified" else "Not Verified",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (user?.isEmailVerified == true)
                                            Color(0xFF4CAF50)
                                        else
                                            MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Show "Verify Email" button only when email is not verified
                            if (user?.isEmailVerified == false) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { viewModel.sendVerificationEmail() },
                                    enabled = emailActionState !is EmailActionState.Loading,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (emailActionState is EmailActionState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Send Verification Email")
                                }
                            }
                        }
                    }

                    // Edit Name Dialog
                    if (showEditNameDialog) {
                        EditNameDialog(
                            currentName = userData.name ?: "",
                            isLoading = updateNameState is UpdateNameState.Loading,
                            onDismiss = { showEditNameDialog = false },
                            onConfirm = { newName ->
                                user?.uid?.let { uid ->
                                    userViewModel.updateUserName(uid, newName)
                                }
                            }
                        )
                    }

                    // Email verification required prompt
                    if (showVerifyEmailPrompt) {
                        AlertDialog(
                            onDismissRequest = { showVerifyEmailPrompt = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = { Text("Email Verification Required") },
                            text = {
                                Text(
                                    "You must verify your email address before resetting your password.\n\nGo to Account Information above and tap \"Send Verification Email\" to verify your email first."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showVerifyEmailPrompt = false
                                        viewModel.sendVerificationEmail()
                                    },
                                    enabled = emailActionState !is EmailActionState.Loading
                                ) {
                                    Text("Send Verification Email")
                                }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showVerifyEmailPrompt = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Reset Password card — students and lecturers only
                    val isStudentOrLecturer = userData.roles?.student == true || userData.roles?.lecturer == true
                    if (isStudentOrLecturer) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Security",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        if (user?.isEmailVerified == true) {
                                            user.email?.let { email ->
                                                viewModel.sendPasswordResetEmail(email)
                                            }
                                        } else {
                                            showVerifyEmailPrompt = true
                                        }
                                    },
                                    enabled = emailActionState !is EmailActionState.Loading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (emailActionState is EmailActionState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text("Send Password Reset Email")
                                }
                            }
                        }
                    }
                }
                is UserDataState.Error -> {
                    Text(
                        text = "User",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error loading profile: ${(userDataState as UserDataState.Error).message}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ProfileInfoRow(
                                icon = Icons.Default.Email,
                                label = "Email",
                                value = user?.email ?: "Not available"
                            )
                        }
                    }
                }
                is UserDataState.Idle -> {
                    Text(
                        text = "User",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            ProfileInfoRow(
                                icon = Icons.Default.Email,
                                label = "Email",
                                value = user?.email ?: "Not available"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun EditNameDialog(
    currentName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Edit Name") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (newName.isNotBlank()) onConfirm(newName) },
                enabled = newName.isNotBlank() && !isLoading
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    SITConnectTheme {
        ProfileScreen()
    }
}
