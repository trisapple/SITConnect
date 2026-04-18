package com.example.sitconnect2

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sitconnect2.features.calendar.presentation.CalendarScreen
import com.example.sitconnect2.features.mcsubmission.presentation.MCSubmissionScreen
import com.example.sitconnect2.features.attendance.presentation.AttendanceScreen
import com.example.sitconnect2.features.facilitybooking.presentation.BLT // change naming scheme
import com.example.sitconnect2.features.facilitybooking.presentation.FacilityBookingScreen
import com.example.sitconnect2.features.messaging.presentation.MessagingScreen
import com.example.sitconnect2.features.classmanagement.presentation.ClassManagementScreen
import com.example.sitconnect2.features.schedule.presentation.ScheduleScreen
import androidx.compose.material.icons.automirrored.filled.List
import kotlinx.coroutines.launch
import com.example.sitconnect2.features.admin.modulemanagement.presentation.ModuleManagementScreen
import com.example.sitconnect2.features.admin.scheduleoverview.presentation.ScheduleOverviewScreen
import com.example.sitconnect2.features.admin.facilitymanagement.presentation.FacilityManagementScreen
import com.example.sitconnect2.features.admin.mcreview.presentation.MCReviewScreen
import com.example.sitconnect2.features.calendar.presentation.LPG
import android.util.Log
import kotlinx.coroutines.delay


sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Profile : Screen("profile")
    object UserManagement : Screen("user_management")
    // Student features
    object Calendar : Screen("calendar")
    object MCSubmission : Screen("mc_submission")
    object Attendance : Screen("attendance")
    object FacilityBooking : Screen("facility_booking")
    object Messaging : Screen("messaging")
    // Lecturer features
    object ClassManagement : Screen("class_management")
    object Schedule : Screen("schedule")
    object RoomBooking : Screen("room_booking")
    // Admin features
    object ModuleManagement : Screen("module_management")
    object ScheduleOverview : Screen("schedule_overview")
    object FacilityManagement : Screen("facility_management")
    object MCReview : Screen("mc_review")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SITConnectNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: AuthViewModel = viewModel(),
    userViewModel: UserViewModel = viewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val userDataState by userViewModel.userDataState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    val currentUser = (authState as? AuthState.Success)?.user
    val userData = (userDataState as? UserDataState.Success)?.userData
    val isAdmin = userData?.roles?.admin == true
    val isLecturer = userData?.roles?.lecturer == true
    val isStudent = userData?.roles?.student == true

    // Fetch user data when authenticated
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            userViewModel.fetchUserData(uid)
        }
    }

    // Route after login: wait for user data to load, then decide home vs onboarding
    LaunchedEffect(authState, userDataState) {
        val isLoggedIn = authState is AuthState.Success
        val dataLoaded = userDataState is UserDataState.Success

        if (isLoggedIn && dataLoaded) {
            val data = (userDataState as UserDataState.Success).userData
            val route = navController.currentBackStackEntry?.destination?.route
            if (!data.isOnboarded) {
                // First-time user: send to onboarding (from any screen except onboarding itself)
                if (route != Screen.Onboarding.route) {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            } else {
                // Already onboarded: send to home only if on login screen
                if (route == Screen.Login.route || route == null) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // Determine start destination based on auth state
    val startDestination = if (authState is AuthState.Success) {
        Screen.Home.route
    } else {
        Screen.Login.route
    }


    // Only show drawer for authenticated screens (not login or onboarding)
    val showDrawer = authState is AuthState.Success &&
        currentRoute != Screen.Login.route &&
        currentRoute != Screen.Onboarding.route

    // Close drawer when navigating to non-drawer screens
    LaunchedEffect(showDrawer) {
        if (!showDrawer && drawerState.isOpen) {
            drawerState.close()
        }
    }

    // Single ModalNavigationDrawer + single NavHost (prevents glitching
    // caused by two NavHost instances swapping on showDrawer changes)
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = showDrawer,
        drawerContent = {
            if (showDrawer) {
                ModalDrawerSheet {
                    // Top section - SIT Connect title (fixed at top)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SIT Connect",
                        modifier = Modifier.padding(16.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Middle section - Scrollable navigation items
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = currentRoute == Screen.Home.route,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        // Student Features - show only for students
                        if (isStudent) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Student",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                                label = { Text("Trimester Calendar") },
                                selected = currentRoute == Screen.Calendar.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Calendar.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Create, contentDescription = "MC Submission") },
                                label = { Text("MC Submission") },
                                selected = currentRoute == Screen.MCSubmission.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.MCSubmission.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Check, contentDescription = "Attendance") },
                                label = { Text("Attendance") },
                                selected = currentRoute == Screen.Attendance.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Attendance.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Place, contentDescription = "Facility Booking") },
                                label = { Text("Facility Booking") },
                                selected = currentRoute == Screen.FacilityBooking.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.FacilityBooking.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Email, contentDescription = "Messaging") },
                                label = { Text("Messaging") },
                                selected = currentRoute == Screen.Messaging.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Messaging.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }

                        // Lecturer Features - show only for lecturers
                        if (isLecturer) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Lecturer",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Class Management") },
                                label = { Text("Class Management") },
                                selected = currentRoute == Screen.ClassManagement.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.ClassManagement.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "My Schedule") },
                                label = { Text("My Schedule") },
                                selected = currentRoute == Screen.Schedule.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Schedule.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Place, contentDescription = "Room Booking") },
                                label = { Text("Room Booking") },
                                selected = currentRoute == Screen.RoomBooking.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.RoomBooking.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Email, contentDescription = "Messaging") },
                                label = { Text("Messaging") },
                                selected = currentRoute == Screen.Messaging.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Messaging.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }

                        // Show User Management only for admins
                        if (isAdmin) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Admin",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.error
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "User Management") },
                                label = { Text("User Management") },
                                selected = currentRoute == Screen.UserManagement.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.UserManagement.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                                label = { Text("Calendar Events") },
                                selected = currentRoute == Screen.Calendar.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Calendar.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Module Management") },
                                label = { Text("Module Management") },
                                selected = currentRoute == Screen.ModuleManagement.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.ModuleManagement.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "Schedule Overview") },
                                label = { Text("Schedule Overview") },
                                selected = currentRoute == Screen.ScheduleOverview.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.ScheduleOverview.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Place, contentDescription = "Facility Management") },
                                label = { Text("Facility Management") },
                                selected = currentRoute == Screen.FacilityManagement.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.FacilityManagement.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Create, contentDescription = "MC Review") },
                                label = { Text("MC Review") },
                                selected = currentRoute == Screen.MCReview.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.MCReview.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )

                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Email, contentDescription = "Messaging") },
                                label = { Text("Messaging") },
                                selected = currentRoute == Screen.Messaging.route,
                                onClick = {
                                    scope.launch {
                                        drawerState.close()
                                        navController.navigate(Screen.Messaging.route)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                                shape = RoundedCornerShape(4.dp)
                            )
                        }
                    }

                    // Bottom section - Profile and Logout (fixed at bottom)
                    Column {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                            label = { Text("Profile") },
                            selected = currentRoute == Screen.Profile.route,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    navController.navigate(Screen.Profile.route)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            shape = RoundedCornerShape(4.dp)
                        )

                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Logout"
                                )
                            },
                            label = {
                                Text("Logout")
                            },
                            selected = false,
                            onClick = {
                                scope.launch {
                                    drawerState.close()
                                    userViewModel.resetUserDataState()
                                    viewModel.logout()
                                    delay(150)
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color(0xFF8B0000),
                                unselectedTextColor = Color.White,
                                unselectedIconColor = Color.White
                            )
                        )
                    }
                }
            } else {
                // Empty drawer content for login/onboarding screens
                ModalDrawerSheet { }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (showDrawer) {
                    TopAppBar(
                        title = {
                            Text(
                                when (currentRoute) {
                                    Screen.Home.route -> "Home"
                                    Screen.Profile.route -> "Profile"
                                    Screen.UserManagement.route -> "User Management"
                                    Screen.Calendar.route -> "Trimester Calendar"
                                    Screen.MCSubmission.route -> "MC Submission"
                                    Screen.Attendance.route -> "Attendance"
                                    Screen.FacilityBooking.route -> "Facility Booking"
                                    Screen.Messaging.route -> "Messaging"
                                    Screen.ClassManagement.route -> "Class Management"
                                    Screen.Schedule.route -> "My Schedule"
                                    Screen.RoomBooking.route -> "Room Booking"
                                    else -> "SIT Connect"
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) {
                                        drawerState.open()
                                    } else {
                                        drawerState.close()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = modifier.padding(paddingValues)
            ) {
                composable(Screen.Login.route) {
                    LoginScreen(
                        viewModel = viewModel,
                        onLoginSuccess = { ctx, uid ->
                            if (uid != null && ctx.LPG()) {
                                BLT(ctx, uid)
                                Log.i("LoginFlow", "Background location tracking started after login for uid: $uid")
                            } else if (uid != null) {
                                Log.w("LoginFlow", "Permissions missing after login — tracking not started yet")
                            }
                            // Routing handled by LaunchedEffect(authState, userDataState)
                        }
                    )
                }

                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        authViewModel = viewModel,
                        userViewModel = userViewModel,
                        onOnboardingComplete = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        userViewModel = userViewModel,
                        onLogout = {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNavigate = { route ->
                            navController.navigate(route)
                        }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(viewModel = viewModel)
                }

                composable(Screen.UserManagement.route) {
                    UserManagementScreen(
                        authViewModel = viewModel,
                        userViewModel = userViewModel
                    )
                }

                composable(Screen.Calendar.route) {
                    CalendarScreen()
                }

                composable(Screen.MCSubmission.route) {
                    MCSubmissionScreen(authViewModel = viewModel)
                }

                composable(Screen.Attendance.route) {
                    AttendanceScreen(authViewModel = viewModel)
                }

                composable(Screen.FacilityBooking.route) {
                    FacilityBookingScreen(authViewModel = viewModel)
                }

                composable(Screen.Messaging.route) {
                    MessagingScreen(
                        authViewModel = viewModel,
                        userViewModel = userViewModel
                    )
                }

                // Lecturer routes
                composable(Screen.ClassManagement.route) {
                    ClassManagementScreen(authViewModel = viewModel)
                }

                composable(Screen.Schedule.route) {
                    ScheduleScreen(authViewModel = viewModel)
                }

                composable(Screen.RoomBooking.route) {
                    // Reuse FacilityBookingScreen but filter for lecturer rooms
                    FacilityBookingScreen(authViewModel = viewModel)
                }

                // Admin routes
                composable(Screen.ModuleManagement.route) {
                    ModuleManagementScreen()
                }

                composable(Screen.ScheduleOverview.route) {
                    ScheduleOverviewScreen()
                }

                composable(Screen.FacilityManagement.route) {
                    FacilityManagementScreen()
                }

                composable(Screen.MCReview.route) {
                    MCReviewScreen(authViewModel = viewModel)
                }
            }
        }
    }
}
