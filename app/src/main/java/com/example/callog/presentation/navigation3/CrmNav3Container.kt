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
import com.example.callog.presentation.screens.calls.CallsHubScreen
import com.example.callog.presentation.screens.contacts.ContactDetailsScreen
import com.example.callog.presentation.screens.contacts.ContactsScreen
import com.example.callog.presentation.screens.crm.CrmHubScreen
import com.example.callog.presentation.screens.insights.InsightsHubScreen
import com.example.callog.presentation.screens.meetings.MeetingDetailsScreen
import com.example.callog.presentation.screens.meetings.ScheduleMeetingScreen
import com.example.callog.presentation.screens.recordings.RecordingManagerScreen
import com.example.callog.presentation.screens.tasks.CreateTaskScreen
import com.example.callog.presentation.screens.tasks.TaskDetailsScreen
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

enum class CrmBottomTab(
    val title: String,
    val icon: ImageVector
) {
    CALLS("Calls", Icons.Default.Phone),
    CONTACTS("Contacts", Icons.Default.People),
    RECORDINGS("Recordings", Icons.Default.GraphicEq),
    ACTIVITIES("CRM", Icons.Default.Assignment),
    INSIGHTS("Insights", Icons.Default.Tune)
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
            CrmBottomTab.CALLS to Nav3Key.CrmTab.Calls,
            CrmBottomTab.CONTACTS to Nav3Key.Contacts.ContactList,
            CrmBottomTab.RECORDINGS to Nav3Key.CrmTab.Recordings,
            CrmBottomTab.ACTIVITIES to Nav3Key.CrmTab.Dashboard,
            CrmBottomTab.INSIGHTS to Nav3Key.CrmTab.Settings
        )
    }

    val multiStack = rememberNav3MultiBackStack(
        initialTab = CrmBottomTab.CALLS,
        tabRoots = tabRoots
    )

    // BackHandler: If active tab has items to pop, pop it; otherwise, if not on CALLS, return to CALLS tab.
    BackHandler(enabled = true) {
        if (!multiStack.pop()) {
            if (multiStack.selectedTab != CrmBottomTab.CALLS) {
                multiStack.selectTab(CrmBottomTab.CALLS)
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
                // --- Calls Hub (History, Keypad / Dialer, Starred Favorites) ---
                is Nav3Key.CrmTab.Calls -> {
                    CallsHubScreen(
                        viewModel = callViewModel,
                        onCallClick = onCallClick,
                        onContactClick = { canonicalId ->
                            multiStack.selectTab(CrmBottomTab.CONTACTS)
                            multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = canonicalId))
                        }
                    )
                }

                is Nav3Key.CrmTab.CallLogs -> {
                    com.example.callog.presentation.screens.logs.CallLogsScreen(
                        viewModel = callViewModel,
                        onCallClick = onCallClick,
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
                            multiStack.selectTab(CrmBottomTab.ACTIVITIES)
                            multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting(initialContactId = cid))
                        },
                        onCreateTaskClick = { cid ->
                            multiStack.selectTab(CrmBottomTab.ACTIVITIES)
                            multiStack.navigate(Nav3Key.Task.CreateTask(initialContactId = cid))
                        }
                    )
                }

                // --- Recordings Flow ---
                is Nav3Key.CrmTab.Recordings -> {
                    RecordingManagerScreen(
                        viewModel = callViewModel,
                        onCallClick = onCallClick
                    )
                }

                // --- CRM Activities Hub (Dashboard, Tasks, Meetings) ---
                is Nav3Key.CrmTab.Dashboard -> {
                    CrmHubScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        onViewAllLogsClick = {
                            multiStack.selectTab(CrmBottomTab.CALLS)
                        },
                        onCallClick = onCallClick,
                        onCreateTaskClick = {
                            multiStack.navigate(Nav3Key.Task.CreateTask())
                        },
                        onTaskClick = { taskId ->
                            multiStack.navigate(Nav3Key.Task.TaskDetails(taskId = taskId))
                        },
                        onScheduleMeetingClick = {
                            multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting())
                        },
                        onMeetingClick = { meetingId ->
                            multiStack.navigate(Nav3Key.Meeting.MeetingDetails(meetingId = meetingId))
                        }
                    )
                }

                // --- Task Sub-Flows ---
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

                // --- Meeting Sub-Flows ---
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

                // --- Insights & Settings Flow (Analytics & System Settings) ---
                is Nav3Key.CrmTab.Settings -> {
                    InsightsHubScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onNavigateToDeveloperDashboard = onNavigateToDeveloperDashboard
                    )
                }

                is Nav3Key.CrmTab.DeveloperLogs -> {
                    com.example.callog.presentation.screens.developer.SyncLogsScreen(
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() }
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
