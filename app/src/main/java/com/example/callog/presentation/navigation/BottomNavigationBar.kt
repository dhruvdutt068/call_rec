package com.example.callog.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem("home", "Calls", Icons.Default.Call)

    object Analytics : BottomNavItem(
        "analytics",
        "Analytics",
        Icons.Default.BarChart
    )

    object Favorites : BottomNavItem(
        "favorites",
        "Favorites",
        Icons.Default.Favorite
    )

    object Settings : BottomNavItem(
        "settings",
        "Settings",
        Icons.Default.Settings
    )
}