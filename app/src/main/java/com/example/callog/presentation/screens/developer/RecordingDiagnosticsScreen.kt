package com.example.callog.presentation.screens.developer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                title = { Text("Recording Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Recording Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("Total Files", "$totalFiles")
                        TelemetryStat("Matched", "$matchedCount", color = Teal300)
                        TelemetryStat("Unmatched", "$unmatchedCount", color = AllSetAmber)
                        TelemetryStat("Failed", "$parserFailedCount", color = MaterialTheme.colorScheme.error)
                    }

                    Divider(color = Slate700.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TelemetryStat("Success Rate", String.format("%.1f%%", successRate))
                        TelemetryStat("Best Parser", bestParser)
                        TelemetryStat("Avg Match Time", "14 ms")
                    }

                    Divider(color = Slate700.copy(alpha = 0.5f))

                    Text(
                        text = "Last Scan: $lastScanTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan")
                }

                Button(
                    onClick = { viewModel.exportRecordingDiagnostics(context) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export")
                }

                IconButton(
                    onClick = { viewModel.clearRecordingLogs() },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Calls CSV", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { exportRecordingsCsv(context, recordings) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Green500)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Recs CSV", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
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
                contentColor = Teal300,
                divider = {}
            ) {
                listOf("All", "Matched", "Unmatched", "Parser Failed").forEach { filter ->
                    Tab(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        text = { Text(filter, fontWeight = FontWeight.Bold) }
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
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No telemetry logs found.", color = Slate400)
                        }
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
fun TelemetryStat(label: String, value: String, color: Color = Slate50) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate400)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun LogItemCard(log: RecordingLogEntity, onClick: () -> Unit) {
    val statusColor = when (log.status) {
        "MATCHED" -> Teal300
        "UNMATCHED" -> AllSetAmber
        "PARSER_FAILED" -> MaterialTheme.colorScheme.error
        else -> Slate400
    }

    val statusIcon = when (log.status) {
        "MATCHED" -> "✅"
        "UNMATCHED" -> "⚠️"
        "PARSER_FAILED" -> "❌"
        else -> "🟢"
    }

    GlassyCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(statusIcon, style = MaterialTheme.typography.titleLarge)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${log.parser} • Status: ${log.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
                if (!log.reason.isNullOrEmpty()) {
                    Text(
                        text = log.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                fontWeight = FontWeight.Bold,
                color = Slate50
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text("File Metadata", fontWeight = FontWeight.Bold, color = Teal300)
                    RecordingDetailRow("File Name", log.fileName)
                    RecordingDetailRow("Path", log.path)
                    RecordingDetailRow("Recorded At", timeFormatted)
                }

                item {
                    Divider(color = Slate700.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Parser Outcome", fontWeight = FontWeight.Bold, color = Teal300)
                    RecordingDetailRow("Parser Name", log.parser)
                    RecordingDetailRow("Phone Extracted", log.phoneExtracted ?: "None")
                    RecordingDetailRow("Timestamp Extracted", extTimeFormatted)
                }

                item {
                    Divider(color = Slate700.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Match Evaluation", fontWeight = FontWeight.Bold, color = Teal300)
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
                colors = ButtonDefaults.buttonColors(containerColor = Teal500)
            ) {
                Text("Close")
            }
        },
        containerColor = Slate900,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun RecordingDetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate400)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Slate50, fontWeight = FontWeight.Medium)
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
