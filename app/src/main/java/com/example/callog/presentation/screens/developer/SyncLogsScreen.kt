package com.example.callog.presentation.screens.developer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.callog.data.local.entity.SyncLogEntity
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SyncSession(
    val syncId: String,
    val timestamp: Long,
    val status: String,
    val durationMs: Long,
    val logs: List<SyncLogEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogsScreen(
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val syncLogs by viewModel.syncLogs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevelFilter by remember { mutableStateOf("ALL") } // ALL, INFO, SUCCESS, WARNING, ERROR
    var selectedCategoryFilter by remember { mutableStateOf("ALL") } // ALL, FIRESTORE, SUPABASE, RECORDING

    var selectedLogForDetail by remember { mutableStateOf<SyncLogEntity?>(null) }
    var expandedSessions = remember { mutableStateMapOf<String, Boolean>() }

    // Filter logs first
    val filteredLogs = remember(syncLogs, searchQuery, selectedLevelFilter, selectedCategoryFilter) {
        syncLogs.filter { log ->
            val matchesSearch = searchQuery.isEmpty() ||
                    log.message.contains(searchQuery, ignoreCase = true) ||
                    log.event.contains(searchQuery, ignoreCase = true) ||
                    log.syncId.contains(searchQuery, ignoreCase = true) ||
                    (log.exception ?: "").contains(searchQuery, ignoreCase = true)

            val matchesLevel = selectedLevelFilter == "ALL" || log.level == selectedLevelFilter

            val matchesCategory = when (selectedCategoryFilter) {
                "FIRESTORE" -> log.event.contains("FIRESTORE", ignoreCase = true)
                "SUPABASE" -> log.event.contains("SUPABASE", ignoreCase = true)
                "RECORDING" -> log.event.contains("RECORDING", ignoreCase = true)
                else -> true
            }

            matchesSearch && matchesLevel && matchesCategory
        }
    }

    // Group logs into chronological sessions (preserving sorting order)
    val sessions = remember(filteredLogs) {
        val uniqueIds = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        filteredLogs.forEach { log ->
            if (log.syncId !in seen) {
                seen.add(log.syncId)
                uniqueIds.add(log.syncId)
            }
        }
        uniqueIds.map { id ->
            val sessionLogs = filteredLogs.filter { it.syncId == id }.sortedBy { it.timestamp }
            val startLog = sessionLogs.firstOrNull { it.event == "SYNC_STARTED" }
            val endLog = sessionLogs.lastOrNull { it.event == "SYNC_COMPLETED" || it.event == "SYNC_FAILED" }
            
            val timestamp = startLog?.timestamp ?: sessionLogs.firstOrNull()?.timestamp ?: 0L
            val status = endLog?.status ?: sessionLogs.lastOrNull()?.status ?: "SUCCESS"
            val duration = endLog?.durationMs ?: if (sessionLogs.size > 1) {
                sessionLogs.last().timestamp - sessionLogs.first().timestamp
            } else 0L

            SyncSession(id, timestamp, status, duration, sessionLogs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Timeline Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { exportLogsToCsv(context, filteredLogs) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export CSV")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { exportLogsToCsv(context, filteredLogs) },
                containerColor = Teal500,
                contentColor = Slate50
            ) {
                Icon(Icons.Default.Download, contentDescription = "Export CSV")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search logs (e.g. error message, sync ID)") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )

            // Filter Levels Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val levels = listOf("ALL", "INFO", "SUCCESS", "WARNING", "ERROR")
                levels.forEach { level ->
                    FilterChip(
                        selected = selectedLevelFilter == level,
                        onClick = { selectedLevelFilter = level },
                        label = { Text(level) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // Filter Categories Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categories = listOf("ALL", "FIRESTORE", "SUPABASE", "RECORDING")
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategoryFilter == category,
                        onClick = { selectedCategoryFilter = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sync sessions found.", color = Slate400)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(sessions) { session ->
                        val isExpanded = expandedSessions[session.syncId] ?: false
                        SessionCard(
                            session = session,
                            isExpanded = isExpanded,
                            onHeaderClick = { expandedSessions[session.syncId] = !isExpanded },
                            onLogClick = { log -> selectedLogForDetail = log }
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedLogForDetail?.let { log ->
        LogDetailDialog(log = log, onDismiss = { selectedLogForDetail = null })
    }
}

@Composable
fun SessionCard(
    session: SyncSession,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    onLogClick: (SyncLogEntity) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm:ss", Locale.getDefault()) }
    val timeString = formatter.format(Date(session.timestamp))

    val statusColor = when (session.status) {
        "SUCCESS" -> Teal300
        "FAILED" -> Color(0xFFEF5350)
        else -> Slate400
    }

    val statusIcon = when (session.status) {
        "SUCCESS" -> Icons.Default.CheckCircle
        "FAILED" -> Icons.Default.Error
        else -> Icons.Default.Help
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onHeaderClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sync Session: $timeString",
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Events: ${session.logs.size}",
                            color = Slate400,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (session.durationMs > 0) {
                            Text(
                                text = "Duration: ${String.format("%.2f s", session.durationMs / 1000.0)}",
                                color = Slate400,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Slate400
                )
            }

            // Timeline logs (if expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate950.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    session.logs.forEachIndexed { index, log ->
                        TimelineLogItem(
                            log = log,
                            isFirst = index == 0,
                            isLast = index == session.logs.size - 1,
                            onClick = { onLogClick(log) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineLogItem(
    log: SyncLogEntity,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val timeString = formatter.format(Date(log.timestamp))

    val dotColor = when (log.level) {
        "SUCCESS" -> Teal300
        "ERROR" -> Color(0xFFEF5350)
        "WARNING" -> Color(0xFFFFCA28)
        else -> Slate400
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Vertical timeline bar on the left
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            // Top connecting line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(if (isFirst) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
            )

            // Timeline dot indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            // Bottom connecting line
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else MaterialTheme.colorScheme.outlineVariant)
                    .padding(bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.event,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeString,
                    color = Slate400,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                color = Slate300,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (log.durationMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Latency: ${log.durationMs} ms",
                    color = Teal300,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun LogDetailDialog(log: SyncLogEntity, onDismiss: () -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
    val timeString = formatter.format(Date(log.timestamp))
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (log.level) {
                        "SUCCESS" -> Icons.Default.CheckCircle
                        "ERROR" -> Icons.Default.Error
                        "WARNING" -> Icons.Default.Warning
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (log.level) {
                        "SUCCESS" -> Teal300
                        "ERROR" -> Color(0xFFEF5350)
                        "WARNING" -> Color(0xFFFFCA28)
                        else -> Slate300
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(log.event, color = Slate50, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow("Timestamp", timeString)
                DetailRow("Sync Session ID", log.syncId)
                DetailRow("Level", log.level)
                DetailRow("Status", log.status)
                if (log.durationMs > 0) {
                    DetailRow("Duration", "${log.durationMs} ms")
                }
                DetailRow("Network", log.network)
                DetailRow("Device", log.deviceModel)
                DetailRow("Android OS", log.androidVersion)
                DetailRow("App Version", log.appVersion)

                Divider(color = Slate800)

                Text("Message:", fontWeight = FontWeight.Bold, color = Slate300, style = MaterialTheme.typography.bodyMedium)
                Text(log.message, color = Slate50, style = MaterialTheme.typography.bodyMedium)

                if (!log.exception.isNullOrEmpty() || !log.stacktrace.isNullOrEmpty()) {
                    Divider(color = Slate800)
                    Text("Error Telemetry:", fontWeight = FontWeight.Bold, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium)
                    log.exception?.let {
                        Text(it, color = Slate50, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    }

                    log.stacktrace?.let { trace ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Slate950)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = trace,
                                color = Slate400,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Stacktrace", trace)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Stacktrace copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp).padding(end = 4.dp))
                            Text("Copy Trace", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Teal500)
            ) {
                Text("Dismiss")
            }
        },
        containerColor = Slate900
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Slate400, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Slate50, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

fun exportLogsToCsv(context: Context, logs: List<SyncLogEntity>) {
    try {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val csvHeader = "ID,Sync ID,Timestamp,Level,Event,Status,Message,Exception,Network,Duration(ms),App Version,Device,OS\n"
        val csvBody = StringBuilder()
        csvBody.append(csvHeader)
        for (log in logs) {
            val timeStr = formatter.format(Date(log.timestamp))
            val escapedMsg = log.message.replace("\"", "\"\"").replace("\n", " ")
            val escapedExc = (log.exception ?: "").replace("\"", "\"\"").replace("\n", " ")
            csvBody.append("${log.id},${log.syncId},\"$timeStr\",\"${log.level}\",\"${log.event}\",\"${log.status}\",\"$escapedMsg\",\"$escapedExc\",\"${log.network}\",${log.durationMs},\"${log.appVersion}\",\"${log.deviceModel}\",\"${log.androidVersion}\"\n")
        }

        val cacheFile = File(context.cacheDir, "call_vault_sync_logs.csv")
        cacheFile.writeText(csvBody.toString())

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CallVault Sync Diagnostics Logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share logs CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
