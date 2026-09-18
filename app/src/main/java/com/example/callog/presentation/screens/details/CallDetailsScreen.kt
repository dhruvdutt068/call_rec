package com.example.callog.presentation.screens.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.core.extensions.toDateTimeString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.*
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Material 3 Expressive Call Details Screen integrating Telephony, Audio Vault,
 * Canonical Person Identity, Lead Pipeline, Sales Wrap-up, and Quick CRM Actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CallDetailsScreen(
    callId: Long,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onNavigateToContact: ((String) -> Unit)? = null,
    onScheduleMeeting: ((String) -> Unit)? = null,
    onCreateTask: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val crmDetailsOpt by viewModel.getCallWithCrmDetailsFlow(callId).collectAsState(initial = null)
    val fallbackCallOpt by viewModel.getCallLogByIdFlow(callId).collectAsState(initial = null)

    val call = crmDetailsOpt?.call ?: fallbackCallOpt
    val person = crmDetailsOpt?.person
    val lead = crmDetailsOpt?.lead
    val salesCall = crmDetailsOpt?.salesCall
    val historyCount = crmDetailsOpt?.totalCallCountWithContact ?: 1
    val totalDuration = crmDetailsOpt?.totalDurationWithContact ?: (call?.duration ?: 0)

    call?.let { currentCall ->
        // States for notes and tags
        var notesText by remember(currentCall.notes) { mutableStateOf(currentCall.notes ?: "") }
        var isNotesSaved by remember { mutableStateOf(false) }

        // States for tags
        val currentTags = remember(currentCall.tags) { currentCall.tags.toMutableStateList() }
        var customTagInput by remember { mutableStateOf("") }
        val presetTags = listOf("Work", "Client", "Lead", "Sales", "Support", "FollowUp", "VIP")

        // States for CRM Sales Disposition Wrap-Up
        var salesRating by remember(lead?.feedbackRating) { mutableIntStateOf(lead?.feedbackRating ?: 5) }
        var salesFeedback by remember(lead?.feedback) { mutableStateOf(lead?.feedback ?: "") }
        var selectedOutcome by remember(salesCall?.callType) {
            mutableStateOf(salesCall?.callType ?: currentCall.callType)
        }
        var isSalesDispositionSaved by remember { mutableStateOf(false) }

        // Dialog states
        var showReminderDialog by remember { mutableStateOf(false) }
        var reminderNote by remember { mutableStateOf("") }
        var showAddContactDialog by remember { mutableStateOf(false) }
        var newContactName by remember { mutableStateOf(currentCall.name ?: "") }
        var newContactCompany by remember { mutableStateOf("") }

        // States for audio playback (Real MediaPlayer)
        var isPlaying by remember { mutableStateOf(false) }
        var playProgress by remember { mutableFloatStateOf(0f) }

        val context = androidx.compose.ui.platform.LocalContext.current

        // Initialize MediaPlayer
        val mediaPlayer = remember(currentCall.recordingPath) {
            if (currentCall.recordingPath != null) {
                try {
                    android.media.MediaPlayer().apply {
                        val file = java.io.File(currentCall.recordingPath)
                        val fis = java.io.FileInputStream(file)
                        setDataSource(fis.fd)
                        prepare()
                        fis.close()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallDetailsScreen", "Error preparing MediaPlayer for path ${currentCall.recordingPath}", e)
                    null
                }
            } else {
                null
            }
        }

        val duration = remember(mediaPlayer) {
            mediaPlayer?.duration?.toFloat()?.div(1000f)?.coerceAtLeast(1f) ?: 1f
        }

        DisposableEffect(mediaPlayer) {
            onDispose {
                mediaPlayer?.release()
            }
        }

        LaunchedEffect(isPlaying, mediaPlayer) {
            if (isPlaying && mediaPlayer != null) {
                try {
                    mediaPlayer.start()
                    while (isPlaying && mediaPlayer.isPlaying) {
                        val currentPos = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration.toFloat()
                        playProgress = currentPos.coerceIn(0f, 1f)
                        delay(100)
                    }
                    if (!mediaPlayer.isPlaying && playProgress >= 0.95f) {
                        isPlaying = false
                        playProgress = 0f
                        mediaPlayer.seekTo(0)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallDetailsScreen", "Error playing recording", e)
                }
            } else {
                try {
                    if (mediaPlayer?.isPlaying == true) {
                        mediaPlayer.pause()
                    }
                } catch (e: Exception) {
                    // Ignore state errors
                }
            }
        }

        var isUploadingManual by remember { mutableStateOf(false) }
        val audioPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                isUploadingManual = true
                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val cacheFile = java.io.File(
                            context.cacheDir,
                            "manual_rec_${currentCall.id}_${System.currentTimeMillis()}.mp3"
                        )
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        viewModel.associateAndUploadRecording(currentCall.id, cacheFile.absolutePath) { result ->
                            isUploadingManual = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CallDetailsScreen", "Failed to copy picked audio file", e)
                        isUploadingManual = false
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Call & CRM Details",
                                style = CallogTypography.sectionTitle,
                                fontWeight = FontWeight.Bold
                            )
                            person?.companyName?.let { company ->
                                Text(
                                    text = company,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(currentCall.id) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (currentCall.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (currentCall.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (currentCall.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteCall(currentCall.id)
                                onBackClick()
                            },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete call log",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── 1. Header Profile & CRM Identity Card ───────────────────────
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ContactAvatar(
                                name = person?.displayName ?: currentCall.name,
                                initials = currentCall.initials,
                                photoUri = currentCall.contactPhotoUri,
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = person?.displayName ?: currentCall.displayName,
                                    style = CallogTypography.entityName,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentCall.number,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!person?.companyName.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Business,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = person!!.companyName!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CallTypeIcon(currentCall.callType)
                                    Text(
                                        text = "${currentCall.callType} • ${currentCall.timestamp.toDateTimeString()}",
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Status Pills Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SyncStatusPill(status = currentCall.syncStatus)

                            if (lead != null) {
                                LeadStatusPill(status = lead.status)
                                PriorityPill(priority = lead.priority)
                            } else {
                                ExpressiveStatusPill(
                                    label = "NOT IN CRM",
                                    accentColor = MaterialTheme.colorScheme.outline,
                                    icon = Icons.Outlined.PersonOff
                                )
                            }
                        }

                        // Direct CRM Profile Action or Add Contact
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (person != null) {
                                Text(
                                    text = "Canonical ID: ${person.id.take(8)}...",
                                    style = CallogTypography.denseData,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                    onClick = { onNavigateToContact?.invoke(person.id) },
                                    shape = CallogShapes.pill
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("View CRM Profile", fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                Text(
                                    text = "Unknown contact in CRM directory",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilledTonalButton(
                                    onClick = { showAddContactDialog = true },
                                    shape = CallogShapes.pill
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add to CRM", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ── 2. CRM Relationship Telemetry & Quick Action Hub ────────────
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "CRM Telemetry & Quick Actions",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = CallogShapes.card,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Total Calls",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$historyCount",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Surface(
                                shape = CallogShapes.card,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Total Talk Time",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = totalDuration.toDurationString(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        // Action Buttons Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onScheduleMeeting?.invoke(person?.id ?: currentCall.number)
                                },
                                shape = CallogShapes.interactive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Meeting", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    onCreateTask?.invoke(person?.id ?: currentCall.number)
                                },
                                shape = CallogShapes.interactive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Task", fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { showReminderDialog = true },
                                shape = CallogShapes.interactive,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Alert", fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ── 3. CRM Lead Pipeline & Lifecycle Manager ────────────────────
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "CRM Lead Pipeline",
                                style = CallogTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (lead != null) {
                                LeadStatusPill(status = lead.status)
                            }
                        }

                        if (person != null) {
                            // Lead Status Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Lead Stage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val statuses = listOf(
                                        LeadStatus.NEW,
                                        LeadStatus.HOT,
                                        LeadStatus.WARM,
                                        LeadStatus.COLD,
                                        LeadStatus.CUSTOMER
                                    )
                                    statuses.forEach { status ->
                                        val isSelected = lead?.status == status
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateLeadStatus(person.id, status)
                                            },
                                            label = { Text(status.name) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            shape = CallogShapes.pill
                                        )
                                    }
                                }
                            }

                            // Lead Priority Selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Deal Priority",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val priorities = listOf(
                                        LeadPriority.LOW,
                                        LeadPriority.MEDIUM,
                                        LeadPriority.HIGH,
                                        LeadPriority.URGENT
                                    )
                                    priorities.forEach { priority ->
                                        val isSelected = lead?.priority == priority
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.updateLeadPriority(person.id, priority)
                                            },
                                            label = { Text(priority.name) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            shape = CallogShapes.pill
                                        )
                                    }
                                }
                            }

                            // Follow-up Schedule
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                val followUpFormatted = lead?.nextFollowUpAt?.let { epoch ->
                                    SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(epoch))
                                } ?: "No follow-up scheduled"

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Next Follow-up",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = followUpFormatted,
                                        style = CallogTypography.denseData,
                                        color = if (lead?.nextFollowUpAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val followUpOptions = listOf(
                                        "Tomorrow" to (24 * 60 * 60 * 1000L),
                                        "In 3 Days" to (3 * 24 * 60 * 60 * 1000L),
                                        "Next Week" to (7 * 24 * 60 * 60 * 1000L)
                                    )

                                    followUpOptions.forEach { (label, durationMs) ->
                                        FilledTonalButton(
                                            onClick = {
                                                val targetEpoch = System.currentTimeMillis() + durationMs
                                                viewModel.updateLeadFollowUp(person.id, targetEpoch)
                                            },
                                            shape = CallogShapes.pill
                                        ) {
                                            Text(label, fontSize = 12.sp)
                                        }
                                    }

                                    if (lead?.nextFollowUpAt != null) {
                                        TextButton(
                                            onClick = {
                                                viewModel.updateLeadFollowUp(person.id, null)
                                            },
                                            shape = CallogShapes.pill
                                        ) {
                                            Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Caller is not registered as a CRM lead yet. Add this caller to CRM to manage lifecycle stage, priority, and follow-ups.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(
                                onClick = { showAddContactDialog = true },
                                shape = CallogShapes.interactive
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create CRM Lead Record")
                            }
                        }
                    }
                }

                // ── 4. Post-Call Sales Disposition Wrap-Up Card ─────────────────
                GlassyCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sales Call Wrap-up & Outcome",
                                style = CallogTypography.sectionTitle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            AnimatedVisibility(
                                visible = isSalesDispositionSaved,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CallogSemanticColors.SyncSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Saved to CRM",
                                        color = CallogSemanticColors.SyncSuccess,
                                        style = CallogTypography.statusLabel,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Star Rating for Sales Call Quality
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Conversation Rating ($salesRating/5)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (1..5).forEach { starIndex ->
                                    IconButton(
                                        onClick = {
                                            salesRating = starIndex
                                            isSalesDispositionSaved = false
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (starIndex <= salesRating) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Rate $starIndex stars",
                                            tint = if (starIndex <= salesRating) Amber500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Call Outcome Tag Selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Call Disposition Outcome",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val outcomes = listOf(
                                "CONNECTED",
                                "INTERESTED",
                                "QUOTE_SENT",
                                "FOLLOW_UP",
                                "CLOSED_WON",
                                "CLOSED_LOST",
                                "VOICEMAIL"
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                outcomes.forEach { outcome ->
                                    val isSelected = selectedOutcome.equals(outcome, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedOutcome = outcome
                                            isSalesDispositionSaved = false
                                        },
                                        label = { Text(outcome) },
                                        shape = CallogShapes.pill
                                    )
                                }
                            }
                        }

                        // Sales Feedback & Discussion Notes
                        OutlinedTextField(
                            value = salesFeedback,
                            onValueChange = {
                                salesFeedback = it
                                isSalesDispositionSaved = false
                            },
                            label = { Text("Sales Feedback & Client Objections") },
                            placeholder = { Text("e.g. Client requested 10% discount on enterprise tier, send quote by Friday...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CallogShapes.card
                        )

                        Button(
                            onClick = {
                                viewModel.saveSalesCallDisposition(
                                    callId = currentCall.id,
                                    personId = person?.id,
                                    buyerNumber = currentCall.number,
                                    buyerName = person?.displayName ?: currentCall.name,
                                    rating = salesRating,
                                    feedback = salesFeedback,
                                    outcome = selectedOutcome
                                )
                                isSalesDispositionSaved = true
                            },
                            shape = CallogShapes.interactive,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Sales Disposition & Sync", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── 5. Call Recording Player (Waveform & Cloud Vault) ───────────
                if (currentCall.recordingPath != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Call Recording Vault",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = CallogSemanticColors.RecordingActive,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = currentCall.recordingPath.substringAfterLast("/"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                val uploadStatusText = when (currentCall.recordingUploadStatus) {
                                    "SUCCESS" -> "Cloud Storage Uploaded"
                                    "UPLOADING" -> "Cloud Storage Uploading..."
                                    "FAILED" -> "Cloud Storage Upload Failed"
                                    else -> "Local File (Pending Cloud Upload)"
                                }
                                val uploadStatusColor = when (currentCall.recordingUploadStatus) {
                                    "SUCCESS" -> CallogSemanticColors.SyncSuccess
                                    "UPLOADING" -> CallogSemanticColors.SyncInProgress
                                    "FAILED" -> CallogSemanticColors.SyncFailed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val uploadStatusIcon = when (currentCall.recordingUploadStatus) {
                                    "SUCCESS" -> Icons.Default.CloudDone
                                    "UPLOADING" -> Icons.Default.Sync
                                    "FAILED" -> Icons.Default.Warning
                                    else -> Icons.Default.CloudUpload
                                }

                                Surface(
                                    color = uploadStatusColor.copy(alpha = 0.12f),
                                    shape = CallogShapes.pill
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = uploadStatusIcon,
                                            contentDescription = null,
                                            tint = uploadStatusColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = uploadStatusText,
                                            style = CallogTypography.statusLabel,
                                            color = uploadStatusColor,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Bouncing animated waveform
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0..24) {
                                        val waveHeight by animateDpAsState(
                                            targetValue = if (isPlaying) {
                                                ((i * 13 + (playProgress * 1000).toInt()) % 30 + 10).dp
                                            } else {
                                                10.dp
                                            },
                                            animationSpec = spring(dampingRatio = 0.5f),
                                            label = "wave_$i"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(waveHeight)
                                                .clip(CircleShape)
                                                .background(if (isPlaying) CallogSemanticColors.RecordingActive else MaterialTheme.colorScheme.outlineVariant)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Slider(
                                    value = playProgress,
                                    onValueChange = { progress ->
                                        playProgress = progress
                                        mediaPlayer?.let { p ->
                                            val seekMs = (progress * p.duration).toInt()
                                            p.seekTo(seekMs)
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (playProgress * duration).toInt().toDurationString(),
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = duration.toInt().toDurationString(),
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val playInteraction = remember { MutableInteractionSource() }
                                    val playPressed by playInteraction.collectIsPressedAsState()
                                    val playScale by animateFloatAsState(
                                        targetValue = if (playPressed) 0.90f else 1f,
                                        animationSpec = CallogMotion.bouncySpring(),
                                        label = "playScale"
                                    )

                                    FilledIconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        interactionSource = playInteraction,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .scale(playScale),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause playback" else "Start playback",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    var isActionInProgress by remember { mutableStateOf(false) }
                                    val status = currentCall.recordingUploadStatus

                                    when (status) {
                                        "SUCCESS" -> {
                                            OutlinedIconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.deleteRecording(currentCall.id) {
                                                        isActionInProgress = false
                                                    }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier.size(56.dp),
                                                colors = IconButtonDefaults.outlinedIconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteSweep,
                                                        contentDescription = "Delete Cloud Recording",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                        "UPLOADING" -> {
                                            FilledTonalIconButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        else -> {
                                            FilledTonalIconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.uploadRecording(currentCall.id) { _ ->
                                                        isActionInProgress = false
                                                    }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier.size(56.dp)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (status == "FAILED") Icons.Default.Replay else Icons.Default.CloudUpload,
                                                        contentDescription = "Upload to Cloud",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Call Recording",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "No recording file associated with this call log.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )

                                Button(
                                    onClick = {
                                        if (!isUploadingManual) {
                                            audioPickerLauncher.launch("audio/*")
                                        }
                                    },
                                    enabled = !isUploadingManual,
                                    shape = CallogShapes.interactive
                                ) {
                                    if (isUploadingManual) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = null
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isUploadingManual) "Uploading..." else "Upload Recording",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 6. Personal Notes Input ─────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Personal Call Notes",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = isNotesSaved,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CallogSemanticColors.SyncSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Saved",
                                    color = CallogSemanticColors.SyncSuccess,
                                    style = CallogTypography.statusLabel,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesText,
                        onValueChange = {
                            notesText = it
                            isNotesSaved = false
                        },
                        placeholder = {
                            Text(
                                text = "Add personal notes about this conversation...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.card
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    FilledTonalButton(
                        onClick = {
                            viewModel.updateNotes(currentCall.id, notesText)
                            isNotesSaved = true
                        },
                        shape = CallogShapes.interactive,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Notes", fontWeight = FontWeight.Bold)
                    }
                }

                // ── 7. Call Tags Manager ────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Call Tags",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentTags.forEach { tag ->
                            TagChip(
                                text = tag,
                                isRemovable = true,
                                onRemove = {
                                    currentTags.remove(tag)
                                    viewModel.updateTags(currentCall.id, currentTags.toList())
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Pre-set Categories",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetTags.forEach { tag ->
                            val isAdded = currentTags.contains(tag)
                            FilterChip(
                                selected = isAdded,
                                onClick = {
                                    if (isAdded) {
                                        currentTags.remove(tag)
                                    } else {
                                        currentTags.add(tag)
                                    }
                                    viewModel.updateTags(currentCall.id, currentTags.toList())
                                },
                                label = { Text(tag, style = CallogTypography.statusLabel) },
                                shape = CallogShapes.pill
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customTagInput,
                            onValueChange = { customTagInput = it },
                            placeholder = {
                                Text(
                                    text = "Add custom tag...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = CallogShapes.interactive,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                val newTag = customTagInput.trim()
                                if (newTag.isNotEmpty() && !currentTags.contains(newTag)) {
                                    currentTags.add(newTag)
                                    viewModel.updateTags(currentCall.id, currentTags.toList())
                                    customTagInput = ""
                                }
                            },
                            shape = CallogShapes.interactive,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Tag"
                            )
                        }
                    }
                }
            }
        }

        // ── Dialog: Schedule Callback Reminder ───────────────────────────────
        if (showReminderDialog) {
            AlertDialog(
                onDismissRequest = { showReminderDialog = false },
                title = {
                    Text(
                        text = "Schedule Callback Reminder",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Set a callback alert for ${person?.displayName ?: currentCall.displayName}. When the timer fires, you will receive a local notification.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = reminderNote,
                            onValueChange = { reminderNote = it },
                            placeholder = {
                                Text(
                                    text = "e.g. Discuss contract pricing details...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            label = { Text("Reminder Notes") },
                            shape = CallogShapes.card,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Select Time Delay",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )

                        val presetTimes = listOf(
                            "In 1 min" to 1 * 60 * 1000L,
                            "In 10 min" to 10 * 60 * 1000L,
                            "In 1 hour" to 60 * 60 * 1000L,
                            "Tomorrow" to 24 * 60 * 60 * 1000L
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            presetTimes.forEach { (label, durationMillis) ->
                                FilledTonalButton(
                                    onClick = {
                                        val targetTime = System.currentTimeMillis() + durationMillis
                                        viewModel.scheduleReminder(currentCall.id, targetTime, reminderNote)
                                        showReminderDialog = false
                                        reminderNote = ""
                                    },
                                    shape = CallogShapes.pill
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { },
                dismissButton = {
                    TextButton(
                        onClick = { showReminderDialog = false },
                        shape = CallogShapes.interactive
                    ) {
                        Text("Cancel")
                    }
                },
                shape = CallogShapes.dialog
            )
        }

        // ── Dialog: Add to CRM Contact ───────────────────────────────────────
        if (showAddContactDialog) {
            AlertDialog(
                onDismissRequest = { showAddContactDialog = false },
                title = {
                    Text(
                        text = "Create CRM Contact & Lead",
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Register this phone number (${currentCall.number}) as a new CRM profile in your global database.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = newContactName,
                            onValueChange = { newContactName = it },
                            label = { Text("Contact Full Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CallogShapes.card
                        )

                        OutlinedTextField(
                            value = newContactCompany,
                            onValueChange = { newContactCompany = it },
                            label = { Text("Company / Organization (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CallogShapes.card
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newContactName.isNotBlank()) {
                                viewModel.createAndLinkContact(
                                    callId = currentCall.id,
                                    name = newContactName.trim(),
                                    number = currentCall.number,
                                    company = newContactCompany.trim().ifBlank { null }
                                ) { createdPerson ->
                                    showAddContactDialog = false
                                }
                            }
                        },
                        enabled = newContactName.isNotBlank(),
                        shape = CallogShapes.interactive
                    ) {
                        Text("Save & Link")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddContactDialog = false },
                        shape = CallogShapes.interactive
                    ) {
                        Text("Cancel")
                    }
                },
                shape = CallogShapes.dialog
            )
        }
    }
}
