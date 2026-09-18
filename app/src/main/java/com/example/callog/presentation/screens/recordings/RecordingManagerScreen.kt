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
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import com.example.callog.core.extensions.toDateString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.data.local.entity.MatchStatus
import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.data.local.entity.UploadStatus
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.ExpressiveEmptyState
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
    onCallClick: ((Long) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    val recordings by viewModel.recordingsFlow.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()

    RecordingManagerContent(
        recordings = recordings,
        callLogs = callLogs,
        onRescan = { viewModel.rescanRecordings() },
        onReUpload = { recordingId -> viewModel.uploadRecordingDirect(recordingId) {} },
        onCallClick = onCallClick,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingManagerContent(
    recordings: List<RecordingEntity>,
    callLogs: List<CallLogEntry>,
    onRescan: () -> Unit,
    onReUpload: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onCallClick: ((Long) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recordings Vault",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRescan) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan Recordings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            if (recordings.isEmpty()) {
                ExpressiveEmptyState(
                    title = "No Recordings Found",
                    description = "Ensure that recordings are stored in the configured directory, then tap the rescan button to match with your calls.",
                    icon = Icons.Default.GraphicEq,
                    actionLabel = "Rescan Device Storage",
                    onActionClick = onRescan,
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
    }

    // Detail Dialog
    selectedRecordingForDetail?.let { rec ->
        val call = callLogs.firstOrNull { it.id == rec.matchedCallId }
        RecordingDetailDialog(
            recording = rec,
            matchedCall = call,
            onDismiss = { selectedRecordingForDetail = null },
            onReUploadClick = {
                onReUpload(rec.id)
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
                modifier = Modifier.size(46.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = CallogTypography.entityName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (displayName != number && number != "Unknown") {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(CallogShapes.pillShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$count recording${if (count > 1) "s" else ""}",
                            style = CallogTypography.statusLabel,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

    val micTint = when {
        !fileExists -> CallogSemanticColors.RecordingColors.Corrupted
        isMatched -> CallogSemanticColors.RecordingColors.Matched
        else -> CallogSemanticColors.RecordingColors.Unmatched
    }

    GlassyCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onCardClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CallogShapes.badgeShape)
                    .background(micTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = micTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.lastModified.toDateString(),
                    style = CallogTypography.entityName,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = recording.duration.toInt().toDurationString(),
                    style = CallogTypography.denseData,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        containerColor = if (isMatched) CallogSemanticColors.RecordingColors.Matched.copy(alpha = 0.15f) else CallogSemanticColors.RecordingColors.Unmatched.copy(alpha = 0.15f),
                        contentColor = if (isMatched) CallogSemanticColors.RecordingColors.Matched else CallogSemanticColors.RecordingColors.Unmatched
                    )

                    // Upload indicator
                    if (isMatched) {
                        val uploadText = when (recording.uploadStatus) {
                            UploadStatus.UPLOADED -> "Uploaded"
                            UploadStatus.PENDING -> "Pending"
                            UploadStatus.FAILED -> "Failed"
                        }
                        val uploadColor = when (recording.uploadStatus) {
                            UploadStatus.UPLOADED -> CallogSemanticColors.SyncColors.Synced
                            UploadStatus.PENDING -> CallogSemanticColors.SyncColors.Pending
                            UploadStatus.FAILED -> CallogSemanticColors.SyncColors.Failed
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
                            containerColor = CallogSemanticColors.RecordingColors.Corrupted.copy(alpha = 0.15f),
                            contentColor = CallogSemanticColors.RecordingColors.Corrupted
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
            .background(containerColor, CallogShapes.pillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = CallogTypography.statusLabel,
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
                text = "Recording Details",
                style = CallogTypography.heroTitle,
                color = MaterialTheme.colorScheme.onSurface
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
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CallogShapes.cardShape)
                            .padding(10.dp)
                    ) {
                        IconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        LinearProgressIndicator(
                            progress = { playProgress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
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
                            .background(CallogSemanticColors.RecordingColors.Corrupted.copy(alpha = 0.12f), CallogShapes.cardShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = CallogSemanticColors.RecordingColors.Corrupted
                            )
                            Text(
                                text = "Audio file not found on device storage.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CallogSemanticColors.RecordingColors.Corrupted,
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = CallogShapes.buttonShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Re-upload", style = CallogTypography.sectionTitle)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = CallogShapes.buttonShape,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close", style = CallogTypography.sectionTitle)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CallogShapes.modalShape
    )
}

@Composable
fun SimpleDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = CallogTypography.statusLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = CallogTypography.denseData,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Recording Manager Screen", showBackground = true)
@Preview(name = "Recording Manager Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun RecordingManagerPreview() {
    CallogTheme {
        RecordingManagerContent(
            recordings = listOf(
                RecordingEntity(
                    id = 1L,
                    fileName = "Call_Recording_Jane_Doe_2026.m4a",
                    filePath = "/storage/emulated/0/Recordings/Call_Jane_Doe.m4a",
                    fileSize = 1024000L,
                    duration = 145000L,
                    lastModified = System.currentTimeMillis() - 1000 * 60 * 30,
                    phoneExtracted = "+1 (555) 345-6789",
                    contactExtracted = "Jane Doe",
                    timestampExtracted = System.currentTimeMillis() - 1000 * 60 * 30,
                    matchStatus = MatchStatus.MATCHED,
                    matchedCallId = 101L,
                    uploadStatus = UploadStatus.UPLOADED
                ),
                RecordingEntity(
                    id = 2L,
                    fileName = "Call_Recording_Unknown_Number.m4a",
                    filePath = "/storage/emulated/0/Recordings/Unknown.m4a",
                    fileSize = 512000L,
                    duration = 45000L,
                    lastModified = System.currentTimeMillis() - 1000 * 60 * 120,
                    phoneExtracted = "+1 (555) 999-0000",
                    contactExtracted = null,
                    timestampExtracted = System.currentTimeMillis() - 1000 * 60 * 120,
                    matchStatus = MatchStatus.UNMATCHED,
                    matchedCallId = null,
                    uploadStatus = UploadStatus.PENDING
                )
            ),
            callLogs = listOf(
                CallLogEntry(
                    id = 101L,
                    name = "Jane Doe",
                    number = "+1 (555) 345-6789",
                    duration = 145,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 30,
                    callType = "INCOMING",
                    recordingPath = "/storage/emulated/0/Recordings/Call_Jane_Doe.m4a",
                    isFavorite = true,
                    notes = null,
                    tags = listOf("work"),
                    contactPhotoUri = null
                )
            ),
            onRescan = {},
            onReUpload = {}
        )
    }
}

@Preview(name = "Group Header Card", showBackground = true)
@Composable
fun GroupHeaderCardPreview() {
    CallogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            GroupHeaderCard(
                displayName = "Jane Doe",
                number = "+1 (555) 345-6789",
                count = 2,
                initials = "JD",
                photoUri = null,
                isExpanded = true,
                onToggleExpand = {}
            )
        }
    }
}

@Preview(name = "Recording Child Item", showBackground = true)
@Composable
fun RecordingChildItemPreview() {
    CallogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            RecordingChildItem(
                recording = RecordingEntity(
                    id = 1L,
                    fileName = "Call_Recording_Jane_Doe.m4a",
                    filePath = "/storage/emulated/0/Recordings/rec.m4a",
                    fileSize = 1024000L,
                    duration = 124000L,
                    lastModified = System.currentTimeMillis(),
                    phoneExtracted = "+1 555 345 6789",
                    contactExtracted = "Jane Doe",
                    timestampExtracted = System.currentTimeMillis(),
                    matchStatus = MatchStatus.MATCHED,
                    matchedCallId = 1L,
                    uploadStatus = UploadStatus.UPLOADED
                ),
                matchedCall = CallLogEntry(
                    id = 1L,
                    name = "Jane Doe",
                    number = "+1 555 345 6789",
                    duration = 124,
                    timestamp = System.currentTimeMillis(),
                    callType = "INCOMING",
                    recordingPath = "/storage/emulated/0/Recordings/rec.m4a",
                    isFavorite = false,
                    notes = null,
                    tags = emptyList(),
                    contactPhotoUri = null
                ),
                onCardClick = {}
            )
        }
    }
}


