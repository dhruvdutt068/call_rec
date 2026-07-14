package com.example.callog.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.callog.presentation.screens.dashboard.DashboardScreen
import com.example.callog.presentation.screens.details.CallDetailsScreen
import com.example.callog.presentation.screens.logs.CallLogsScreen
import com.example.callog.presentation.screens.permission.PermissionScreen
import com.example.callog.presentation.screens.recordings.RecordingManagerScreen
import com.example.callog.presentation.screens.settings.SettingsScreen
import com.example.callog.presentation.screens.splash.SplashScreen
import com.example.callog.presentation.screens.developer.DeveloperDashboardScreen
import com.example.callog.presentation.screens.developer.SyncLogsScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

import com.example.callog.presentation.screens.onboarding.OnboardingScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateNext = { isPermissionGranted ->
                    if (isPermissionGranted) {
                        callViewModel.loadSimConfigurations()
                        val hasPhone = callViewModel.devicePhoneNumber.value.isNotEmpty()
                        val hasOwner = callViewModel.deviceOwnerName.value.isNotEmpty()
                        if (hasPhone && hasOwner) {
                            navController.navigate(Screen.Main.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.Onboarding.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    } else {
                        navController.navigate(Screen.Permission.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        
        composable(Screen.Permission.route) {
            PermissionScreen(
                onPermissionsGranted = {
                    callViewModel.loadSimConfigurations()
                    callViewModel.syncLogs()
                    val hasPhone = callViewModel.devicePhoneNumber.value.isNotEmpty()
                    val hasOwner = callViewModel.deviceOwnerName.value.isNotEmpty()
                    if (hasPhone && hasOwner) {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Permission.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Permission.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                viewModel = callViewModel,
                onSetupComplete = {
                    callViewModel.loadSimConfigurations()
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            val isSimChangeRequired by callViewModel.isSimChangeRequired.collectAsState()
            val activeSims by callViewModel.activeSims.collectAsState()
            val selectedSubId by callViewModel.selectedSimId.collectAsState()
            val isSuspendedDueToChange = activeSims.isNotEmpty() && selectedSubId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID

            Box {
                MainScreen(
                    callViewModel = callViewModel,
                    analyticsViewModel = analyticsViewModel,
                    onCallClick = { callId ->
                        navController.navigate(Screen.CallDetails.createRoute(callId))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )

                if (isSimChangeRequired) {
                    com.example.callog.presentation.components.BusinessSimWizardDialog(
                        activeSims = activeSims,
                        onSelectSim = { sim ->
                            callViewModel.saveBusinessSim(sim)
                        },
                        onSelectSyncAll = {
                            callViewModel.saveSyncAll()
                        },
                        isSuspendedDueToChange = isSuspendedDueToChange
                    )
                }
            }
        }

        composable(
            route = Screen.CallDetails.route,
            arguments = listOf(navArgument("callId") { type = NavType.LongType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getLong("callId") ?: 0L
            CallDetailsScreen(
                callId = callId,
                viewModel = callViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            Scaffold(
                topBar = {
                    OptInTopAppBar(
                        title = "Settings",
                        onBackClick = { navController.popBackStack() }
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                SettingsScreen(
                    viewModel = callViewModel,
                    darkTheme = darkTheme,
                    onDarkThemeChange = onDarkThemeChange,
                    onNavigateToDeveloperDashboard = {
                        navController.navigate(Screen.DeveloperDashboard.route)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }

        composable(Screen.DeveloperDashboard.route) {
            DeveloperDashboardScreen(
                viewModel = callViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToLogs = { navController.navigate(Screen.DeveloperLogs.route) }
            )
        }

        composable(Screen.DeveloperLogs.route) {
            SyncLogsScreen(
                viewModel = callViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    onCallClick: (Long) -> Unit,
    onSettingsClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf<Screen.Tab>(Screen.Tab.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CallVault", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                val tabs = listOf(
                    Screen.Tab.Dashboard,
                    Screen.Tab.Logs,
                    Screen.Tab.Recordings
                )
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            Screen.Tab.Dashboard -> DashboardScreen(
                callViewModel = callViewModel,
                analyticsViewModel = analyticsViewModel,
                onViewAllLogsClick = { selectedTab = Screen.Tab.Logs },
                onCallClick = onCallClick,
                modifier = modifier
            )
            Screen.Tab.Logs -> CallLogsScreen(
                viewModel = callViewModel,
                onCallClick = onCallClick,
                modifier = modifier
            )
            Screen.Tab.Recordings -> RecordingManagerScreen(
                viewModel = callViewModel,
                onCallClick = onCallClick,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptInTopAppBar(
    title: String,
    onBackClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}
