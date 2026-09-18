package com.example.callog.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDurationString
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.*
import com.example.callog.presentation.components.pullrefresh.ElasticPullRefreshLayout
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

@Composable
fun DashboardScreen(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    onViewAllLogsClick: () -> Unit,
    onCallClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val callLogs by callViewModel.callLogs.collectAsState()
    val favoriteLogs by callViewModel.favoriteLogs.collectAsState()
    val pendingReminders by callViewModel.pendingReminders.collectAsState()
    val analyticsState by analyticsViewModel.uiState.collectAsState()
    val isSyncing by callViewModel.isSyncing.collectAsState()
    val syncProgress by callViewModel.syncProgress.collectAsState()
    val deviceOwnerName by callViewModel.deviceOwnerName.collectAsState()
    
    val selectedSimId by callViewModel.selectedSimId.collectAsState()
    val selectedSimCarrier by callViewModel.selectedSimCarrier.collectAsState()
    val selectedSimDisplayName by callViewModel.selectedSimDisplayName.collectAsState()
    val selectedSimPhoneNumber by callViewModel.selectedSimPhoneNumber.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var previousSyncingState by remember { mutableStateOf(false) }

    LaunchedEffect(isSyncing) {
        if (previousSyncingState && !isSyncing) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Logs and recordings are synced to Firestore",
                    duration = SnackbarDuration.Short
                )
            }
        }
        previousSyncingState = isSyncing
    }

    val activeFilter by callViewModel.callTypeFilter.collectAsState()

    val filterOptions = listOf(
        "ALL" to "All",
        "INCOMING" to "Incoming",
        "OUTGOING" to "Outgoing",
        "MISSED" to "Missed",
        "REJECTED" to "Rejected",
        "RECORDED" to "Recorded"
    )

    val recentCalls = callLogs.take(4)
    val favoriteContacts = favoriteLogs.distinctBy { it.number }.take(10)

    Box(modifier = modifier.fillMaxSize()) {
        ElasticPullRefreshLayout(
            isRefreshing = isSyncing,
            onRefresh = { callViewModel.syncLogs() },
            modifier = Modifier.fillMaxSize()
        ) {
            DashboardContent(
                deviceOwnerName = deviceOwnerName,
                selectedSimId = selectedSimId,
                selectedSimCarrier = selectedSimCarrier,
                selectedSimDisplayName = selectedSimDisplayName,
                selectedSimPhoneNumber = selectedSimPhoneNumber,
                isSyncing = isSyncing,
                syncProgress = syncProgress,
                onSyncClick = { callViewModel.syncLogs() },
                analyticsState = analyticsState,
                favoriteContacts = favoriteContacts,
                pendingReminders = pendingReminders,
                onCompleteReminder = { callViewModel.markReminderCompleted(it) },
                onDeleteReminder = { callViewModel.deleteReminder(it) },
                recentCalls = recentCalls,
                activeFilter = activeFilter ?: "ALL",
                filterOptions = filterOptions,
                onFilterSelect = { callViewModel.setCallTypeFilter(it) },
                onViewAllLogsClick = onViewAllLogsClick,
                onCallClick = onCallClick,
                onFavoriteToggle = { callViewModel.toggleFavorite(it) }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun DashboardContent(
    deviceOwnerName: String,
    selectedSimId: Int,
    selectedSimCarrier: String,
    selectedSimDisplayName: String,
    selectedSimPhoneNumber: String,
    isSyncing: Boolean,
    syncProgress: String?,
    onSyncClick: () -> Unit,
    analyticsState: com.example.callog.presentation.viewmodel.AnalyticsUiState,
    favoriteContacts: List<CallLogEntry>,
    pendingReminders: List<ReminderWithCall>,
    onCompleteReminder: (Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    recentCalls: List<CallLogEntry>,
    activeFilter: String,
    filterOptions: List<Pair<String, String>>,
    onFilterSelect: (String) -> Unit,
    onViewAllLogsClick: () -> Unit,
    onCallClick: (Long) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome and Header Hero
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (deviceOwnerName.isNotEmpty()) "Hello, $deviceOwnerName" else "Call Intelligence",
                    style = CallogTypography.heroTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(2.dp))

                val simLabel = if (selectedSimDisplayName.isNotEmpty()) selectedSimDisplayName else selectedSimCarrier
                val isSimValid = selectedSimId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
                val simDetailText = if (isSimValid) {
                    "Business Line: $simLabel ($selectedSimPhoneNumber)"
                } else {
                    "SIM Config required - Sync suspended"
                }
                
                Text(
                    text = simDetailText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSimValid) AllSetTeal else Red500,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                shape = CallogShapes.interactive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSyncing) MaterialTheme.colorScheme.surfaceVariant else AllSetBlue,
                    contentColor = if (isSyncing) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSyncing) (syncProgress ?: "Syncing...") else "Sync Vault",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Expressive KPI Statistics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Logged Calls",
                value = analyticsState.totalCalls.toString(),
                icon = Icons.Default.Call,
                iconColor = AllSetBlue,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Avg Call Duration",
                value = analyticsState.avgDurationSeconds.toDurationString(),
                icon = Icons.Default.HourglassEmpty,
                iconColor = AllSetTeal,
                modifier = Modifier.weight(1f)
            )
        }

        // Horizontal Favorites Contacts with Tactile Avatars
        if (favoriteContacts.isNotEmpty()) {
            Column {
                Text(
                    text = "Starred Contacts",
                    style = CallogTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favoriteContacts, key = { it.id }) { contact ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(68.dp)
                                .clickable {
                                    onCallClick(contact.id)
                                }
                        ) {
                            ContactAvatar(
                                name = contact.displayName,
                                initials = contact.initials,
                                photoUri = contact.contactPhotoUri,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = contact.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Pending Reminders Section
        if (pendingReminders.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Upcoming Callback Reminders",
                    style = CallogTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                pendingReminders.forEach { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        onCompleteClick = { onCompleteReminder(reminder.reminder.id) },
                        onDeleteClick = { onDeleteReminder(reminder.reminder.id) }
                    )
                }
            }
        }

        // Recent Calls Section
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Interactions",
                    style = CallogTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onViewAllLogsClick() }
                )
            }

            // Segmented Filter Bar
            ExpressiveSegmentedButtonGroup(
                options = filterOptions.take(4).map { (key, label) ->
                    val icon = when (key) {
                        "ALL" -> Icons.Outlined.List
                        "INCOMING" -> Icons.Outlined.CallReceived
                        "OUTGOING" -> Icons.Outlined.CallMade
                        "MISSED" -> Icons.Outlined.CallMissed
                        else -> null
                    }
                    SegmentedOption(value = key, label = label, icon = icon)
                },
                selectedValue = activeFilter,
                onValueSelected = onFilterSelect
            )

            if (recentCalls.isEmpty()) {
                ExpressiveEmptyState(
                    icon = Icons.Outlined.PhoneDisabled,
                    title = "No Calls Found",
                    description = "Grant call log permissions or sync vault to see recent interactions."
                )
            } else {
                recentCalls.forEach { call ->
                    CallCard(
                        call = call,
                        onClick = { onCallClick(call.id) },
                        onFavoriteToggle = { onFavoriteToggle(call.id) }
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Dashboard Screen Default", showBackground = true)
@Composable
fun DashboardScreenPreview() {
    CallogTheme {
        DashboardContent(
            deviceOwnerName = "Dhruv Dutt",
            selectedSimId = 1,
            selectedSimCarrier = "Jio 5G",
            selectedSimDisplayName = "Work SIM",
            selectedSimPhoneNumber = "+919876543210",
            isSyncing = false,
            syncProgress = null,
            onSyncClick = {},
            analyticsState = com.example.callog.presentation.viewmodel.AnalyticsUiState(
                totalCalls = 42,
                avgDurationSeconds = 185
            ),
            favoriteContacts = listOf(
                CallLogEntry(
                    id = 1L,
                    name = "Alice Johnson",
                    number = "+919876543210",
                    duration = 120,
                    timestamp = System.currentTimeMillis(),
                    callType = "INCOMING",
                    recordingPath = null,
                    isFavorite = true,
                    notes = null,
                    tags = emptyList(),
                    contactPhotoUri = null
                )
            ),
            pendingReminders = emptyList<ReminderWithCall>(),
            onCompleteReminder = {},
            onDeleteReminder = {},
            recentCalls = listOf(
                CallLogEntry(
                    id = 1L,
                    name = "Alice Johnson",
                    number = "+919876543210",
                    duration = 125,
                    timestamp = System.currentTimeMillis() - 600000,
                    callType = "INCOMING",
                    recordingPath = "/storage/emulated/0/Recordings/Call_Alice.m4a",
                    isFavorite = false,
                    notes = null,
                    tags = listOf("Lead", "Followup"),
                    contactPhotoUri = null
                )
            ),
            activeFilter = "ALL",
            filterOptions = listOf(
                "ALL" to "All",
                "INCOMING" to "Incoming",
                "OUTGOING" to "Outgoing",
                "MISSED" to "Missed"
            ),
            onFilterSelect = {},
            onViewAllLogsClick = {},
            onCallClick = {},
            onFavoriteToggle = {}
        )
    }
}
