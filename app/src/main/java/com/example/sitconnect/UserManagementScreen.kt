package com.example.sitconnect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val userCreateState by adminViewModel.userCreateState.collectAsState()
    val passwordResetState by adminViewModel.passwordResetState.collectAsState()
    val userDeleteState by adminViewModel.userDeleteState.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<UserListItem?>(null) }
    var showResetPasswordDialog by remember { mutableStateOf<UserListItem?>(null) }
    var showEditNameDialog by remember { mutableStateOf<UserListItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

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

    // Handle user creation success/error
    LaunchedEffect(userCreateState) {
        when (userCreateState) {
            is UserCreateState.Success -> {
                showCreateDialog = false
                snackbarHostState.showSnackbar("User created successfully!")
                adminViewModel.resetCreateState()
            }
            is UserCreateState.Error -> {
                snackbarHostState.showSnackbar(
                    "Error: ${(userCreateState as UserCreateState.Error).message}"
                )
                adminViewModel.resetCreateState()
            }
            else -> {}
        }
    }

    // Handle password reset success/error
    LaunchedEffect(passwordResetState) {
        when (passwordResetState) {
            is PasswordResetState.Success -> {
                showResetPasswordDialog = null
                snackbarHostState.showSnackbar("Password reset email sent!")
                adminViewModel.resetPasswordState()
            }
            is PasswordResetState.Error -> {
                snackbarHostState.showSnackbar(
                    "Error: ${(passwordResetState as PasswordResetState.Error).message}"
                )
                adminViewModel.resetPasswordState()
            }
            else -> {}
        }
    }

    // Handle user deletion success/error
    LaunchedEffect(userDeleteState) {
        when (userDeleteState) {
            is UserDeleteState.Success -> {
                showDeleteDialog = null
                snackbarHostState.showSnackbar("User deleted successfully!")
                adminViewModel.resetDeleteState()
            }
            is UserDeleteState.Error -> {
                snackbarHostState.showSnackbar(
                    "Error: ${(userDeleteState as UserDeleteState.Error).message}"
                )
                adminViewModel.resetDeleteState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (isAdmin && userDataState !is UserDataState.Loading) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create User")
                }
            }
        }
    ) { paddingValues ->
        when {
            userDataState is UserDataState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            !isAdmin -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
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
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Header section
                            item {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
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
                                }
                            }

                            // User cards
                            items(users) { user ->
                                UserManagementCard(
                                    user = user,
                                    currentUserId = currentUser?.uid,
                                    onUpdateRoles = { roles ->
                                        adminViewModel.updateUserRoles(user.uid, roles)
                                    },
                                    onResetPassword = { showResetPasswordDialog = user },
                                    onDeleteUser = { showDeleteDialog = user },
                                    onEditName = { showEditNameDialog = user },
                                    isUpdating = userUpdateState is UserUpdateState.Loading
                                )
                            }
                        }
                    }
                    is UsersListState.Error -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Header section
                            item {
                                Column(
                                    modifier = Modifier.padding(bottom = 16.dp)
                                ) {
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
                                }
                            }

                            // Error card
                            item {
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
                        }
                    }
                    is UsersListState.Idle -> {
                        // Initial state - show header only
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            item {
                                Column {
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Show create user dialog
    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { email, password, name, roles ->
                adminViewModel.createUser(email, password, name, roles)
            },
            isLoading = userCreateState is UserCreateState.Loading
        )
    }

    // Reset password dialog
    showResetPasswordDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showResetPasswordDialog = null },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Send password reset email to:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.email ?: "Unknown email",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (passwordResetState is PasswordResetState.Loading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { user.email?.let { adminViewModel.resetPassword(it) } },
                    enabled = passwordResetState !is PasswordResetState.Loading && user.email != null
                ) {
                    Text("Send Reset Email")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showResetPasswordDialog = null },
                    enabled = passwordResetState !is PasswordResetState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete user dialog
    showDeleteDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete User") },
            text = {
                Column {
                    Text("Are you sure you want to delete this user?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${user.name ?: "Unknown"} (${user.email ?: "No email"})",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    if (userDeleteState is UserDeleteState.Loading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { adminViewModel.deleteUser(user.uid) },
                    enabled = userDeleteState !is UserDeleteState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = null },
                    enabled = userDeleteState !is UserDeleteState.Loading
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit name dialog
    showEditNameDialog?.let { user ->
        var newName by remember { mutableStateOf(user.name ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditNameDialog = null },
            title = { Text("Edit User Name") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            adminViewModel.updateUserName(user.uid, newName)
                            showEditNameDialog = null
                        }
                    },
                    enabled = newName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEditNameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UserManagementCard(
    user: UserListItem,
    currentUserId: String?,
    onUpdateRoles: (UserRoles) -> Unit,
    onResetPassword: () -> Unit,
    onDeleteUser: () -> Unit,
    onEditName: () -> Unit,
    isUpdating: Boolean
) {
    var showDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Roles") },
                            onClick = {
                                showMenu = false
                                showDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            enabled = !isUpdating
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Name") },
                            onClick = {
                                showMenu = false
                                onEditName()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset Password") },
                            onClick = {
                                showMenu = false
                                onResetPassword()
                            },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                        )
                        if (!isCurrentUser) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete User", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDeleteUser()
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
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

    Spacer(modifier = Modifier.height(16.dp))

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
    // Determine initial selected role
    val initialRole = when {
        user.roles?.admin == true -> "admin"
        user.roles?.lecturer == true -> "lecturer"
        user.roles?.student == true -> "student"
        else -> "student" // default to student
    }

    var selectedRole by remember { mutableStateOf(initialRole) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Role for ${user.name ?: "User"}")
        },
        text = {
            Column {
                Text(
                    text = "Select one role for this user:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "student",
                        onClick = { selectedRole = "student" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Student")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "lecturer",
                        onClick = { selectedRole = "lecturer" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lecturer")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "admin",
                        onClick = { selectedRole = "admin" }
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
                        student = selectedRole == "student",
                        lecturer = selectedRole == "lecturer",
                        admin = selectedRole == "admin"
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

@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, UserRoles) -> Unit,
    isLoading: Boolean
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("student") }

    var emailError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text("Create New User")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enter user details:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Name") },
                    isError = nameError,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        emailError = !it.contains("@") || it.isBlank()
                    },
                    label = { Text("Email") },
                    isError = emailError,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = it.length < 6
                    },
                    label = { Text("Password") },
                    isError = passwordError,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        if (passwordError) {
                            Text("Password must be at least 6 characters")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select one role:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "student",
                        onClick = { selectedRole = "student" },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Student")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "lecturer",
                        onClick = { selectedRole = "lecturer" },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lecturer")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedRole == "admin",
                        onClick = { selectedRole = "admin" },
                        enabled = !isLoading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Admin")
                }

                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val hasErrors = email.isBlank() || !email.contains("@") ||
                                   password.length < 6 || name.isBlank()

                    if (!hasErrors) {
                        onConfirm(
                            email,
                            password,
                            name,
                            UserRoles(
                                student = selectedRole == "student",
                                lecturer = selectedRole == "lecturer",
                                admin = selectedRole == "admin"
                            )
                        )
                    } else {
                        emailError = email.isBlank() || !email.contains("@")
                        passwordError = password.length < 6
                        nameError = name.isBlank()
                    }
                },
                enabled = !isLoading
            ) {
                Text("Create User")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

