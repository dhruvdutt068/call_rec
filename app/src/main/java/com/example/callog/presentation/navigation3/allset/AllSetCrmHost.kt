package com.example.callog.presentation.navigation3.allset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.callog.presentation.screens.contacts.AddFeedbackScreen
import com.example.callog.presentation.screens.contacts.ContactDetailsScreen
import com.example.callog.presentation.screens.contacts.EditContactScreen
import com.example.callog.presentation.screens.contacts.GlobalSearchScreen
import com.example.callog.presentation.screens.contacts.SearchResultsScreen
import com.example.callog.presentation.screens.contacts.WhatsAppScreen
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

/**
 * Top-level AllSet CRM Host Composable.
 * Orchestrates multi-stack Navigation 3 for Home, Contacts, Tasks, Meetings, and Settings.
 */
@Composable
fun AllSetCrmHost(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onCallClick: (Long) -> Unit,
    onNavigateToDeveloperDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val multiStack = rememberAllSetMultiBackStack(initialTab = AllSetCrmTab.HOME)

    BackHandler(enabled = true) {
        multiStack.pop()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                AllSetCrmTab.values().forEach { tab ->
                    val (title, icon) = when (tab) {
                        AllSetCrmTab.HOME -> "Home" to Icons.Default.Home
                        AllSetCrmTab.CONTACTS -> "Contacts" to Icons.Default.People
                        AllSetCrmTab.TASKS -> "Tasks" to Icons.Default.CheckCircle
                        AllSetCrmTab.MEETINGS -> "Meetings" to Icons.Default.Event
                        AllSetCrmTab.SETTINGS -> "Settings" to Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = multiStack.selectedTab == tab,
                        onClick = { multiStack.selectTab(tab, popToRootIfReselected = true) },
                        icon = { Icon(icon, contentDescription = title) },
                        label = { Text(title) },
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
        val hostModifier = modifier.padding(innerPadding)

        AllSetNavDisplay(
            backStack = multiStack.currentStack,
            modifier = hostModifier,
            enableBackHandler = false
        ) { key ->
            when (key) {
                // --- Home Tab ---
                is AllSetNavKey.Home -> {
                    DashboardScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        onViewAllLogsClick = { multiStack.navigate(AllSetNavKey.CallLogs) },
                        onCallClick = onCallClick
                    )
                }

                is AllSetNavKey.CallLogs -> {
                    com.example.callog.presentation.screens.logs.CallLogsScreen(
                        viewModel = callViewModel,
                        onCallClick = onCallClick,
                        onBackClick = { multiStack.pop() }
                    )
                }

                is AllSetNavKey.DeveloperLogs -> {
                    com.example.callog.presentation.screens.developer.SyncLogsScreen(
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Contacts Flow ---
                is AllSetNavKey.Contacts.List -> {
                    AdaptiveContactsLayout(
                        viewModel = callViewModel,
                        isExpandedScreen = false,
                        selectedContactId = null,
                        onContactClick = { contactId ->
                            multiStack.navigate(AllSetNavKey.Contacts.ContactDetails(contactId))
                        },
                        onScheduleMeetingClick = { contactId ->
                            multiStack.selectTab(AllSetCrmTab.MEETINGS)
                            multiStack.navigate(AllSetNavKey.Meetings.ScheduleMeeting(contactId))
                        },
                        onCreateTaskClick = { contactId ->
                            multiStack.selectTab(AllSetCrmTab.TASKS)
                            multiStack.navigate(AllSetNavKey.Tasks.CreateTask(contactId))
                        }
                    )
                }

                is AllSetNavKey.Contacts.GlobalSearch -> {
                    GlobalSearchScreen(
                        initialQuery = key.initialQuery,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onSearchSubmitted = { query ->
                            multiStack.navigate(AllSetNavKey.Contacts.SearchResults(query))
                        },
                        onContactSelected = { contactId ->
                            multiStack.navigate(AllSetNavKey.Contacts.ContactDetails(contactId))
                        }
                    )
                }

                is AllSetNavKey.Contacts.SearchResults -> {
                    SearchResultsScreen(
                        query = key.query,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onContactSelected = { contactId ->
                            multiStack.navigate(AllSetNavKey.Contacts.ContactDetails(contactId))
                        }
                    )
                }

                is AllSetNavKey.Contacts.ContactDetails -> {
                    ContactDetailsScreen(
                        contactId = key.contactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onScheduleMeetingClick = { contactId ->
                            multiStack.selectTab(AllSetCrmTab.MEETINGS)
                            multiStack.navigate(AllSetNavKey.Meetings.ScheduleMeeting(contactId))
                        },
                        onCreateTaskClick = { contactId ->
                            multiStack.selectTab(AllSetCrmTab.TASKS)
                            multiStack.navigate(AllSetNavKey.Tasks.CreateTask(contactId))
                        }
                    )
                }

                is AllSetNavKey.Contacts.SubSection.WhatsApp -> {
                    WhatsAppScreen(
                        contactId = key.contactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() }
                    )
                }

                is AllSetNavKey.Contacts.EditContact -> {
                    EditContactScreen(
                        contactId = key.contactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onSaveSuccess = { updatedName ->
                            multiStack.resultManager.setResult("contact_updated_${key.contactId}", updatedName)
                            multiStack.pop()
                        }
                    )
                }

                is AllSetNavKey.Contacts.AddFeedback -> {
                    AddFeedbackScreen(
                        contactId = key.contactId,
                        onBackClick = { multiStack.pop() },
                        onFeedbackSubmitted = { rating, notes ->
                            multiStack.resultManager.setResult("feedback_${key.contactId}", rating to notes)
                            multiStack.pop()
                        }
                    )
                }

                // --- Tasks Flow ---
                is AllSetNavKey.Tasks.List -> {
                    TaskListScreen(
                        onCreateTaskClick = { multiStack.navigate(AllSetNavKey.Tasks.CreateTask()) },
                        onTaskClick = { taskId -> multiStack.navigate(AllSetNavKey.Tasks.TaskDetails(taskId)) }
                    )
                }

                is AllSetNavKey.Tasks.CreateTask -> {
                    CreateTaskScreen(
                        initialContactId = key.initialContactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onTaskCreated = {
                            multiStack.resultManager.setResult("task_created", true)
                            multiStack.pop()
                        }
                    )
                }

                is AllSetNavKey.Tasks.TaskDetails -> {
                    TaskDetailsScreen(
                        taskId = key.taskId,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Meetings Flow ---
                is AllSetNavKey.Meetings.List -> {
                    MeetingListScreen(
                        onScheduleMeetingClick = { multiStack.navigate(AllSetNavKey.Meetings.ScheduleMeeting()) },
                        onMeetingClick = { meetingId -> multiStack.navigate(AllSetNavKey.Meetings.MeetingDetails(meetingId)) }
                    )
                }

                is AllSetNavKey.Meetings.ScheduleMeeting -> {
                    ScheduleMeetingScreen(
                        initialContactId = key.initialContactId,
                        viewModel = callViewModel,
                        onBackClick = { multiStack.pop() },
                        onMeetingScheduled = {
                            multiStack.resultManager.setResult("meeting_scheduled", true)
                            multiStack.pop()
                        }
                    )
                }

                is AllSetNavKey.Meetings.MeetingDetails -> {
                    MeetingDetailsScreen(
                        meetingId = key.meetingId,
                        onBackClick = { multiStack.pop() }
                    )
                }

                // --- Settings Tab ---
                is AllSetNavKey.SettingsTab -> {
                    SettingsScreen(
                        viewModel = callViewModel,
                        darkTheme = darkTheme,
                        onDarkThemeChange = onDarkThemeChange,
                        onNavigateToDeveloperDashboard = onNavigateToDeveloperDashboard
                    )
                }

                else -> {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Text("Destination: $key")
                    }
                }
            }
        }
    }
}
