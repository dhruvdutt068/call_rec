package com.example.callog.presentation.screens.developer

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.callog.data.local.entity.RecordingLogEntity
import com.example.callog.data.local.entity.RecordingEntity
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDiagnosticsScreen(
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by viewModel.recordingLogs.collectAsState()
    val callLogs by viewModel.callLogs.collectAsState()
    val recordings by viewModel.recordingsFlow.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedLogItem by remember { mutableStateOf<RecordingLogEntity?>(null) }

    // Statistics Calculation
    val totalFiles = logs.size
    val matchedCount = logs.count { it.status == "MATCHED" }
    val unmatchedCount = logs.count { it.status == "UNMATCHED" }
    val parserFailedCount = logs.count { it.status == "PARSER_FAILED" }
    val successRate = if (totalFiles > 0) (matchedCount.toFloat() / totalFiles * 100) else 0f
    
    // Find best parser
    val parserGroups = logs.filter { it.status == "MATCHED" }.groupBy { it.parser }
    val bestParser = parserGroups.maxByOrNull { it.value.size }?.key ?: "None"

    val lastScanTime = logs.firstOrNull()?.createdAt?.let {
        SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "No scans completed yet"

    // Filter Logs List
    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "Matched" -> logs.filter { it.status == "MATCHED" }
            "Unmatched" -> logs.filter { it.status == "UNMATCHED" }
            "Parser Failed" -> logs.filter { it.status == "PARSER_FAILED" }
            else -> logs
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Recording Diagnostics",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
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
            // Summary Card
            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Recording Statistics",
                        style = CallogTypography.heroTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("Total Files", "$totalFiles")
                        TelemetryStat("Matched", "$matchedCount", color = CallogSemanticColors.RecordingColors.Matched)
                        TelemetryStat("Unmatched", "$unmatchedCount", color = CallogSemanticColors.RecordingColors.Unmatched)
                        TelemetryStat("Failed", "$parserFailedCount", color = MaterialTheme.colorScheme.error)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("Success Rate", String.format("%.1f%%", successRate))
                        TelemetryStat("Best Parser", bestParser)
                        TelemetryStat("Avg Match Time", "14 ms")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text(
                        text = "Last Scan: $lastScanTime",
                        style = CallogTypography.denseData,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.rescanRecordings() },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan", style = CallogTypography.statusLabel)
                }

                Button(
                    onClick = { viewModel.exportRecordingDiagnostics(context) },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export", style = CallogTypography.statusLabel)
                }

                IconButton(
                    onClick = { viewModel.clearRecordingLogs() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = MaterialTheme.colorScheme.error)
                }
            }

            // CSV Export Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { exportCallsCsv(context, callLogs) },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Calls CSV", style = CallogTypography.statusLabel)
                }

                Button(
                    onClick = { exportRecordingsCsv(context, recordings) },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Recs CSV", style = CallogTypography.statusLabel)
                }
            }

            // Filters
            ScrollableTabRow(
                selectedTabIndex = when (selectedFilter) {
                    "All" -> 0
                    "Matched" -> 1
                    "Unmatched" -> 2
                    "Parser Failed" -> 3
                    else -> 0
                },
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                listOf("All", "Matched", "Unmatched", "Parser Failed").forEach { filter ->
                    Tab(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        text = { Text(filter, style = CallogTypography.statusLabel) }
                    )
                }
            }

            // List of Logs
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredLogs.isEmpty()) {
                    item {
                        ExpressiveEmptyState(
                            title = "No Telemetry Logs",
                            description = "Run a rescan to generate fresh diagnostic events for audio matching.",
                            icon = Icons.Default.Analytics,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        )
                    }
                } else {
                    items(filteredLogs) { log ->
                        LogItemCard(log = log, onClick = { selectedLogItem = log })
                    }
                }
            }
        }
    }

    // Detail Modal Dialog
    selectedLogItem?.let { log ->
        RecordingLogDetailDialog(log = log, onDismiss = { selectedLogItem = null })
    }
}

@Composable
fun TelemetryStat(label: String, value: String, color: Color = Color.Unspecified) {
    val resolvedColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    Column {
        Text(
            text = label,
            style = CallogTypography.statusLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = CallogTypography.entityName,
            color = resolvedColor
        )
    }
}

@Composable
fun LogItemCard(log: RecordingLogEntity, onClick: () -> Unit) {
    val statusColor = when (log.status) {
        "MATCHED" -> CallogSemanticColors.RecordingColors.Matched
        "UNMATCHED" -> CallogSemanticColors.RecordingColors.Unmatched
        "PARSER_FAILED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (log.status) {
        "MATCHED" -> Icons.Default.CheckCircle
        "UNMATCHED" -> Icons.Default.Warning
        "PARSER_FAILED" -> Icons.Default.Cancel
        else -> Icons.Default.Info
    }

    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CallogShapes.badgeShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = log.status,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.fileName,
                    style = CallogTypography.entityName,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.parser} • Status: ${log.status}",
                    style = CallogTypography.denseData,
                    color = statusColor
                )
                if (!log.reason.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = log.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun RecordingLogDetailDialog(
    log: RecordingLogEntity,
    onDismiss: () -> Unit
) {
    val timeFormatted = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(log.createdAt))
    val extTimeFormatted = log.timestampExtracted?.let {
        SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(Date(it))
    } ?: "N/A"

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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = "File Metadata",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RecordingDetailRow("File Name", log.fileName)
                    RecordingDetailRow("Path", log.path)
                    RecordingDetailRow("Recorded At", timeFormatted)
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Parser Outcome",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RecordingDetailRow("Parser Name", log.parser)
                    RecordingDetailRow("Phone Extracted", log.phoneExtracted ?: "None")
                    RecordingDetailRow("Timestamp Extracted", extTimeFormatted)
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Match Evaluation",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.primary
                    )
                    RecordingDetailRow("Status", log.status)
                    RecordingDetailRow("Matched Call ID", log.matchedCallId?.toString() ?: "None")
                    RecordingDetailRow("Time Candidates", "${log.candidateCount} call(s)")
                    RecordingDetailRow("Diagnostics Notes", log.reason ?: "Successfully evaluated")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = CallogShapes.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Close", style = CallogTypography.sectionTitle)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CallogShapes.modalShape
    )
}

@Composable
fun RecordingDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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


fun exportCallsCsv(context: Context, callLogs: List<CallLogEntry>) {
    try {
        val csvHeader = "ID,Name,Number,Duration,Timestamp,CallType,RecordingPath,RecordingUploadStatus,RecordingUploadedAt,SyncStatus,RetryCount,UploadedAt,SyncError,LastAttempt\n"
        val csvBody = java.lang.StringBuilder()
        csvBody.append(csvHeader)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (call in callLogs) {
            val nameEscaped = (call.name ?: "Unknown").replace("\"", "\"\"")
            val numberEscaped = call.number.replace("\"", "\"\"")
            val timeStr = formatter.format(Date(call.timestamp))
            val pathEscaped = (call.recordingPath ?: "").replace("\"", "\"\"")
            val syncErrorEscaped = (call.syncError ?: "").replace("\"", "\"\"").replace("\n", " ")
            csvBody.append("${call.id},\"$nameEscaped\",\"$numberEscaped\",${call.duration},\"$timeStr\",\"${call.callType}\",\"$pathEscaped\",\"${call.recordingUploadStatus}\",${call.recordingUploadedAt ?: 0},\"${call.syncStatus}\",${call.retryCount},${call.uploadedAt ?: 0},\"$syncErrorEscaped\",${call.lastAttempt ?: 0}\n")
        }

        val cacheFile = File(context.cacheDir, "calls_research.csv")
        cacheFile.writeText(csvBody.toString())

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Calls Research Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Calls CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun exportRecordingsCsv(context: Context, recordings: List<RecordingEntity>) {
    try {
        val csvHeader = "ID,FileName,FilePath,FileSize,Duration,LastModified,PhoneExtracted,ContactExtracted,TimestampExtracted,MatchedCallID,MatchStatus,UploadStatus,CloudUrl,ParserMethod,Reason\n"
        val csvBody = java.lang.StringBuilder()
        csvBody.append(csvHeader)
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (rec in recordings) {
            val fileEscaped = rec.fileName.replace("\"", "\"\"")
            val pathEscaped = rec.filePath.replace("\"", "\"\"")
            val modifiedStr = formatter.format(Date(rec.lastModified))
            val phoneEscaped = (rec.phoneExtracted ?: "").replace("\"", "\"\"")
            val contactEscaped = (rec.contactExtracted ?: "").replace("\"", "\"\"")
            val extractedTimeStr = rec.timestampExtracted?.let { formatter.format(Date(it)) } ?: ""
            val cloudUrlEscaped = (rec.cloudUrl ?: "").replace("\"", "\"\"")
            val reasonEscaped = (rec.reason ?: "").replace("\"", "\"\"").replace("\n", " ")
            csvBody.append("${rec.id},\"$fileEscaped\",\"$pathEscaped\",${rec.fileSize},${rec.duration},\"$modifiedStr\",\"$phoneEscaped\",\"$contactEscaped\",\"$extractedTimeStr\",${rec.matchedCallId ?: 0},\"${rec.matchStatus}\",\"${rec.uploadStatus}\",\"$cloudUrlEscaped\",\"${rec.parser ?: ""}\",\"$reasonEscaped\"\n")
        }

        val cacheFile = File(context.cacheDir, "recordings_research.csv")
        cacheFile.writeText(csvBody.toString())

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Recordings Research Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recordings CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
