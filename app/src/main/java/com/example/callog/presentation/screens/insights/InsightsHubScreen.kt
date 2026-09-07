package com.example.callog.presentation.screens.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.screens.analytics.AnalyticsScreen
import com.example.callog.presentation.screens.settings.SettingsScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

enum class InsightsSubTab(
    val title: String,
    val icon: ImageVector
) {
    ANALYTICS("Analytics", Icons.Default.BarChart),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun InsightsHubScreen(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onNavigateToDeveloperDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(InsightsSubTab.ANALYTICS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Top TabRow
        Surface(
            color = Slate900,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab.ordinal,
                containerColor = Slate900,
                contentColor = Slate50,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab.ordinal]),
                        color = Teal500
                    )
                }
            ) {
                InsightsSubTab.values().forEach { tab ->
                    val isSelected = selectedSubTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSubTab = tab },
                        text = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) Teal300 else Slate400
                                )
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Slate50 else Slate400
                                )
                            }
                        }
                    )
                }
            }
        }

        // Sub-screen content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                InsightsSubTab.ANALYTICS -> {
                    AnalyticsScreen(
                        viewModel = analyticsViewModel
                    )
                }
                InsightsSubTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = callViewModel,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onNavigateToDeveloperDashboard = onNavigateToDeveloperDashboard
                    )
                }
            }
        }
    }
}
