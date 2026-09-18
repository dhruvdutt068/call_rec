package com.example.callog.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Permission : Screen("permission")
    object Onboarding : Screen("onboarding")
    object Auth : Screen("auth")
    object Main : Screen("main")
    object CallDetails : Screen("call_details/{callId}") {
        fun createRoute(callId: Long) = "call_details/$callId"
    }
    object Settings : Screen("settings")
    object DeveloperDashboard : Screen("developer_dashboard")
    object DeveloperLogs : Screen("developer_logs")
    object RecordingDiagnostics : Screen("recording_diagnostics")
    object CallSimulator : Screen("call_simulator")
    object EnvironmentPresets : Screen("environment_presets")

    // Tabs for Bottom Navigation under Main Screen
    sealed class Tab(val tabRoute: String, val title: String, val icon: ImageVector) {
        object Logs : Tab("tab_logs", "Call Logs", Icons.Default.History)
        object Contacts : Tab("tab_contacts", "Contacts", Icons.Default.People)
        object Recordings : Tab("tab_recordings", "Recordings", Icons.Default.GraphicEq)
        object Settings : Tab("tab_settings", "Settings", Icons.Default.Settings)
    }
}