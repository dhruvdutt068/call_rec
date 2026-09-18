package com.example.callog.presentation.screens.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

                        viewModel.associateAndUploadRecording(call.id, cacheFile.absolutePath) { result ->
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
                        IconButton(
                            onClick = {
                                viewModel.deleteCall(call.id)
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
                // Header profile card
                GlassyCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        ContactAvatar(
                            name = call.name,
                            initials = call.initials,
                            photoUri = call.contactPhotoUri,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = call.displayName,
                                style = CallogTypography.entityName,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = call.number,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CallTypeIcon(call.callType)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${call.callType} • ${call.timestamp.toDateTimeString()}",
                                    style = CallogTypography.denseData,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            SyncStatusPill(status = call.syncStatus)
                        }
                    }
                }

                // Interactive Callback Reminder trigger
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(reminderScale)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CallogShapes.avatar,
                                color = CallogSemanticColors.LeadWarm.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = CallogSemanticColors.LeadWarm,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Callback Reminder",
                                    style = CallogTypography.sectionTitle,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Call Recording Player (Waveforms)
                if (call.recordingPath != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Call Recording",
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
                                        text = call.recordingPath?.substringAfterLast("/") ?: "recording",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                val uploadStatusText = when (call.recordingUploadStatus) {
                                    "SUCCESS" -> "Cloud Storage Uploaded"
                                    "UPLOADING" -> "Cloud Storage Uploading..."
                                    "FAILED" -> "Cloud Storage Upload Failed"
                                    else -> "Local File (Pending Cloud Upload)"
                                }
                                val uploadStatusColor = when (call.recordingUploadStatus) {
                                    "SUCCESS" -> CallogSemanticColors.SyncSuccess
                                    "UPLOADING" -> CallogSemanticColors.SyncInProgress
                                    "FAILED" -> CallogSemanticColors.SyncFailed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val uploadStatusIcon = when (call.recordingUploadStatus) {
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

                                Spacer(modifier = Modifier.height(12.dp))

                                // Play and Upload controls
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Play/Pause Button
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
                                                modifier = Modifier.size(56.dp),
                                                colors = IconButtonDefaults.outlinedIconButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
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
                                        else -> { // PENDING or FAILED
                                            FilledTonalIconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.uploadRecording(call.id) { _ ->
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

                // Notes input field
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Call Notes",
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
                            viewModel.updateNotes(call.id, notesText)
                            isNotesSaved = true
                        },
                        shape = CallogShapes.interactive,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Notes", fontWeight = FontWeight.Bold)
                    }
                }

                // Tags Manager
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Call Tags",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Current tags layout
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

                    Spacer(modifier = Modifier.height(4.dp))

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

                    Spacer(modifier = Modifier.height(4.dp))

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
            }
        }

        // Dialog for scheduling callback reminders
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
    }
}

