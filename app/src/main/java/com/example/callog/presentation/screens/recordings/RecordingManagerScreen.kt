package com.example.callog.presentation.screens.recordings

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.core.extensions.toDateString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.data.local.entity.MatchStatus
import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.UploadStatus
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.delay
import java.io.File

data class MatchedRecordingItem(
    val recording: RecordingEntity,
    val call: CallLogEntry?
)

@Composable
fun RecordingManagerScreen(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onCallClick: ((Long) -> Unit)? = null
) {
    val recordings by viewModel.recordingsFlow.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()

    // Resolve all recordings to call log candidates (both matched and unmatched)
    val resolvedRecordings = remember(recordings, callLogs) {
        recordings.map { rec ->
            val call = callLogs.firstOrNull { it.id == rec.matchedCallId }
            MatchedRecordingItem(rec, call)
        }
    }

    // Group all resolved recordings by their contact phone number / name identifier
    val groupedRecordings = remember(resolvedRecordings) {
        resolvedRecordings.groupBy { item ->
            item.call?.number ?: item.recording.phoneExtracted ?: item.recording.contactExtracted ?: "Unknown"
        }
    }

    // Sort grouped keys by the latest call/recording timestamp
    val sortedGroupedKeys = remember(groupedRecordings) {
        groupedRecordings.keys.sortedByDescending { key ->
            val group = groupedRecordings[key] ?: emptyList()
            group.maxOfOrNull { it.call?.timestamp ?: it.recording.lastModified } ?: 0L
        }
    }

    // Expandable groups saveable state
    var expandedNumbers by rememberSaveable { mutableStateOf(setOf<String>()) }

    // Detail Dialog state
    var selectedRecordingForDetail by remember { mutableStateOf<RecordingEntity?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (recordings.isEmpty()) {
            EmptyStateView(
                title = "No Recordings Found",
                description = "Ensure that recordings are stored in the configured directory, then tap the scan button.",
                icon = Icons.Default.MusicNote,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                sortedGroupedKeys.forEach { number ->
                    val groupList = groupedRecordings[number] ?: emptyList()
                    val isExpanded = expandedNumbers.contains(number)

                    // 1. Group Header Card
                    item(key = "header_$number") {
                        val representative = groupList.first()
                        val displayName = representative.call?.name 
                            ?: representative.recording.contactExtracted 
                            ?: if (number != "Unknown") number else "Unknown/Unmatched"
                        
                        val displayNumber = representative.call?.number 
                            ?: representative.recording.phoneExtracted 
                            ?: "Unknown"

                        val initials = representative.call?.initials ?: if (displayName != "Unknown/Unmatched" && displayName.isNotEmpty()) {
                            displayName.split(" ").filter { it.isNotEmpty() }.map { it.first() }.joinToString("").take(2).uppercase()
                        } else {
                            "#"
                        }
                        val photoUri = representative.call?.contactPhotoUri

                        GroupHeaderCard(
                            displayName = displayName,
                            number = displayNumber,
                            count = groupList.size,
                            initials = initials,
                            photoUri = photoUri,
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

                    // 2. Expanded Group Items
                    if (isExpanded) {
                        items(groupList, key = { "rec_${it.recording.id}" }) { item ->
                            RecordingChildItem(
                                recording = item.recording,
                                matchedCall = item.call,
                                onCardClick = { selectedRecordingForDetail = item.recording },
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedRecordingForDetail?.let { rec ->
        val call = callLogs.firstOrNull { it.id == rec.matchedCallId }
        RecordingDetailDialog(
            recording = rec,
            matchedCall = call,
            onDismiss = { selectedRecordingForDetail = null },
            onReUploadClick = {
                viewModel.uploadRecordingDirect(rec.id) { }
            }
        )
    }
}

@Composable
fun GroupHeaderCard(
    displayName: String,
    number: String,
    count: Int,
    initials: String,
    photoUri: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onToggleExpand
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
                if (displayName != number && number != "Unknown") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$count recording${if (count > 1) "s" else ""}",
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

@Composable
fun RecordingChildItem(
    recording: RecordingEntity,
    matchedCall: CallLogEntry?,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileExists = remember(recording.filePath) { File(recording.filePath).exists() }
    val isMatched = recording.matchStatus == MatchStatus.MATCHED && matchedCall != null

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onCardClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = when {
                    !fileExists -> Red500
                    isMatched -> Green500
                    else -> Amber500
                },
                modifier = Modifier
                    .size(32.dp)
                    .background(Slate950, CircleShape)
                    .padding(6.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.lastModified.toDateString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Slate50
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recording.duration.toInt().toDurationString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Indicators Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Match indicator
                    StatusBadge(
                        text = if (isMatched) "Matched" else "Unmatched",
                        containerColor = if (isMatched) Green500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f),
                        contentColor = if (isMatched) Green500 else Amber500
                    )

                    // Upload indicator
                    if (isMatched) {
                        val uploadText = when (recording.uploadStatus) {
                            UploadStatus.UPLOADED -> "Uploaded"
                            UploadStatus.PENDING -> "Pending"
                            UploadStatus.FAILED -> "Failed"
                        }
                        val uploadColor = when (recording.uploadStatus) {
                            UploadStatus.UPLOADED -> Green500
                            UploadStatus.PENDING -> Slate400
                            UploadStatus.FAILED -> Red500
                        }
                        StatusBadge(
                            text = uploadText,
                            containerColor = uploadColor.copy(alpha = 0.15f),
                            contentColor = uploadColor
                        )
                    }

                    // Missing file existence warning
                    if (!fileExists) {
                        StatusBadge(
                            text = "File Missing",
                            containerColor = Red500.copy(alpha = 0.15f),
                            contentColor = Red500
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = Modifier.align(Alignment.Center).let { MaterialTheme.typography.labelSmall },
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
fun RecordingDetailDialog(
    recording: RecordingEntity,
    matchedCall: CallLogEntry?,
    onDismiss: () -> Unit,
    onReUploadClick: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val file = remember(recording.filePath) { File(recording.filePath) }
    val fileExists = remember(file) { file.exists() }
    val isMatched = recording.matchStatus == MatchStatus.MATCHED && matchedCall != null

    LaunchedEffect(isPlaying) {
        if (isPlaying && fileExists) {
            try {
                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer().apply {
                        val fis = java.io.FileInputStream(file)
                        setDataSource(fis.fd)
                        prepare()
                        fis.close()
                    }
                }
                mediaPlayer?.start()
                while (isPlaying && mediaPlayer?.isPlaying == true) {
                    val progress = mediaPlayer!!.currentPosition.toFloat() / mediaPlayer!!.duration.toFloat()
                    playProgress = progress.coerceIn(0f, 1f)
                    delay(100)
                }
                if (mediaPlayer?.isPlaying == false && playProgress >= 0.95f) {
                    isPlaying = false
                    playProgress = 0f
                    mediaPlayer?.seekTo(0)
                }
            } catch (e: Exception) {
                android.util.Log.e("RecordingDetailDialog", "Playback failed", e)
                Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
                isPlaying = false
            }
        } else {
            mediaPlayer?.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Recording Details",
                color = Slate50,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Playback Slider (enabled if file exists)
                if (fileExists) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Slate950, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        IconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Teal300
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { playProgress },
                            color = Teal300,
                            trackColor = Slate700,
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Red500.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Red500)
                            Text(
                                text = "Audio file not found on device storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Red500,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Attributes List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SimpleDetailRow("Filename", recording.fileName)
                    SimpleDetailRow("Path", recording.filePath)

                    if (isMatched) {
                        SimpleDetailRow("Contact", matchedCall?.name ?: "Unknown")
                        SimpleDetailRow("Matched Call Phone", matchedCall?.number ?: "Unknown")
                    } else {
                        if (recording.phoneExtracted != null) {
                            SimpleDetailRow("Extracted Phone", recording.phoneExtracted)
                        }
                        if (recording.contactExtracted != null) {
                            SimpleDetailRow("Extracted Contact", recording.contactExtracted)
                        }
                    }

                    SimpleDetailRow("Date", recording.lastModified.toDateString())
                    SimpleDetailRow("Duration", recording.duration.toInt().toDurationString())

                    SimpleDetailRow("Status", when {
                        !fileExists -> "File Missing"
                        isMatched -> "Matched & Linked"
                        else -> "Unmatched"
                    })

                    if (isMatched && fileExists) {
                        SimpleDetailRow("Sync State", when (recording.uploadStatus) {
                            UploadStatus.UPLOADED -> "Uploaded"
                            UploadStatus.PENDING -> "Pending Upload"
                            UploadStatus.FAILED -> "Failed"
                        })
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isMatched && fileExists) {
                    Button(
                        onClick = onReUploadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Teal500),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Re-upload")
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
                }
            }
        },
        containerColor = Slate900,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SimpleDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate400)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Slate50, fontWeight = FontWeight.Medium)
    }
}
