package com.example.callog.presentation.screens.contacts

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.ExpressiveLoadingView
import com.example.callog.presentation.components.ExpressiveSegmentedButtonGroup
import com.example.callog.presentation.components.ExpressiveSegmentedButtonItem
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.components.LeadStatusPill
import com.example.callog.presentation.components.PriorityPill
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import com.example.callog.presentation.viewmodel.ContactDetailUiState
import com.example.callog.presentation.viewmodel.ContactDetailsViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Contact Details Screen utilizing canonical [contactId] (String) for CRM client & lead management.
 * Material 3 Expressive upgraded with tactile feedback, semantic color tokens, and dense telemetry.
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
                        else -> "Contact Profile"
                    }
                    Text(
                        text = titleName,
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to contacts"
                        )
                    }
                },
                actions = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
                    ExpressiveLoadingView(message = "Loading contact details...")
                }
            }

            is ContactDetailUiState.Error -> {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveEmptyState(
                        title = "Unable to load contact",
                        description = state.message,
                        icon = Icons.Outlined.Person,
                        actionLabel = "Retry",
                        onActionClick = { detailsViewModel.loadContact(contactId) }
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
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ContactAvatar(
                                    name = contact.name,
                                    initials = initials,
                                    photoUri = contact.photoUri,
                                    modifier = Modifier.size(88.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = contact.name,
                                    style = CallogTypography.heroTitle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!person?.companyName.isNullOrBlank()) {
                                    Text(
                                        text = person?.companyName ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = CallogShapes.pill
                                ) {
                                    Text(
                                        text = "ID: ${person?.id ?: contactId}",
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Quick Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ExpressiveQuickActionButton(
                                        icon = Icons.Default.Phone,
                                        label = "Call",
                                        accentColor = CallogSemanticColors.Incoming,
                                        onClick = {
                                            contact.phoneNumbers.firstOrNull()?.let { phone ->
                                                viewModel.initiateCall(context, phone)
                                            }
                                        }
                                    )
                                    ExpressiveQuickActionButton(
                                        icon = Icons.Default.Message,
                                        label = "Message",
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        onClick = {
                                            contact.phoneNumbers.firstOrNull()?.let { phone ->
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                    ExpressiveQuickActionButton(
                                        icon = Icons.Default.Email,
                                        label = "Email",
                                        accentColor = CallogSemanticColors.LeadWarm,
                                        onClick = {
                                            contact.emails.firstOrNull()?.let { email ->
                                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                                context.startActivity(intent)
                                            }
                                        }
                                    )
                                    ExpressiveQuickActionButton(
                                        icon = Icons.Default.CalendarToday,
                                        label = "Meeting",
                                        accentColor = CallogSemanticColors.LeadVip,
                                        onClick = { onScheduleMeetingClick(contactId) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. CRM Lifecycle & Lead Status Section
                    item {
                        Text(
                            text = "CRM Lead Status & Priority",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Lead Status Selector
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LEAD STATUS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                        LeadStatusPill(status = lead?.status ?: LeadStatus.NEW)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))

                                    val statusItems = LeadStatus.entries.map { status ->
                                        ExpressiveSegmentedButtonItem(
                                            key = status.name,
                                            label = status.name
                                        )
                                    }
                                    ExpressiveSegmentedButtonGroup(
                                        items = statusItems,
                                        selectedKey = (lead?.status ?: LeadStatus.NEW).name,
                                        onItemSelected = { key ->
                                            val status = LeadStatus.entries.find { it.name == key } ?: LeadStatus.NEW
                                            detailsViewModel.updateLeadStatus(status)
                                        }
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // Lead Priority Selector
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "DEAL PRIORITY",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                        PriorityPill(priority = lead?.priority?.name ?: "Medium")
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))

                                    val priorityItems = LeadPriority.entries.map { priority ->
                                        ExpressiveSegmentedButtonItem(
                                            key = priority.name,
                                            label = priority.name
                                        )
                                    }
                                    ExpressiveSegmentedButtonGroup(
                                        items = priorityItems,
                                        selectedKey = (lead?.priority ?: LeadPriority.MEDIUM).name,
                                        onItemSelected = { key ->
                                            val priority = LeadPriority.entries.find { it.name == key } ?: LeadPriority.MEDIUM
                                            detailsViewModel.updateLeadPriority(priority)
                                        }
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        val followUpText = lead?.nextFollowUpAt?.let {
                                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(it))
                                        } ?: "Not Scheduled"
                                        Text(
                                            text = followUpText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (lead?.nextFollowUpAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = { showFollowUpDialog = true },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        shape = CallogShapes.interactive
                                    ) {
                                        Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Set Date", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    // 3. CRM Notes Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CRM Deal Notes",
                                style = CallogTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    notesInput = lead?.notes ?: ""
                                    showEditNotesDialog = true
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Notes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                if (lead?.notes.isNullOrBlank()) {
                                    Text(
                                        text = "No deal notes recorded. Click the edit icon to add notes.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        text = lead?.notes ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 4. CRM Feedback Section
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Client Feedback",
                                style = CallogTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = {
                                    feedbackInput = lead?.feedback ?: ""
                                    feedbackRatingInput = lead?.feedbackRating ?: 5
                                    showFeedbackDialog = true
                                },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    Icons.Default.RateReview,
                                    contentDescription = "Edit Feedback",
                                    tint = CallogSemanticColors.LeadWarm
                                )
                            }
                        }
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (lead?.feedback.isNullOrBlank() && lead?.feedbackRating == null) {
                                    Text(
                                        text = "No feedback recorded yet. Tap the review icon to submit feedback.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    if (lead?.feedbackRating != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            repeat(5) { index ->
                                                val isFilled = index < (lead.feedbackRating ?: 0)
                                                Icon(
                                                    imageVector = if (isFilled) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = null,
                                                    tint = if (isFilled) CallogSemanticColors.LeadWarm else MaterialTheme.colorScheme.outlineVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${lead.feedbackRating}/5 Rating",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = CallogSemanticColors.LeadWarm,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (!lead?.feedback.isNullOrBlank()) {
                                        Text(
                                            text = "\"${lead?.feedback}\"",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
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
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    item {
                        GlassyCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                contact.phoneNumbers.forEach { phone ->
                                    InfoRow(
                                        icon = Icons.Default.Phone,
                                        label = "Phone Number",
                                        value = phone,
                                        tint = CallogSemanticColors.Incoming
                                    )
                                }
                                contact.emails.forEach { email ->
                                    InfoRow(
                                        icon = Icons.Default.Email,
                                        label = "Email Address",
                                        value = email,
                                        tint = CallogSemanticColors.LeadWarm
                                    )
                                }
                            }
                        }
                    }

                    // 6. Cross-Device Aliases (if available)
                    if (state.aliases.isNotEmpty()) {
                        item {
                            Text(
                                text = "Cross-Device Aliases (${state.aliases.size})",
                                style = CallogTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        item {
                            GlassyCard {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.aliases.forEach { alias ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = alias.aliasName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Device: ${alias.deviceId.take(8)}",
                                                style = CallogTypography.denseData,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
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
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (contactCallLogs.isEmpty()) {
                        item {
                            ExpressiveEmptyState(
                                title = "No interaction history",
                                description = "Calls and recordings with this contact will appear here automatically.",
                                icon = Icons.Outlined.Phone
                            )
                        }
                    } else {
                        items(contactCallLogs, key = { it.id }) { callLog ->
                            GlassyCard {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = callLog.callType.lowercase().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${callLog.duration}s duration",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(callLog.timestamp)),
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            title = { Text("Edit CRM Notes", style = CallogTypography.sectionTitle) },
            text = {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Deal / Client Notes") },
                    shape = CallogShapes.card,
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
                    shape = CallogShapes.interactive
                ) {
                    Text("Save Notes")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEditNotesDialog = false },
                    shape = CallogShapes.interactive
                ) {
                    Text("Cancel")
                }
            },
            shape = CallogShapes.dialog
        )
    }

    // 2. Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Client Feedback & Rating", style = CallogTypography.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Rating (1-5 stars):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        (1..5).forEach { star ->
                            IconButton(
                                onClick = { feedbackRatingInput = star },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = if (star <= feedbackRatingInput) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "$star stars",
                                    tint = if (star <= feedbackRatingInput) CallogSemanticColors.LeadWarm else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = feedbackInput,
                        onValueChange = { feedbackInput = it },
                        label = { Text("Feedback Details") },
                        shape = CallogShapes.card,
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
                    shape = CallogShapes.interactive
                ) {
                    Text("Submit Feedback", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFeedbackDialog = false },
                    shape = CallogShapes.interactive
                ) {
                    Text("Cancel")
                }
            },
            shape = CallogShapes.dialog
        )
    }

    // 3. Follow-Up Dialog
    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = { showFollowUpDialog = false },
            title = { Text("Schedule Follow-Up", style = CallogTypography.sectionTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quick follow-up presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(
                        onClick = {
                            val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(tomorrow)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.interactive
                    ) {
                        Text("Tomorrow (+24 hours)")
                    }
                    FilledTonalButton(
                        onClick = {
                            val in3Days = System.currentTimeMillis() + 3 * 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(in3Days)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.interactive
                    ) {
                        Text("In 3 Days")
                    }
                    FilledTonalButton(
                        onClick = {
                            val nextWeek = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
                            detailsViewModel.updateLeadFollowUp(nextWeek)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.interactive
                    ) {
                        Text("Next Week (+7 days)")
                    }
                    OutlinedButton(
                        onClick = {
                            detailsViewModel.updateLeadFollowUp(null)
                            showFollowUpDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.interactive,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear Follow-Up")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showFollowUpDialog = false },
                    shape = CallogShapes.interactive
                ) {
                    Text("Close")
                }
            },
            shape = CallogShapes.dialog
        )
    }
}

@Composable
private fun ExpressiveQuickActionButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = CallogMotion.snappySpring(),
        label = "quickActionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(52.dp),
            shape = CallogShapes.avatar,
            color = accentColor.copy(alpha = 0.15f),
            contentColor = accentColor
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = CallogTypography.statusLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
        Surface(
            shape = CallogShapes.avatar,
            color = tint.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Contact Details - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Contact Details - Light", showBackground = true)
@Composable
private fun ContactDetailsPreview() {
    CallogTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContactAvatar(
                            name = "Jane Cooper",
                            initials = "JC",
                            photoUri = null,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Jane Cooper",
                            style = CallogTypography.heroTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Acme Corp Ltd.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

