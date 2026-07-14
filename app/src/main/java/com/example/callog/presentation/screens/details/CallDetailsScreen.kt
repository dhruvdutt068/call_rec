package com.example.callog.presentation.screens.details

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDateTimeString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.presentation.components.*
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
                    title = { Text("Call Details", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleFavorite(call.id) }) {
                            Icon(
                                imageVector = if (call.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (call.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            viewModel.deleteCall(call.id)
                            onBackClick()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Log", tint = Red500)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header profile card
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    ContactAvatar(
                        name = call.name,
                        initials = call.initials,
                        photoUri = call.contactPhotoUri,
                        modifier = Modifier.size(68.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = call.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = call.number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CallTypeIcon(call.callType)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${call.callType} • ${call.timestamp.toDateTimeString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val syncIcon = when (call.syncStatus.uppercase()) {
                            "SYNCED" -> Icons.Default.CloudDone
                            "UPLOADING" -> Icons.Default.CloudUpload
                            "FAILED" -> Icons.Default.CloudOff
                            else -> Icons.Default.Cloud
                        }
                        val syncColor = when (call.syncStatus.uppercase()) {
                            "SYNCED" -> Green500
                            "UPLOADING" -> Amber500
                            "FAILED" -> Red500
                            else -> Slate400
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(syncColor.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = syncIcon,
                                contentDescription = "Sync Status: ${call.syncStatus}",
                                tint = syncColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (call.syncStatus.uppercase()) {
                                    "SYNCED" -> "Backup Synced"
                                    "UPLOADING" -> "Backing up..."
                                    "FAILED" -> "Sync Failed"
                                    else -> "Local Only"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = syncColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Interactive Callback Reminder trigger
                GlassyCard(
                    onClick = { showReminderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Amber500,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Callback Reminder",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate50
                                )
                                Text(
                                    text = "Schedule a reminder notification for this caller",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Slate400
                        )
                    }
                }

                // Call Recording Player (Bouncing Waveforms!)
                if (call.recordingPath != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Call Recording",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        
                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Teal300,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = call.recordingPath?.substringAfterLast("/") ?: "recording",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Slate50,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val statusText = when (call.recordingUploadStatus) {
                                    "SUCCESS" -> "Cloud Storage Uploaded"
                                    "UPLOADING" -> "Cloud Storage Uploading..."
                                    "FAILED" -> "Cloud Storage Upload Failed"
                                    else -> "Local File (Pending Cloud Upload)"
                                }
                                val statusColor = when (call.recordingUploadStatus) {
                                    "SUCCESS" -> Green500
                                    "UPLOADING" -> Teal300
                                    "FAILED" -> Red500
                                    else -> Slate400
                                }
                                val statusIcon = when (call.recordingUploadStatus) {
                                    "SUCCESS" -> Icons.Default.CloudDone
                                    "UPLOADING" -> Icons.Default.Sync
                                    "FAILED" -> Icons.Default.Warning
                                    else -> Icons.Default.CloudUpload
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))

                                // Bouncing animated waveform
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    for (i in 0..24) {
                                        val waveHeight by animateDpAsState(
                                            targetValue = if (isPlaying) {
                                                ((i * 13 + (playProgress * 1000).toInt()) % 30 + 10).dp
                                            } else {
                                                12.dp
                                            },
                                            animationSpec = spring(dampingRatio = 0.5f)
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(waveHeight)
                                                .clip(CircleShape)
                                                .background(if (isPlaying) Teal300 else Slate700)
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
                                        thumbColor = Teal500,
                                        activeTrackColor = Teal500,
                                        inactiveTrackColor = Slate700
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = (playProgress * duration).toInt().toDurationString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                    Text(
                                        text = duration.toInt().toDurationString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Play and Upload controls
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Play/Pause Button
                                    IconButton(
                                        onClick = {
                                            isPlaying = !isPlaying
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Teal500)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Slate50,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }

                                    // GCS Upload/Delete Action Button
                                    var isActionInProgress by remember { mutableStateOf(false) }
                                    val status = call.recordingUploadStatus
                                    
                                    when (status) {
                                        "SUCCESS" -> {
                                            IconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.deleteRecording(call.id) {
                                                        isActionInProgress = false
                                                     }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Red500.copy(alpha = 0.2f))
                                                    .border(1.dp, Red500, CircleShape)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Red500
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteSweep,
                                                        contentDescription = "Delete Cloud Recording",
                                                        tint = Red500,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            }
                                        }
                                        "UPLOADING" -> {
                                            IconButton(
                                                onClick = {},
                                                enabled = false,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Slate800)
                                                    .border(1.dp, Teal300, CircleShape)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Teal300
                                                )
                                            }
                                        }
                                        else -> { // PENDING or FAILED
                                            IconButton(
                                                onClick = {
                                                    isActionInProgress = true
                                                    viewModel.uploadRecording(call.id) { _ ->
                                                        isActionInProgress = false
                                                    }
                                                },
                                                enabled = !isActionInProgress,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(Teal500.copy(alpha = 0.2f))
                                                    .border(1.dp, Teal300, CircleShape)
                                            ) {
                                                if (isActionInProgress) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp,
                                                        color = Teal300
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (status == "FAILED") Icons.Default.Replay else Icons.Default.CloudUpload,
                                                        contentDescription = "Upload to Cloud",
                                                        tint = Teal300,
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        
                        GlassyCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "No recording file associated with this call log.",
                                    color = Slate400,
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Teal500,
                                        contentColor = Slate50
                                    )
                                ) {
                                    if (isUploadingManual) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = Slate50
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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )
                        AnimatedVisibility(
                            visible = isNotesSaved,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Green500, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Saved", color = Green500, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = {
                            notesText = it
                            isNotesSaved = false
                        },
                        placeholder = { Text("Add personal notes about this conversation...", color = Slate400) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = {
                            viewModel.updateNotes(call.id, notesText)
                            isNotesSaved = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save Notes", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                // Tags Manager
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Call Tags",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    // Current tags layout
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        currentTags.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(tag, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable {
                                            currentTags.remove(tag)
                                            viewModel.updateTags(call.id, currentTags.toList())
                                        }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Preset quick selection tags
                    Text("Pre-set Categories", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presetTags.forEach { tag ->
                            val isAdded = currentTags.contains(tag)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (isAdded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isAdded) {
                                            currentTags.remove(tag)
                                        } else {
                                            currentTags.add(tag)
                                        }
                                        viewModel.updateTags(call.id, currentTags.toList())
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isAdded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
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
                            placeholder = { Text("Add custom tag...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val newTag = customTagInput.trim()
                                if (newTag.isNotEmpty() && !currentTags.contains(newTag)) {
                                    currentTags.add(newTag)
                                    viewModel.updateTags(call.id, currentTags.toList())
                                    customTagInput = ""
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Tag", tint = MaterialTheme.colorScheme.onPrimary)
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
                        "Schedule Callback Reminder",
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
                            placeholder = { Text("e.g. Discuss contract pricing details...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            label = { Text("Reminder Notes", color = MaterialTheme.colorScheme.primary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Select Time Delay", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

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
                                Button(
                                    onClick = {
                                        val targetTime = System.currentTimeMillis() + durationMillis
                                        viewModel.scheduleReminder(call.id, targetTime, reminderNote)
                                        showReminderDialog = false
                                        reminderNote = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = { },
                dismissButton = {
                    TextButton(onClick = { showReminderDialog = false }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
        }
    }
}
