package com.example.callog.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permission : Screen("permission")
    object Onboarding : Screen("onboarding")
    object Main : Screen("main")
    object CallDetails : Screen("call_details/{callId}") {
        fun createRoute(callId: Long) = "call_details/$callId"
    }
    object Settings : Screen("settings")
    object DeveloperDashboard : Screen("developer_dashboard")
    object DeveloperLogs : Screen("developer_logs")

    // Tabs for Bottom Navigation under Main Screen
    sealed class Tab(val tabRoute: String, val title: String, val icon: ImageVector) {
        object Dashboard : Tab("tab_dashboard", "Dashboard", Icons.Default.Dashboard)
        object Logs : Tab("tab_logs", "Logs", Icons.Default.History)
        object Recordings : Tab("tab_recordings", "Recordings", Icons.Default.Mic)
    }
}