package com.example.callog.presentation.screens.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import com.example.callog.presentation.viewmodel.ContactDetailUiState
import com.example.callog.presentation.viewmodel.ContactDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Contact Details Screen utilizing canonical [contactId] (String) for CRM client & lead management.
 * Phase 4: Integrates CRM Lead lifecycle (Status, Priority, Feedback & Notes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    contactId: String,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onScheduleMeetingClick: (String) -> Unit = {},
    onCreateTaskClick: (String) -> Unit = {},
    detailsViewModel: ContactDetailsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by detailsViewModel.uiState.collectAsState()

    // Dialog States
    var showEditNotesDialog by remember { mutableStateOf(false) }
    var notesInput by remember { mutableStateOf("") }

    var showFeedbackDialog by remember { mutableStateOf(false) }
    var feedbackInput by remember { mutableStateOf("") }
    var feedbackRatingInput by remember { mutableStateOf(5) }

    var showFollowUpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(contactId) {
        detailsViewModel.loadContact(contactId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleName = when (val s = uiState) {
                        is ContactDetailUiState.Success -> s.contact.name
                        else -> "Contact Details"
                    }
                    Text(titleName, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when (val state = uiState) {
            is ContactDetailUiState.Loading -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AllSetBlue)
                }
            }

            is ContactDetailUiState.Error -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Red500
                    )
                }
            }

            is ContactDetailUiState.Success -> {
                val contact = state.contact
                val person = state.person
                val lead = state.lead
                val contactCallLogs = state.interactionHistory

                val initials = remember(contact.name) {
                    val parts = contact.name.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                    } else {
                        contact.name.take(1).uppercase()
                    }
                }

                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    // 1. Profile Header Card
                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ContactAvatar(
                                    name = contact.name,
                                    initials = initials,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.size(80.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate50
                                )
                                if (!person?.companyName.isNullOrBlank()) {
                                    Text(
                                        text = person?.companyName ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate300
                                    )
                                }
                                Text(
                                    text = "Canonical ID: ${person?.id ?: contactId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Teal300
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Quick Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    QuickActionButton(
                                        icon = Icons.Default.Phone,
                                        label = "Call",
                                        color = Teal300,
                                        onClick = {
                                            contact.phoneNumbers.firstOrNull()?.let { phone ->
                                                viewModel.initiateCall(context, phone)
                                            }
                                        }
                                    )
                                    QuickActionButton(
                                        icon = Icons.Default.Message,
                                        label = "Message",
                                        color = AllSetBlue,
                                        onClick = {
                                            contact.phoneNumbers.firstOrNull()?.let { phone ->
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                    QuickActionButton(
                                        icon = Icons.Default.Email,
                                        label = "Email",
                                        color = Amber500,
                                        onClick = {
                                            contact.emails.firstOrNull()?.let { email ->
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. CRM Lifecycle & Lead Status Section (PHASE 4)
                    item {
                        Text(
                            text = "CRM Lead Status & Priority",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate300
                        )
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Lead Status Selector
                                Column {
                                    Text(
                                        text = "LEAD STATUS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LeadStatus.entries.forEach { statusOption ->
                                            val isSelected = (lead?.status ?: LeadStatus.UNKNOWN) == statusOption
                                            val (badgeBg, badgeFg) = when (statusOption) {
                                                LeadStatus.HOT -> if (isSelected) Red500 to Color.White else Red500.copy(alpha = 0.15f) to Red500
                                                LeadStatus.WARM -> if (isSelected) Amber500 to Slate900 else Amber500.copy(alpha = 0.15f) to Amber500
                                                LeadStatus.COLD -> if (isSelected) Teal300 to Slate900 else Teal300.copy(alpha = 0.15f) to Teal300
                                                LeadStatus.NEW -> if (isSelected) AllSetBlue to Color.White else AllSetBlue.copy(alpha = 0.15f) to AllSetBlue
                                                LeadStatus.CUSTOMER -> if (isSelected) Color(0xFF52C41A) to Color.White else Color(0xFF52C41A).copy(alpha = 0.15f) to Color(0xFF52C41A)
                                                LeadStatus.UNKNOWN -> if (isSelected) Slate400 to Slate900 else Slate400.copy(alpha = 0.15f) to Slate400
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        detailsViewModel.updateLeadStatus(statusOption)
                                                    },
                                                color = badgeBg,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = statusOption.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = badgeFg
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Slate700.copy(alpha = 0.5f))

                                // Lead Priority Selector
                                Column {
                                    Text(
                                        text = "DEAL PRIORITY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        LeadPriority.entries.forEach { priorityOption ->
                                            val isSelected = (lead?.priority ?: LeadPriority.MEDIUM) == priorityOption
                                            val chipColor = when (priorityOption) {
                                                LeadPriority.URGENT -> Red500
                                                LeadPriority.HIGH -> Amber500
                                                LeadPriority.MEDIUM -> AllSetBlue
                                                LeadPriority.LOW -> Teal300
                                            }

                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    detailsViewModel.updateLeadPriority(priorityOption)
                                                },
                                                label = {
                                                    Text(
                                                        priorityOption.name,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = chipColor.copy(alpha = 0.25f),
                                                    selectedLabelColor = chipColor,
                                                    containerColor = Color.Transparent,
                                                    labelColor = Slate300
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isSelected,
                                                    borderColor = if (isSelected) chipColor else Slate700
                                                )
                                            )
                                        }
                                    }
                                }

                                // Follow-Up & Source Meta
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "NEXT FOLLOW-UP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Slate400
                                        )
                                        val followUpText = lead?.nextFollowUpAt?.let {
                                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
                                        } ?: "Not Scheduled"
                                        Text(
                                            text = followUpText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (lead?.nextFollowUpAt != null) Teal300 else Slate400,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    Button(
                                        onClick = { showFollowUpDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AllSetBlue.copy(alpha = 0.2f),
                                            contentColor = AllSetLavender
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Set Date", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 3. CRM Notes Section (PHASE 4)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CRM Deal Notes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                            IconButton(onClick = {
                                notesInput = lead?.notes ?: ""
                                showEditNotesDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Notes", tint = Teal300)
                            }
                        }
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                if (lead?.notes.isNullOrBlank()) {
                                    Text(
                                        text = "No deal notes recorded. Click edit to add notes.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate400
                                    )
                                } else {
                                    Text(
                                        text = lead?.notes ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate50
                                    )
                                }
                            }
                        }
                    }

                    // 4. CRM Feedback Section (PHASE 4)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Latest Client Feedback",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                            IconButton(onClick = {
                                feedbackInput = lead?.feedback ?: ""
                                feedbackRatingInput = lead?.feedbackRating ?: 5
                                showFeedbackDialog = true
                            }) {
                                Icon(Icons.Default.RateReview, contentDescription = "Edit Feedback", tint = Amber500)
                            }
                        }
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (lead?.feedback.isNullOrBlank() && lead?.feedbackRating == null) {
                                    Text(
                                        text = "No feedback recorded yet. Tap review icon to submit feedback.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate400
                                    )
                                } else {
                                    if (lead?.feedbackRating != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            repeat(5) { index ->
                                                val starColor = if (index < (lead.feedbackRating ?: 0)) Amber500 else Slate400
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = starColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${lead.feedbackRating}/5 Rating",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Amber500,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (!lead?.feedback.isNullOrBlank()) {
                                        Text(
                                            text = "\"${lead?.feedback}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Slate50
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Contact Phone & Email Details
                    item {
                        Text(
                            text = "Contact Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate300
                        )
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                contact.phoneNumbers.forEach { phone ->
                                    InfoRow(icon = Icons.Default.Phone, label = "Phone Number", value = phone, tint = Teal300)
                                }
                                contact.emails.forEach { email ->
                                    InfoRow(icon = Icons.Default.Email, label = "Email Address", value = email, tint = Amber500)
                                }
                            }
                        }
                    }

                    // 6. Cross-Device Aliases (if available)
                    if (state.aliases.isNotEmpty()) {
                        item {
                            Text(
                                text = "Cross-Device Aliases (${state.aliases.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate300
                            )
                        }

                        item {
                            GlassyCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.aliases.forEach { alias ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(alias.aliasName, style = MaterialTheme.typography.bodyMedium, color = Slate50)
                                            Text("Device: ${alias.deviceId.take(8)}", style = MaterialTheme.typography.labelSmall, color = Slate400)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 7. Recent Call History with this contact
                    item {
                        Text(
                            text = "Interaction History (${contactCallLogs.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate300
                        )
                    }

                    if (contactCallLogs.isEmpty()) {
                        item {
                            GlassyCard {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No recent calls recorded with this contact", color = Slate400)
                                }
                            }
                        }
                    } else {
                        items(contactCallLogs, key = { it.id }) { callLog ->
                            GlassyCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = callLog.callType.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Slate50
                                        )
                                        Text(
                                            text = "${callLog.duration}s duration",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Slate400
                                        )
                                    }
                                    Text(
                                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(callLog.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Edit Notes Dialog
    if (showEditNotesDialog) {
        AlertDialog(
            onDismissRequest = { showEditNotesDialog = false },
            title = { Text("Edit CRM Notes", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Deal / Client Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        detailsViewModel.updateLeadNotes(notesInput)
                        showEditNotesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AllSetBlue)
                ) {
                    Text("Save Notes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNotesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Client Feedback & Rating", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Rating (1-5 stars):", style = MaterialTheme.typography.labelMedium, color = Slate400)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { feedbackRatingInput = star }) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "$star stars",
                                    tint = if (star <= feedbackRatingInput) Amber500 else Slate400,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = feedbackInput,
                        onValueChange = { feedbackInput = it },
                        label = { Text("Feedback Details") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        detailsViewModel.updateLeadFeedback(feedbackInput, feedbackRatingInput)
                        showFeedbackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Slate900)
                ) {
                    Text("Submit Feedback", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Follow-Up Dialog
    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = { showFollowUpDialog = false },
            title = { Text("Schedule Follow-Up", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quick follow-up presets:", style = MaterialTheme.typography.labelSmall, color = Slate400)
                    Button(
                        onClick = {
                            val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(tomorrow)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                    ) {
                        Text("Tomorrow (+24 hours)")
                    }
                    Button(
                        onClick = {
                            val in3Days = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(in3Days)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                    ) {
                        Text("In 3 Days")
                    }
                    Button(
                        onClick = {
                            val nextWeek = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(nextWeek)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate700)
                    ) {
                        Text("Next Week (+7 days)")
                    }
                    Button(
                        onClick = {
                            detailsViewModel.updateLeadFollowUp(null)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500.copy(alpha = 0.2f), contentColor = Red500)
                    ) {
                        Text("Clear Follow-Up")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFollowUpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate300)
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Slate50)
        }
    }
}
