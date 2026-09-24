package com.example.callog.presentation.screens.details

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDateTimeString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.presentation.components.*
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CallDetailsScreen(
    callId: Long,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onNavigateToContact: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val callEntryOpt by viewModel.getCallLogByIdFlow(callId).collectAsState(initial = null)

    callEntryOpt?.let { call ->
        // States for notes and tags
        var notesText by remember(call.notes) { mutableStateOf(call.notes ?: "") }
        var isNotesSaved by remember { mutableStateOf(false) }

        // States for tags
        val currentTags = remember(call.tags) { call.tags.toMutableStateList() }
        var customTagInput by remember { mutableStateOf("") }
        val presetTags = listOf("Work", "Family", "Personal", "Friend", "Spam", "FollowUp")

        // States for audio playback (Real MediaPlayer)
        var isPlaying by remember { mutableStateOf(false) }
        var playProgress by remember { mutableFloatStateOf(0f) }

        val context = androidx.compose.ui.platform.LocalContext.current

        // Initialize MediaPlayer
        val mediaPlayer = remember(call.recordingPath) {
            if (call.recordingPath != null) {
                try {
                    android.media.MediaPlayer().apply {
                        val file = java.io.File(call.recordingPath)
                        val fis = java.io.FileInputStream(file)
                        setDataSource(fis.fd)
                        prepare()
                        fis.close()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CallDetailsScreen", "Error preparing MediaPlayer for path ${call.recordingPath}", e)
                    null
                }
            } else {
                null
            }
        }

        // Get actual duration from media file (in seconds)
        val duration = remember(mediaPlayer) {
            mediaPlayer?.duration?.toFloat()?.div(1000f)?.coerceAtLeast(1f) ?: 1f
        }

        // Release player when screen is disposed or path changes
        DisposableEffect(mediaPlayer) {
            onDispose {
                mediaPlayer?.release()
            }
        }

        // Real Playback Progress tracking
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

        // State for scheduling reminder dialog
        var showReminderDialog by remember { mutableStateOf(false) }
        var reminderNote by remember { mutableStateOf("") }

        // State for delete confirmation dialog & overflow menu
        var showDeleteConfirmDialog by remember { mutableStateOf(false) }
        var showOverflowMenu by remember { mutableStateOf(false) }

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
                            "manual_rec_${call.id}_${System.currentTimeMillis()}.mp3"
                        )
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        viewModel.associateAndUploadRecording(call.id, cacheFile.absolutePath) { _ ->
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
                        Text(
                            text = "Call Details",
                            style = MaterialTheme.typography.titleLarge,
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
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleFavorite(call.id) },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (call.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (call.isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (call.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Delete call log",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showDeleteConfirmDialog = true
                                    }
                                )
                            }
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ==========================================
                // 1. CALLER IDENTITY CARD
                // ==========================================
                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ContactAvatar(
                            name = call.name,
                            initials = call.initials,
                            photoUri = call.contactPhotoUri,
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = call.displayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = call.number,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Formatted Call Type and Duration Row
                            val (callTypeLabel, callTypeColor) = when (call.callType.uppercase()) {
                                "INCOMING" -> "Incoming call" to CallogSemanticColors.CallIncoming
                                "OUTGOING" -> "Outgoing call" to CallogSemanticColors.CallOutgoing
                                "MISSED" -> "Missed call" to CallogSemanticColors.CallMissed
                                "REJECTED" -> "Rejected call" to CallogSemanticColors.CallRejected
                                "BLOCKED" -> "Blocked call" to CallogSemanticColors.CallRejected
                                else -> call.callType to MaterialTheme.colorScheme.onSurfaceVariant
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CallTypeIcon(call.callType, modifier = Modifier.size(16.dp))
                                Text(
                                    text = callTypeLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = callTypeColor
                                )
                                if (call.duration > 0) {
                                    Text(
                                        text = "• ${call.duration.toDurationString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Clean Date & Time Display
                            Text(
                                text = call.timestamp.toDateTimeString().replace(" • ", " · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            // Sync Status Badge
                            SyncStatusPill(status = call.unifiedSyncStatus)
                        }
                    }
                }

                // ==========================================
                // 2. PRIMARY ACTION ROW (Call, Message, CRM)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call Action Button
                    Button(
                        onClick = { viewModel.initiateCall(context, call.number) },
                        modifier = Modifier.weight(1f),
                        shape = CallogShapes.interactive,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CallogSemanticColors.CallIncoming,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call ${call.displayName}",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Call",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Message Action Button
                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${call.number}")).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                android.util.Log.e("CallDetailsScreen", "Failed to launch SMS", e)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CallogShapes.interactive,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Message,
                            contentDescription = "Message ${call.displayName}",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    }

                    // CRM Action Button
                    FilledTonalButton(
                        onClick = {
                            val targetId = call.personId ?: call.number
                            if (onNavigateToContact != null) {
                                onNavigateToContact(targetId)
                            } else {
                                viewModel.setPendingNavigationKey(
                                    com.example.callog.presentation.navigation3.Nav3Key.Contacts.ContactDetails(contactId = targetId)
                                )
                                onBackClick()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = CallogShapes.interactive,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Open CRM profile for ${call.displayName}",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CRM",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // ==========================================
                // 3. CALLBACK REMINDER (Compact Card)
                // ==========================================
                val reminderInteraction = remember { MutableInteractionSource() }
                val reminderPressed by reminderInteraction.collectIsPressedAsState()
                val reminderScale by animateFloatAsState(
                    targetValue = if (reminderPressed) 0.98f else 1f,
                    animationSpec = CallogMotion.snappySpring(),
                    label = "reminderScale"
                )

                Surface(
                    onClick = { showReminderDialog = true },
                    interactionSource = reminderInteraction,
                    shape = CallogShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(reminderScale)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CallogShapes.pill,
                                color = CallogSemanticColors.LeadWarm.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = "Schedule reminder",
                                        tint = CallogSemanticColors.LeadWarm,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Callback Reminder",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Schedule a reminder notification for this caller",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ==========================================
                // 4. CALL RECORDING SECTION
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Call Recording",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (call.recordingPath != null) {
                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = CallogSemanticColors.RecordingActive,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = call.recordingPath?.substringAfterLast("/") ?: "recording.m4a",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Upload status pill
                                    val uploadStatusLabel = when (call.recordingUploadStatus) {
                                        "SUCCESS" -> "Uploaded"
                                        "UPLOADING" -> "Uploading..."
                                        "FAILED" -> "Upload Failed"
                                        else -> "Local Only"
                                    }
                                    val uploadStatusColor = when (call.recordingUploadStatus) {
                                        "SUCCESS" -> CallogSemanticColors.SyncSuccess
                                        "UPLOADING" -> CallogSemanticColors.SyncInProgress
                                        "FAILED" -> CallogSemanticColors.SyncFailed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    ExpressiveStatusPill(
                                        label = uploadStatusLabel,
                                        accentColor = uploadStatusColor
                                    )
                                }

                                // Bouncing animated waveform
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0..24) {
                                        val waveHeight by animateDpAsState(
                                            targetValue = if (isPlaying) {
                                                ((i * 13 + (playProgress * 1000).toInt()) % 24 + 8).dp
                                            } else {
                                                8.dp
                                            },
                                            animationSpec = spring(dampingRatio = 0.5f),
                                            label = "wave_$i"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(waveHeight)
                                                .clip(CircleShape)
                                                .background(if (isPlaying) CallogSemanticColors.RecordingActive else MaterialTheme.colorScheme.outlineVariant)
                                        )
                                    }
                                }

                                // Player Slider
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

                                // Play & Cloud controls
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Play/Pause Button
                                    val playInteraction = remember { MutableInteractionSource() }
                                    val playPressed by playInteraction.collectIsPressedAsState()
                                    val playScale by animateFloatAsState(
                                        targetValue = if (playPressed) 0.92f else 1f,
                                        animationSpec = CallogMotion.bouncySpring(),
                                        label = "playScale"
                                    )

                                    FilledIconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        interactionSource = playInteraction,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .scale(playScale),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause recording" else "Play recording",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    // Cloud Upload/Delete Action Button
                                    var isActionInProgress by remember { mutableStateOf(false) }
                                    val status = call.recordingUploadStatus

                                    when (status) {
                                        "SUCCESS" -> {
                                            OutlinedIconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.deleteRecording(call.id) {
                                                        isActionInProgress = false
                                                    }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier.size(48.dp),
                                                colors = IconButtonDefaults.outlinedIconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteSweep,
                                                        contentDescription = "Delete cloud recording",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                        "UPLOADING" -> {
                                            FilledTonalIconButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        else -> { // PENDING or FAILED
                                            FilledTonalIconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.uploadRecording(call.id) { _ ->
                                                        isActionInProgress = false
                                                    }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(20.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (status == "FAILED") Icons.Default.Replay else Icons.Default.CloudUpload,
                                                        contentDescription = "Upload recording to cloud",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Compact Empty State
                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CallogShapes.pill,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.MicNone,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "No recording available",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Add a recording to this call",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledTonalButton(
                                    onClick = {
                                        if (!isUploadingManual) {
                                            audioPickerLauncher.launch("audio/*")
                                        }
                                    },
                                    enabled = !isUploadingManual,
                                    shape = CallogShapes.interactive,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (isUploadingManual) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.CloudUpload,
                                            contentDescription = "Upload call recording",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Upload",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 5. CALL NOTES SECTION
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Call Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        AnimatedVisibility(
                            visible = isNotesSaved,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = CallogSemanticColors.SyncSuccess,
                                    modifier = Modifier.size(16.dp)
                                )
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
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CallogShapes.interactive
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                            onClick = {
                                viewModel.updateNotes(call.id, notesText)
                                isNotesSaved = true
                            },
                            enabled = notesText != (call.notes ?: "") || !isNotesSaved,
                            shape = CallogShapes.interactive,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save call notes",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Save",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ==========================================
                // 6. CALL TAGS SECTION
                // ==========================================
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Call Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Current tags layout
                    if (currentTags.isNotEmpty()) {
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
                                        viewModel.updateTags(call.id, currentTags.toList())
                                    }
                                )
                            }
                        }
                    }

                    // Preset quick selection tags
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
                                    viewModel.updateTags(call.id, currentTags.toList())
                                },
                                label = { Text(tag, style = CallogTypography.statusLabel) },
                                shape = CallogShapes.pill
                            )
                        }
                    }

                    // Add Custom Tag text field
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
                                    viewModel.updateTags(call.id, currentTags.toList())
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Dialog for scheduling callback reminders
        if (showReminderDialog) {
            AlertDialog(
                onDismissRequest = { showReminderDialog = false },
                title = {
                    Text(
                        text = "Schedule Callback Reminder",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Set a callback alert for ${call.displayName}. When the timer fires, you will receive a local notification.",
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
                            shape = CallogShapes.interactive,
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
                                        viewModel.scheduleReminder(call.id, targetTime, reminderNote)
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

        // Dialog for confirming call deletion
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = {
                    Text(
                        text = "Delete Call Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete this call record for ${call.displayName}?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            viewModel.deleteCall(call.id)
                            onBackClick()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmDialog = false }
                    ) {
                        Text("Cancel")
                    }
                },
                shape = CallogShapes.dialog
            )
        }
    }
}
