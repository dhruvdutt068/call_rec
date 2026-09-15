package com.example.callog.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object CallLogs : BottomNavItem("call_logs", "Call Logs", Icons.Default.History)
    object Contacts : BottomNavItem("contacts", "Contacts", Icons.Default.People)
    object Recordings : BottomNavItem("recordings", "Recordings", Icons.Default.GraphicEq)
    object Settings : BottomNavItem("settings", "Settings", Icons.Default.Settings)
}