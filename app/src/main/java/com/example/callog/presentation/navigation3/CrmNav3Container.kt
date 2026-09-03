package com.example.callog.presentation.navigation3

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.callog.presentation.screens.contacts.ContactDetailsScreen
import com.example.callog.presentation.screens.contacts.ContactsScreen
import com.example.callog.presentation.screens.dashboard.DashboardScreen
import com.example.callog.presentation.screens.meetings.MeetingDetailsScreen
import com.example.callog.presentation.screens.meetings.MeetingListScreen
import com.example.callog.presentation.screens.meetings.ScheduleMeetingScreen
import com.example.callog.presentation.screens.settings.SettingsScreen
import com.example.callog.presentation.screens.tasks.CreateTaskScreen
import com.example.callog.presentation.screens.tasks.TaskDetailsScreen
import com.example.callog.presentation.screens.tasks.TaskListScreen
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

enum class CrmBottomTab(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    CONTACTS("Contacts", Icons.Default.People),
    TASKS("Tasks", Icons.Default.CheckCircle),
    MEETINGS("Meetings", Icons.Default.Event),
    SETTINGS("Settings", Icons.Default.Settings)
}

/**
 * Top-level CRM Navigation 3 container with multi-backstack state.
 * Each tab preserves its complete independent backstack and scroll position.
 */
@Composable
fun CrmNav3Container(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onCallClick: (Long) -> Unit,
    onNavigateToDeveloperDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabRoots = remember {
        mapOf<CrmBottomTab, Nav3Key>(
            CrmBottomTab.DASHBOARD to Nav3Key.CrmTab.Dashboard,
            CrmBottomTab.CONTACTS to Nav3Key.Contacts.ContactList,
            CrmBottomTab.TASKS to Nav3Key.Task.TaskList,
            CrmBottomTab.MEETINGS to Nav3Key.Meeting.MeetingList,
            CrmBottomTab.SETTINGS to Nav3Key.CrmTab.Settings
        )
    }

    val multiStack = rememberNav3MultiBackStack(
        initialTab = CrmBottomTab.DASHBOARD,
        tabRoots = tabRoots
    )

    // BackHandler: If active tab has items to pop, pop it; otherwise, if not on DASHBOARD, return to DASHBOARD tab.
    BackHandler(enabled = true) {
        if (!multiStack.pop()) {
            if (multiStack.selectedTab != CrmBottomTab.DASHBOARD) {
                multiStack.selectTab(CrmBottomTab.DASHBOARD)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                CrmBottomTab.values().forEach { tab ->
                    val isSelected = multiStack.selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            multiStack.selectTab(tab, popToRootIfSelected = true)
                        },
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
        val tabModifier = modifier.padding(innerPadding)

        Nav3Display(
            backStack = multiStack.currentStack,
            modifier = tabModifier,
            enableBackHandler = false // Outer handler manages multi-tab fallback
        ) { key ->
            when (key) {
                // --- Dashboard Flow ---
                is Nav3Key.CrmTab.Dashboard -> {
                    DashboardScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        onViewAllLogsClick = {
                            multiStack.navigate(Nav3Key.CrmTab.CallLogs)
                        },
                        onCallClick = onCallClick
                    )
                }

                is Nav3Key.CrmTab.CallLogs -> {
                    com.example.callog.presentation.screens.logs.CallLogsScreen(
                        viewModel = callViewModel,
                        onCallClick = onCallClick,
                        onBackClick = { multiStack.pop() }
                    )
                }

                is Nav3Key.CrmTab.DeveloperLogs -> {
                    com.example.callog.presentation.screens.developer.SyncLogsScreen(
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Contacts Flow (Contact List -> Contact Details) ---
                is Nav3Key.Contacts.ContactList -> {
                    ContactsScreen(
                        viewModel = callViewModel,
                        onContactClick = { canonicalId ->
                            multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = canonicalId))
                        }
                    )
                }
                is Nav3Key.Contacts.ContactDetails -> {
                    ContactDetailsScreen(
                        contactId = key.contactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onScheduleMeetingClick = { cid ->
                            multiStack.selectTab(CrmBottomTab.MEETINGS)
                            multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting(initialContactId = cid))
                        },
                        onCreateTaskClick = { cid ->
                            multiStack.selectTab(CrmBottomTab.TASKS)
                            multiStack.navigate(Nav3Key.Task.CreateTask(initialContactId = cid))
                        }
                    )
                }

                // --- Task Flows ---
                is Nav3Key.Task.TaskList -> {
                    TaskListScreen(
                        onCreateTaskClick = {
                            multiStack.navigate(Nav3Key.Task.CreateTask())
                        },
                        onTaskClick = { taskId ->
                            multiStack.navigate(Nav3Key.Task.TaskDetails(taskId = taskId))
                        }
                    )
                }
                is Nav3Key.Task.CreateTask -> {
                    CreateTaskScreen(
                        initialContactId = key.initialContactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onTaskCreated = { multiStack.pop() }
                    )
                }
                is Nav3Key.Task.TaskDetails -> {
                    TaskDetailsScreen(
                        taskId = key.taskId,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Meeting Flows ---
                is Nav3Key.Meeting.MeetingList -> {
                    MeetingListScreen(
                        onScheduleMeetingClick = {
                            multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting())
                        },
                        onMeetingClick = { meetingId ->
                            multiStack.navigate(Nav3Key.Meeting.MeetingDetails(meetingId = meetingId))
                        }
                    )
                }
                is Nav3Key.Meeting.ScheduleMeeting -> {
                    ScheduleMeetingScreen(
                        initialContactId = key.initialContactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onMeetingScheduled = { multiStack.pop() }
                    )
                }
                is Nav3Key.Meeting.MeetingDetails -> {
                    MeetingDetailsScreen(
                        meetingId = key.meetingId,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Settings Flow ---
                is Nav3Key.CrmTab.Settings -> {
                    SettingsScreen(
                        viewModel = callViewModel,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onNavigateToDeveloperDashboard = onNavigateToDeveloperDashboard
                    )
                }

                else -> {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Text("Unknown Destination: $key")
                    }
                }
            }
        }
    }
}
