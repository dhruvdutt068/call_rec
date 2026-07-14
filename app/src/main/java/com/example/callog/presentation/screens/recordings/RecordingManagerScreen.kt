package com.example.callog.presentation.screens.recordings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.callog.core.extensions.toDateString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.delay

@Composable
fun RecordingManagerScreen(
    viewModel: CallViewModel,
    onCallClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val callLogs by viewModel.callLogs.collectAsState()
    
    // Filter database logs that have recording files associated
    val recordings = remember(callLogs) {
        callLogs.filter { it.recordingPath != null }
    }

    // Group recordings by phone number
    val groupedRecordings = remember(recordings) {
        recordings.groupBy { it.number }
    }

    // Sort grouped keys by the latest recording's timestamp
    val sortedGroupedKeys = remember(groupedRecordings) {
        groupedRecordings.keys.sortedByDescending { number ->
            groupedRecordings[number]?.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    // Track expanded phone number groups
    var expandedNumbers by rememberSaveable { mutableStateOf(setOf<String>()) }

    var activePlayCallId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableFloatStateOf(0f) }
    var activeDuration by remember { mutableFloatStateOf(1f) }

    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    // Clean up media player when active call changes
    LaunchedEffect(activePlayCallId) {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        playProgress = 0f
        activeDuration = 1f
        
        if (activePlayCallId != null) {
            val activeCall = recordings.firstOrNull { it.id == activePlayCallId }
            if (activeCall?.recordingPath != null) {
                try {
                    mediaPlayer = android.media.MediaPlayer().apply {
                        val file = java.io.File(activeCall.recordingPath)
                        val fis = java.io.FileInputStream(file)
                        setDataSource(fis.fd)
                        prepare()
                        activeDuration = duration.toFloat() / 1000f
                        fis.close()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecordingManagerScreen", "Error preparing MediaPlayer for path ${activeCall.recordingPath}", e)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
    }

    // Playback ticker using real player state
    LaunchedEffect(isPlaying, mediaPlayer) {
        if (isPlaying && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                while (isPlaying && mediaPlayer?.isPlaying == true) {
                    val currentPos = mediaPlayer!!.currentPosition.toFloat() / mediaPlayer!!.duration.toFloat()
                    playProgress = currentPos.coerceIn(0f, 1f)
                    delay(100)
                }
                if (mediaPlayer?.isPlaying == false && playProgress >= 0.95f) {
                    isPlaying = false
                    playProgress = 0f
                    mediaPlayer?.seekTo(0)
                }
            } catch (e: Exception) {
                android.util.Log.e("RecordingManagerScreen", "Error playing recording", e)
            }
        } else {
            try {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (e: Exception) {
                // Ignore state errors
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Recording Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Slate50,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "No Call Recordings Scanned",
                    description = "We scan accessible folders like Recordings/, Call/, OnePlus/, and Samsung/ for matching audio assets.",
                    icon = Icons.Default.QueueMusic
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                sortedGroupedKeys.forEach { number ->
                    val groupRecordings = groupedRecordings[number] ?: emptyList()
                    val isExpanded = expandedNumbers.contains(number)
                    
                    item(key = "header_$number") {
                        val representativeCall = groupRecordings.first()
                        GroupHeaderCard(
                            displayName = representativeCall.displayName,
                            number = number,
                            recordingCount = groupRecordings.size,
                            initials = representativeCall.initials,
                            photoUri = representativeCall.contactPhotoUri,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedNumbers = if (isExpanded) {
                                    expandedNumbers - number
                                } else {
                                    expandedNumbers + number
                                }
                            }
                        )
                    }

                    if (isExpanded) {
                        items(
                            items = groupRecordings,
                            key = { "rec_${it.id}" }
                        ) { call ->
                            val isActive = activePlayCallId == call.id
                            
                            RecordingCard(
                                call = call,
                                isPlaying = isActive && isPlaying,
                                playProgress = if (isActive) playProgress else 0f,
                                activeDuration = if (isActive) activeDuration else null,
                                onPlayPauseClick = {
                                    if (isActive) {
                                        isPlaying = !isPlaying
                                    } else {
                                        activePlayCallId = call.id
                                        isPlaying = true
                                        playProgress = 0f
                                    }
                                },
                                onCardClick = { onCallClick(call.id) },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    call: CallLogEntry,
    isPlaying: Boolean,
    playProgress: Float,
    onPlayPauseClick: () -> Unit,
    onCardClick: () -> Unit,
    activeDuration: Float? = null,
    modifier: Modifier = Modifier
) {
    val actualFile = remember(call.recordingPath) {
        if (call.recordingPath != null) java.io.File(call.recordingPath) else null
    }

    // Get real file size if it exists, otherwise fall back to mock
    val fileSizeString = remember(actualFile, call.duration) {
        if (actualFile != null && actualFile.exists()) {
            val bytes = actualFile.length()
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            String.format(java.util.Locale.US, "%.1f MB", mb)
        } else {
            val sizeKb = call.duration * 16.0
            val sizeMb = sizeKb / 1024.0
            String.format(java.util.Locale.US, "%.1f MB", if (sizeMb > 0.1) sizeMb else 0.4)
        }
    }

    // Use active duration from media player if available, otherwise call log duration
    val totalDurationSec = activeDuration ?: call.duration.toFloat()
    val durationString = totalDurationSec.toInt().toDurationString()
    val dateString = call.timestamp.toDateString()

    GlassyCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular Play Button
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Teal500)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Slate50,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCardClick() }
                ) {
                    Text(
                        text = call.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Duration: $durationString  •  File Size: $fileSizeString",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                val statusIcon = when (call.recordingUploadStatus) {
                    "SUCCESS" -> Icons.Default.CloudDone
                    "UPLOADING" -> Icons.Default.Sync
                    "FAILED" -> Icons.Default.CloudOff
                    else -> Icons.Default.Cloud
                }
                
                val statusColor = when (call.recordingUploadStatus) {
                    "SUCCESS" -> Green500
                    "UPLOADING" -> Amber500
                    "FAILED" -> Red500
                    else -> Slate400
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = call.recordingUploadStatus,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded slider view when active
            AnimatedVisibility(
                visible = playProgress > 0f || isPlaying,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { playProgress },
                        color = Teal300,
                        trackColor = Slate700,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = (playProgress * totalDurationSec).toInt().toDurationString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                        Text(
                            text = dateString,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeaderCard(
    displayName: String,
    number: String,
    recordingCount: Int,
    initials: String,
    photoUri: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassyCard(
        modifier = modifier,
        onClick = onToggleExpand
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                name = displayName,
                initials = initials,
                photoUri = photoUri,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displayName != number) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$recordingCount recording${if (recordingCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Teal300,
                    fontWeight = FontWeight.SemiBold
                )
            }

            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Slate400
                )
            }
        }
    }
}

