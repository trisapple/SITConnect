package com.example.sitconnect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sitconnect.ui.theme.SITConnectTheme

data class FeatureItem(
    val title: String,
    val emoji: String,
    val description: String,
    val route: String,
    val color: Color
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel(),
    onLogout: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()
    val userDataState by userViewModel.userDataState.collectAsState()
    val user = (authState as? AuthState.Success)?.user
    val userData = (userDataState as? UserDataState.Success)?.userData
    val isStudent = userData?.roles?.student == true
    val isLecturer = userData?.roles?.lecturer == true
    val isAdmin = userData?.roles?.admin == true

    // Fetch user data when user changes
    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Student features
    val studentFeatures = listOf(
        FeatureItem(
            title = "Calendar",
            emoji = "📅",
            description = "View exam dates, recess weeks, holidays",
            route = Screen.Calendar.route,
            color = Color(0xFF2196F3)
        ),
        FeatureItem(
            title = "MC Submission",
            emoji = "🏥",
            description = "Submit Medical Certificates",
            route = Screen.MCSubmission.route,
            color = Color(0xFF4CAF50)
        ),
        FeatureItem(
            title = "Attendance",
            emoji = "✅",
            description = "Mark attendance via GPS + Code",
            route = Screen.Attendance.route,
            color = Color(0xFF9C27B0)
        ),
        FeatureItem(
            title = "Facility Booking",
            emoji = "🏢",
            description = "Book DR & Sports facilities",
            route = Screen.FacilityBooking.route,
            color = Color(0xFF00BCD4)
        ),
        FeatureItem(
            title = "Messaging",
            emoji = "💬",
            description = "Join group discussions",
            route = Screen.Messaging.route,
            color = Color(0xFF795548)
        )
    )

    // Lecturer features
    val lecturerFeatures = listOf(
        FeatureItem(
            title = "Class Management",
            emoji = "👥",
            description = "View students in your modules",
            route = Screen.ClassManagement.route,
            color = Color(0xFF3F51B5)
        ),
        FeatureItem(
            title = "My Schedule",
            emoji = "📆",
            description = "View teaching timetable",
            route = Screen.Schedule.route,
            color = Color(0xFFFF9800)
        ),
        FeatureItem(
            title = "Room Booking",
            emoji = "🚪",
            description = "Book Meeting Rooms & Lecture Halls",
            route = Screen.RoomBooking.route,
            color = Color(0xFF009688)
        ),
        FeatureItem(
            title = "Messaging",
            emoji = "💬",
            description = "Discuss with students",
            route = Screen.Messaging.route,
            color = Color(0xFF795548)
        )
    )

    // Admin features
    val adminFeatures = listOf(
        FeatureItem(
            title = "User Management",
            emoji = "⚙️",
            description = "Create/Delete accounts",
            route = Screen.UserManagement.route,
            color = Color(0xFFF44336)
        ),
        FeatureItem(
            title = "Calendar Events",
            emoji = "📅",
            description = "Manage calendar events",
            route = Screen.Calendar.route,
            color = Color(0xFF2196F3)
        ),
        FeatureItem(
            title = "Module Management",
            emoji = "📚",
            description = "Create/Edit/Delete modules",
            route = Screen.ModuleManagement.route,
            color = Color(0xFF3F51B5)
        ),
        FeatureItem(
            title = "Schedule Overview",
            emoji = "📆",
            description = "View all schedules & conflicts",
            route = Screen.ScheduleOverview.route,
            color = Color(0xFFFF9800)
        ),
        FeatureItem(
            title = "Facility Management",
            emoji = "🏢",
            description = "Manage bookings & facilities",
            route = Screen.FacilityManagement.route,
            color = Color(0xFF009688)
        ),
        FeatureItem(
            title = "MC Review",
            emoji = "🏥",
            description = "Review MC submissions",
            route = Screen.MCReview.route,
            color = Color(0xFF4CAF50)
        )
    )

    // Determine which features to show based on role
    val features = when {
        isStudent -> studentFeatures
        isLecturer -> lecturerFeatures
        isAdmin -> adminFeatures
        else -> emptyList() // Loading or no role
    }

    // Determine role text for welcome message
    val roleText = when {
        isAdmin -> "Admin"
        isLecturer -> "Lecturer"
        isStudent -> "Student"
        else -> "User"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Welcome Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Welcome to SIT Connect! 👋",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = user?.email ?: roleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                Text(
                    text = roleText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Quick Access",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Feature Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(features) { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = { onNavigate(feature.route) }
                )
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = feature.color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = feature.emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = feature.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = feature.color,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feature.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SITConnectTheme {
        HomeScreen()
    }
}
