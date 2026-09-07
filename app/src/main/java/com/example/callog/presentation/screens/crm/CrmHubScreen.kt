package com.example.callog.presentation.screens.crm

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
import com.example.callog.presentation.screens.dashboard.DashboardScreen
import com.example.callog.presentation.screens.meetings.MeetingListScreen
import com.example.callog.presentation.screens.tasks.TaskListScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

enum class CrmSubTab(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    TASKS("Tasks", Icons.Default.CheckCircle),
    MEETINGS("Meetings", Icons.Default.Event)
}

@Composable
fun CrmHubScreen(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    onViewAllLogsClick: () -> Unit,
    onCallClick: (Long) -> Unit,
    onCreateTaskClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onScheduleMeetingClick: () -> Unit,
    onMeetingClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(CrmSubTab.DASHBOARD) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Top TabRow for CRM sub-features
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
                        color = AllSetBlue
                    )
                }
            ) {
                CrmSubTab.values().forEach { tab ->
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
                                    tint = if (isSelected) AllSetLavender else Slate400
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
                CrmSubTab.DASHBOARD -> {
                    DashboardScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        onViewAllLogsClick = onViewAllLogsClick,
                        onCallClick = onCallClick
                    )
                }
                CrmSubTab.TASKS -> {
                    TaskListScreen(
                        onCreateTaskClick = onCreateTaskClick,
                        onTaskClick = onTaskClick
                    )
                }
                CrmSubTab.MEETINGS -> {
                    MeetingListScreen(
                        onScheduleMeetingClick = onScheduleMeetingClick,
                        onMeetingClick = onMeetingClick
                    )
                }
            }
        }
    }
}
