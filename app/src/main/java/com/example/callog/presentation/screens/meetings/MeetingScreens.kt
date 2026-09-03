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
import com.example.callog.presentation.components.EmptyStateView
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
                title = { Text("Meetings & Calls", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onScheduleMeetingClick) {
                        Icon(Icons.Default.Add, contentDescription = "Schedule", tint = Teal300)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScheduleMeetingClick,
                containerColor = Teal300,
                contentColor = Slate900
            ) {
                Icon(Icons.Default.Add, contentDescription = "Schedule")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (meetings.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "No Scheduled Meetings",
                    description = "Schedule client calls and sync them with your CRM.",
                    icon = Icons.Default.Event
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 0.dp)
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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Teal300.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Teal300)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meeting.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${meeting.date} at ${meeting.time} (${meeting.durationMinutes}m)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Teal300
                )
                if (meeting.linkedContactName != null) {
                    Text(
                        text = "With ${meeting.linkedContactName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = Slate400
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
                title = { Text("Schedule CRM Meeting", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Teal300)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Meeting Attendee", style = MaterialTheme.typography.labelSmall, color = Slate400)
                            Text(initialContact.name, style = MaterialTheme.typography.titleSmall, color = Slate50)
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
                    singleLine = true
                )
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location or Link") },
                modifier = Modifier.fillMaxWidth(),
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
                    .height(50.dp),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
            ) {
                Text("Schedule Meeting", fontWeight = FontWeight.Bold)
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
                title = { Text("Meeting Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("Meeting not found", color = Slate400)
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
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(meeting.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Slate50)
                        Text("${meeting.date} at ${meeting.time}", style = MaterialTheme.typography.bodyLarge, color = Teal300)

                        HorizontalDivider(color = Slate700)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Duration", color = Slate400)
                            Text("${meeting.durationMinutes} minutes", color = Slate50)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Channel", color = Slate400)
                            Text(meeting.locationOrLink, color = Slate50)
                        }

                        if (meeting.linkedContactName != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Client", color = Slate400)
                                Text(meeting.linkedContactName, fontWeight = FontWeight.SemiBold, color = Amber500)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
