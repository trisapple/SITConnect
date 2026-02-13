package com.example.sitconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UserManagementScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Success)?.user
    val userDataState by userViewModel.userDataState.collectAsState()
    val usersListState by adminViewModel.usersListState.collectAsState()
    val userUpdateState by adminViewModel.userUpdateState.collectAsState()

    // Fetch current user's data to check admin status
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Check if current user is admin
    val isAdmin = (userDataState as? UserDataState.Success)?.userData?.roles?.admin == true

    // Fetch all users if admin
    LaunchedEffect(isAdmin) {
        if (isAdmin) {
            adminViewModel.fetchAllUsers()
        }
    }

    // Show success message when user is updated
    LaunchedEffect(userUpdateState) {
        if (userUpdateState is UserUpdateState.Success) {
            adminViewModel.resetUpdateState()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        when {
            userDataState is UserDataState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            !isAdmin -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Access Denied",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Access Denied",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You do not have administrator privileges",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                Text(
                    text = "User Management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Manage user roles and permissions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                when (usersListState) {
                    is UsersListState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is UsersListState.Success -> {
                        val users = (usersListState as UsersListState.Success).users
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(users) { user ->
                                UserManagementCard(
                                    user = user,
                                    currentUserId = currentUser?.uid,
                                    onUpdateRoles = { roles ->
                                        adminViewModel.updateUserRoles(user.uid, roles)
                                    },
                                    isUpdating = userUpdateState is UserUpdateState.Loading
                                )
                            }
                        }
                    }
                    is UsersListState.Error -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = "Error: ${(usersListState as UsersListState.Error).message}",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    is UsersListState.Idle -> {
                        // Initial state
                    }
                }
            }
        }
    }
}

@Composable
fun UserManagementCard(
    user: UserListItem,
    currentUserId: String?,
    onUpdateRoles: (UserRoles) -> Unit,
    isUpdating: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    val isCurrentUser = user.uid == currentUserId

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = user.name ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = user.email ?: "No email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Button(
                    onClick = { showDialog = true },
                    enabled = !isUpdating
                ) {
                    Text("Edit Roles")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (user.roles?.admin == true) {
                    RoleBadge("Admin", MaterialTheme.colorScheme.error)
                }
                if (user.roles?.lecturer == true) {
                    RoleBadge("Lecturer", MaterialTheme.colorScheme.tertiary)
                }
                if (user.roles?.student == true) {
                    RoleBadge("Student", MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showDialog) {
        EditRolesDialog(
            user = user,
            onDismiss = { showDialog = false },
            onConfirm = { roles ->
                onUpdateRoles(roles)
                showDialog = false
            }
        )
    }
}

@Composable
fun RoleBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EditRolesDialog(
    user: UserListItem,
    onDismiss: () -> Unit,
    onConfirm: (UserRoles) -> Unit
) {
    var isStudent by remember { mutableStateOf(user.roles?.student ?: false) }
    var isLecturer by remember { mutableStateOf(user.roles?.lecturer ?: false) }
    var isAdmin by remember { mutableStateOf(user.roles?.admin ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Roles for ${user.name ?: "User"}")
        },
        text = {
            Column {
                Text(
                    text = "Select the roles for this user:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isStudent,
                        onCheckedChange = { isStudent = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Student")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isLecturer,
                        onCheckedChange = { isLecturer = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lecturer")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAdmin,
                        onCheckedChange = { isAdmin = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(UserRoles(
                        student = isStudent,
                        lecturer = isLecturer,
                        admin = isAdmin
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

