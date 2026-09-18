package com.example.callog.presentation.screens.meetings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

data class CrmMeetingItem(
    val id: String,
    val title: String,
    val date: String,
    val time: String,
    val durationMinutes: Int,
    val locationOrLink: String,
    val linkedContactName: String? = null,
    val linkedContactId: String? = null
)

object CrmMeetingStore {
    val sampleMeetings = mutableStateListOf(
        CrmMeetingItem(
            id = "meet-1",
            title = "Quarterly Sales Review & Demo",
            date = "Today",
            time = "3:30 PM",
            durationMinutes = 45,
            locationOrLink = "Google Meet: meet.google.com/xyz-call-crm",
            linkedContactName = "Alex Smith",
            linkedContactId = "3"
        ),
        CrmMeetingItem(
            id = "meet-2",
            title = "Contract Onboarding Session",
            date = "Tomorrow",
            time = "11:00 AM",
            durationMinutes = 30,
            locationOrLink = "Phone Call: +1 555-0199",
            linkedContactName = "Emma Watson",
            linkedContactId = "4"
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingListScreen(
    onScheduleMeetingClick: () -> Unit,
    onMeetingClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val meetings = CrmMeetingStore.sampleMeetings

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Meetings & Calls",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onScheduleMeetingClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Schedule",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScheduleMeetingClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CallogShapes.cardShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule Meeting")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (meetings.isEmpty()) {
            ExpressiveEmptyState(
                title = "No Scheduled Meetings",
                description = "Schedule client calls and demo sessions to sync them directly with your CRM calendar.",
                icon = Icons.Default.Event,
                actionLabel = "Schedule Meeting",
                onActionClick = onScheduleMeetingClick,
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(meetings, key = { it.id }) { meeting ->
                    MeetingCard(
                        meeting = meeting,
                        onClick = { onMeetingClick(meeting.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MeetingCard(
    meeting: CrmMeetingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CallogShapes.badgeShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = CallogTypography.entityName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${meeting.date} at ${meeting.time} (${meeting.durationMinutes}m)",
                    style = CallogTypography.denseData,
                    color = MaterialTheme.colorScheme.primary
                )
                if (meeting.linkedContactName != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "With ${meeting.linkedContactName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleMeetingScreen(
    initialContactId: String?,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onMeetingScheduled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.contacts.collectAsState()
    val initialContact = remember(contacts, initialContactId) {
        contacts.find { it.contactId == initialContactId }
    }

    var title by remember { mutableStateOf(if (initialContact != null) "Call with ${initialContact.name}" else "") }
    var date by remember { mutableStateOf("Tomorrow") }
    var time by remember { mutableStateOf("4:00 PM") }
    var duration by remember { mutableStateOf("30") }
    var location by remember { mutableStateOf("Phone Call") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Schedule CRM Meeting",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (initialContact != null) {
                GlassyCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Meeting Attendee",
                                style = CallogTypography.statusLabel,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = initialContact.name,
                                style = CallogTypography.entityName,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Meeting Title / Agenda") },
                placeholder = { Text("e.g. Contract Discussion") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.inputShape,
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.inputShape,
                    singleLine = true
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.inputShape,
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.inputShape,
                singleLine = true
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location or Link") },
                modifier = Modifier.fillMaxWidth(),
                shape = CallogShapes.inputShape,
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        CrmMeetingStore.sampleMeetings.add(
                            0,
                            CrmMeetingItem(
                                id = "meet-${System.currentTimeMillis()}",
                                title = title.trim(),
                                date = date.trim(),
                                time = time.trim(),
                                durationMinutes = duration.toIntOrNull() ?: 30,
                                locationOrLink = location.trim(),
                                linkedContactName = initialContact?.name,
                                linkedContactId = initialContact?.contactId
                            )
                        )
                        onMeetingScheduled()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = title.isNotBlank(),
                shape = CallogShapes.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.EventAvailable,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Meeting", style = CallogTypography.sectionTitle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailsScreen(
    meetingId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meeting = CrmMeetingStore.sampleMeetings.find { it.id == meetingId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Meeting Details",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (meeting == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Meeting not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = meeting.title,
                            style = CallogTypography.heroTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${meeting.date} at ${meeting.time}",
                            style = CallogTypography.denseData,
                            color = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Duration",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${meeting.durationMinutes} minutes",
                                style = CallogTypography.denseData,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Channel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = meeting.locationOrLink,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (meeting.linkedContactName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Client",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = meeting.linkedContactName,
                                    style = CallogTypography.entityName,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Close", style = CallogTypography.sectionTitle)
                }
            }
        }
    }
}

