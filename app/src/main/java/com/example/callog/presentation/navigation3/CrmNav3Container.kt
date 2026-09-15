package com.example.callog.presentation.navigation3

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.core.telecom.TelecomRoleHelper
import com.example.callog.di.TelecomEntryPoint
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.screens.analytics.AnalyticsScreen
import com.example.callog.presentation.screens.contacts.ContactDetailsScreen
import com.example.callog.presentation.screens.contacts.ContactsScreen
import com.example.callog.presentation.screens.crm.CrmHubScreen
import com.example.callog.presentation.screens.developer.SyncLogsScreen
import com.example.callog.presentation.screens.dialer.DialerScreen
import com.example.callog.presentation.screens.logs.CallLogsScreen
import com.example.callog.presentation.screens.meetings.MeetingDetailsScreen
import com.example.callog.presentation.screens.meetings.MeetingListScreen
import com.example.callog.presentation.screens.meetings.ScheduleMeetingScreen
import com.example.callog.presentation.screens.recordings.RecordingManagerScreen
import com.example.callog.presentation.screens.settings.CrmRingtoneSettingsScreen
import com.example.callog.presentation.screens.settings.SettingsScreen
import com.example.callog.presentation.screens.tasks.CreateTaskScreen
import com.example.callog.presentation.screens.tasks.TaskDetailsScreen
import com.example.callog.presentation.screens.tasks.TaskListScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

enum class CrmBottomTab(
    val title: String,
    val icon: ImageVector
) {
    DIALER("Dialer", Icons.Default.Dialpad),
    CALL_LOGS("Call Logs", Icons.Default.History),
    CONTACTS("Contacts", Icons.Default.People),
    RECORDINGS("Recordings", Icons.Default.GraphicEq),
    CRM_HUB("CRM Hub", Icons.Default.Dashboard)
}

/**
 * Top-level Client Navigation container with multi-backstack state and slide-out Menu Drawer:
 * 1. Dialer (T9 keypad, speed dial, SIM router)
 * 2. Call Logs (Filterable call logs, audio & sync badges)
 * 3. Contacts (Global CRM & local phonebook)
 * 4. Recordings (Audio vault with inline player)
 * 5. CRM Hub (Pipeline, Tasks, Meetings, Analytics)
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val telecomEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, TelecomEntryPoint::class.java)
    }
    val ringtoneRepository = remember(telecomEntryPoint) {
        telecomEntryPoint.ringtoneRepository()
    }

    var isDefaultDialer by remember { mutableStateOf(TelecomRoleHelper.isDefaultDialer(context)) }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = TelecomRoleHelper.isDefaultDialer(context)
    }

    val activeSims by callViewModel.activeSims.collectAsState()
    val isSyncing by callViewModel.isSyncing.collectAsState()

    val tabRoots = remember {
        mapOf<CrmBottomTab, Nav3Key>(
            CrmBottomTab.DIALER to Nav3Key.CrmTab.Dialer,
            CrmBottomTab.CALL_LOGS to Nav3Key.CrmTab.CallLogs,
            CrmBottomTab.CONTACTS to Nav3Key.Contacts.ContactList,
            CrmBottomTab.RECORDINGS to Nav3Key.CrmTab.Recordings,
            CrmBottomTab.CRM_HUB to Nav3Key.CrmTab.Dashboard
        )
    }

    val multiStack = rememberNav3MultiBackStack(
        initialTab = CrmBottomTab.CALL_LOGS,
        tabRoots = tabRoots
    )

    val openDrawer: () -> Unit = {
        coroutineScope.launch { drawerState.open() }
    }
    val closeDrawer: () -> Unit = {
        coroutineScope.launch { drawerState.close() }
    }

    // BackHandler: If drawer is open, close it; otherwise pop active tab stack; fallback to CALL_LOGS.
    BackHandler(enabled = true) {
        if (drawerState.isOpen) {
            closeDrawer()
        } else if (!multiStack.pop()) {
            if (multiStack.selectedTab != CrmBottomTab.CALL_LOGS) {
                multiStack.selectTab(CrmBottomTab.CALL_LOGS)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Slate900,
                drawerContentColor = Slate50,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 1. Drawer Header Banner
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AllSetBlue, AllSetLavender, Slate800)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneInTalk,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Callog Enterprise",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Call Vault & CRM Hub",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }

                                if (activeSims.isNotEmpty()) {
                                    Text(
                                        text = "Active Lines: ${activeSims.joinToString(", ") { it.displayName }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }

                        // 2. Default Dialer Role Status Card
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDefaultDialer) Green500.copy(alpha = 0.12f) else Amber500.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDefaultDialer) Green500.copy(alpha = 0.3f) else Amber500.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isDefaultDialer) Icons.Default.VerifiedUser else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (isDefaultDialer) Green500 else Amber500,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (isDefaultDialer) "Default Phone App" else "Default Role Required",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDefaultDialer) Green500 else Amber500
                                    )
                                }

                                if (!isDefaultDialer) {
                                    FilledTonalButton(
                                        onClick = {
                                            val intent = TelecomRoleHelper.createRequestDialerRoleIntent(context)
                                            if (intent != null) {
                                                roleLauncher.launch(intent)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Amber500,
                                            contentColor = Slate950
                                        )
                                    ) {
                                        Text("Enable", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Slate800)

                        // 3. Navigation Drawer Menu Items
                        Text(
                            text = "Core Telephony",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Dialpad, contentDescription = null) },
                            label = { Text("Dialer & Keypad") },
                            selected = multiStack.selectedTab == CrmBottomTab.DIALER,
                            onClick = {
                                multiStack.selectTab(CrmBottomTab.DIALER, popToRootIfSelected = true)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                selectedIconColor = Teal300,
                                selectedTextColor = Slate50,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.History, contentDescription = null) },
                            label = { Text("Call Logs & History") },
                            selected = multiStack.selectedTab == CrmBottomTab.CALL_LOGS,
                            onClick = {
                                multiStack.selectTab(CrmBottomTab.CALL_LOGS, popToRootIfSelected = true)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                selectedIconColor = Teal300,
                                selectedTextColor = Slate50,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.People, contentDescription = null) },
                            label = { Text("Contacts & Leads") },
                            selected = multiStack.selectedTab == CrmBottomTab.CONTACTS,
                            onClick = {
                                multiStack.selectTab(CrmBottomTab.CONTACTS, popToRootIfSelected = true)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                selectedIconColor = Teal300,
                                selectedTextColor = Slate50,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                            label = { Text("Recording Vault") },
                            selected = multiStack.selectedTab == CrmBottomTab.RECORDINGS,
                            onClick = {
                                multiStack.selectTab(CrmBottomTab.RECORDINGS, popToRootIfSelected = true)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                selectedIconColor = Teal300,
                                selectedTextColor = Slate50,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Slate800)

                        Text(
                            text = "CRM & Productivity",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                            label = { Text("Sales Pipeline Hub") },
                            selected = multiStack.selectedTab == CrmBottomTab.CRM_HUB,
                            onClick = {
                                multiStack.selectTab(CrmBottomTab.CRM_HUB, popToRootIfSelected = true)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = AllSetBlue.copy(alpha = 0.2f),
                                selectedIconColor = AllSetLavender,
                                selectedTextColor = Slate50,
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Analytics, contentDescription = null) },
                            label = { Text("Call Analytics & Reports") },
                            selected = false,
                            onClick = {
                                multiStack.navigate(Nav3Key.CrmTab.Analytics)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Event, contentDescription = null) },
                            label = { Text("Meetings & Schedule") },
                            selected = false,
                            onClick = {
                                multiStack.navigate(Nav3Key.Meeting.MeetingList)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.TaskAlt, contentDescription = null) },
                            label = { Text("Tasks & Follow-ups") },
                            selected = false,
                            onClick = {
                                multiStack.navigate(Nav3Key.Task.TaskList)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        HorizontalDivider(color = Slate800)

                        Text(
                            text = "Preferences & Tools",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                            label = { Text("CRM Ringtone Rules") },
                            selected = false,
                            onClick = {
                                multiStack.navigate(Nav3Key.CrmTab.RingtoneSettings)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings & Sync") },
                            selected = false,
                            onClick = {
                                multiStack.navigate(Nav3Key.CrmTab.Settings)
                                closeDrawer()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Code, contentDescription = null) },
                            label = { Text("Developer Diagnostics") },
                            selected = false,
                            onClick = {
                                closeDrawer()
                                onNavigateToDeveloperDashboard()
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedIconColor = Slate400,
                                unselectedTextColor = Slate300
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // 4. Drawer Footer
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = Slate800)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DarkMode,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Dark Theme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate300
                                )
                            }
                            Switch(
                                checked = darkTheme,
                                onCheckedChange = onDarkThemeChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Teal300,
                                    checkedTrackColor = Teal500,
                                    uncheckedThumbColor = Slate400,
                                    uncheckedTrackColor = Slate800
                                )
                            )
                        }

                        Button(
                            onClick = { callViewModel.syncLogs() },
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal500.copy(alpha = 0.2f),
                                contentColor = Teal300
                            )
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Teal300
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing Vault...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now")
                            }
                        }
                    }
                }
            }
        }
    ) {
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
                    // --- 1. Dialer Screen ---
                    is Nav3Key.CrmTab.Dialer -> {
                        DialerScreen(
                            onNavigateToContact = { contactId ->
                                multiStack.selectTab(CrmBottomTab.CONTACTS)
                                multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = contactId))
                            },
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 2. Call Logs Screen ---
                    is Nav3Key.CrmTab.CallLogs, is Nav3Key.CrmTab.Calls -> {
                        CallLogsScreen(
                            viewModel = callViewModel,
                            onCallClick = onCallClick,
                            onBackClick = null,
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 3. Contacts Screen (Global Supabase & Local) ---
                    is Nav3Key.Contacts.ContactList -> {
                        ContactsScreen(
                            viewModel = callViewModel,
                            onContactClick = { canonicalId ->
                                multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = canonicalId))
                            },
                            onMenuClick = openDrawer
                        )
                    }
                    is Nav3Key.Contacts.ContactDetails -> {
                        ContactDetailsScreen(
                            contactId = key.contactId,
                            viewModel = callViewModel,
                            onBackClick = { multiStack.pop() }
                        )
                    }

                    // --- 4. Recording List Screen ---
                    is Nav3Key.CrmTab.Recordings -> {
                        RecordingManagerScreen(
                            viewModel = callViewModel,
                            onCallClick = onCallClick,
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 5. Whole CRM UI (Pipeline, Overview, Tasks, Meetings, Analytics) ---
                    is Nav3Key.CrmTab.Dashboard -> {
                        CrmHubScreen(
                            callViewModel = callViewModel,
                            analyticsViewModel = analyticsViewModel,
                            onViewAllLogsClick = {
                                multiStack.selectTab(CrmBottomTab.CALL_LOGS)
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
                            },
                            onContactClick = { contactId ->
                                multiStack.selectTab(CrmBottomTab.CONTACTS)
                                multiStack.navigate(Nav3Key.Contacts.ContactDetails(contactId = contactId))
                            },
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 6. Analytics Standalone Screen ---
                    is Nav3Key.CrmTab.Analytics -> {
                        AnalyticsScreen(
                            viewModel = analyticsViewModel,
                            onBackClick = { multiStack.pop() },
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 7. CRM Ringtone Policy Settings Screen ---
                    is Nav3Key.CrmTab.RingtoneSettings -> {
                        CrmRingtoneSettingsScreen(
                            ringtoneRepository = ringtoneRepository,
                            onBackClick = { multiStack.pop() },
                            onMenuClick = openDrawer
                        )
                    }

                    // --- 8. Settings Screen ---
                    is Nav3Key.CrmTab.Settings -> {
                        SettingsScreen(
                            viewModel = callViewModel,
                            darkTheme = darkTheme,
                            onDarkThemeChange = onDarkThemeChange,
                            onNavigateToDeveloperDashboard = onNavigateToDeveloperDashboard,
                            onNavigateToRingtoneSettings = {
                                multiStack.navigate(Nav3Key.CrmTab.RingtoneSettings)
                            }
                        )
                    }

                    // --- Task Sub-Flows ---
                    is Nav3Key.Task.TaskList -> {
                        TaskListScreen(
                            onCreateTaskClick = { multiStack.navigate(Nav3Key.Task.CreateTask()) },
                            onTaskClick = { taskId -> multiStack.navigate(Nav3Key.Task.TaskDetails(taskId = taskId)) }
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

                    // --- Meeting Sub-Flows ---
                    is Nav3Key.Meeting.MeetingList -> {
                        MeetingListScreen(
                            onScheduleMeetingClick = { multiStack.navigate(Nav3Key.Meeting.ScheduleMeeting()) },
                            onMeetingClick = { meetingId -> multiStack.navigate(Nav3Key.Meeting.MeetingDetails(meetingId = meetingId)) }
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

                    // --- Developer Logs Flow ---
                    is Nav3Key.CrmTab.DeveloperLogs -> {
                        SyncLogsScreen(
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
}
