package com.example.callog.presentation.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDurationString
import com.example.callog.presentation.components.*
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
    
    val recentCalls = callLogs.take(3)
    
    // Find unique favorite contacts (group by number)
    val favoriteContacts = favoriteLogs.distinctBy { it.number }.take(10)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
        // Welcome and Seeding Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Call Intelligence",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Welcome to CallVault Dashboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { callViewModel.syncLogs() },
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSyncing) MaterialTheme.colorScheme.surfaceVariant else Teal500
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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
                    text = if (isSyncing) (syncProgress ?: "Syncing...") else "Sync Cloud",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Quick Statistics row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Calls",
                value = analyticsState.totalCalls.toString(),
                icon = Icons.Default.Call,
                iconColor = Teal300,
                modifier = Modifier.weight(1f)
            )
            
            StatCard(
                title = "Avg Length",
                value = analyticsState.avgDurationSeconds.toDurationString(),
                icon = Icons.Default.HourglassEmpty,
                iconColor = Green500,
                modifier = Modifier.weight(1f)
            )
        }

        // Horizontal Favorites Contacts
        if (favoriteContacts.isNotEmpty()) {
            Column {
                Text(
                    text = "Quick Contacts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(favoriteContacts) { contact ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(70.dp)
                                .clickable {
                                    // Clicking a quick contact opens their last call log details
                                    onCallClick(contact.id)
                                }
                        ) {
                            ContactAvatar(
                                name = contact.displayName,
                                initials = contact.initials,
                                photoUri = contact.contactPhotoUri,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = contact.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Slate400,
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                pendingReminders.forEach { reminder ->
                    ReminderItemCard(
                        reminder = reminder,
                        onCompleteClick = { callViewModel.markReminderCompleted(reminder.reminder.id) },
                        onDeleteClick = { callViewModel.deleteReminder(reminder.reminder.id) }
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
                    text = "Recent Calls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Teal300,
                    modifier = Modifier.clickable { onViewAllLogsClick() }
                )
            }

            if (recentCalls.isEmpty()) {
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No calls found. Grant permissions or sync logs to see call log aggregates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                recentCalls.forEach { call ->
                    CallCard(
                        call = call,
                        onClick = { onCallClick(call.id) },
                        onFavoriteToggle = { callViewModel.toggleFavorite(call.id) }
                    )
                }
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
    )
}
}
