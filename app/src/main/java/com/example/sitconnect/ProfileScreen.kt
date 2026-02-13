package com.example.sitconnect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.ui.theme.SITConnectTheme

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val user = (authState as? AuthState.Success)?.user
    val userDataState by userViewModel.userDataState.collectAsState()

    // Fetch user data when user is available
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = "Profile",
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Profile",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        when (userDataState) {
            is UserDataState.Loading -> {
                CircularProgressIndicator()
            }
            is UserDataState.Success -> {
                val userData = (userDataState as UserDataState.Success).userData
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ProfileInfoRow(label = "Name", value = userData.name ?: "Not set")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(label = "Email", value = user?.email ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(label = "User ID", value = user?.uid ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(
                            label = "Email Verified",
                            value = if (user?.isEmailVerified == true) "Yes" else "No"
                        )
                    }
                }
            }
            is UserDataState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Error loading user data: ${(userDataState as UserDataState.Error).message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(label = "Email", value = user?.email ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(label = "User ID", value = user?.uid ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(
                            label = "Email Verified",
                            value = if (user?.isEmailVerified == true) "Yes" else "No"
                        )
                    }
                }
            }
            is UserDataState.Idle -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        ProfileInfoRow(label = "Email", value = user?.email ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(label = "User ID", value = user?.uid ?: "Not available")
                        Spacer(modifier = Modifier.height(12.dp))
                        ProfileInfoRow(
                            label = "Email Verified",
                            value = if (user?.isEmailVerified == true) "Yes" else "No"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "More profile features coming soon!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    SITConnectTheme {
        ProfileScreen()
    }
}
