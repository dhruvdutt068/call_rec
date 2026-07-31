package com.example.callog.presentation.screens.developer

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.example.callog.core.constants.Constants
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDashboardScreen(
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToRecordingDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }
    val context = LocalContext.current
    val syncLogs by viewModel.syncLogs.collectAsState()
    val allCalls by viewModel.callLogs.collectAsState()
    val allSalesCalls by viewModel.allSalesCalls.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val pendingCallsCount = allCalls.count { it.syncStatus != "SYNCED" }
    val pendingSalesCallsCount = allSalesCalls.count { it.syncStatus != "SYNCED" }

    val lastSyncLog = syncLogs.firstOrNull { it.event == "SYNC_COMPLETED" || it.event == "SYNC_FAILED" }
    val lastSyncTime = lastSyncLog?.timestamp?.let {
        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
    } ?: "No synchronization recorded"
    val lastSyncStatus = lastSyncLog?.status ?: "PENDING"

    var dbSize by remember { mutableStateOf("Calculating...") }

    LaunchedEffect(Unit) {
        val dbFile = context.getDatabasePath(Constants.DATABASE_NAME)
        dbSize = if (dbFile.exists()) {
            val bytes = dbFile.length()
            if (bytes < 1024) "$bytes Bytes"
            else if (bytes < 1024 * 1024) String.format("%.2f KB", bytes / 1024.0)
            else String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        } else {
            "0 Bytes"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Diagnostics", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Description
            Text(
                text = "Diagnostics & System Telemetry",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )
            Text(
                text = "Use this interface to inspect local database metrics, verify API connections, review synchronization events, and export system diagnostic reports.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
            )

            // Section 1: Health & Sync Status Card
            DashboardSectionHeader(title = "Synchronization Health")
            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TelemetryRow(
                        icon = Icons.Default.Schedule,
                        iconTint = Teal300,
                        label = "Last Sync Attempt",
                        value = lastSyncTime
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Teal300,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sync Health Status",
                            color = Slate400,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (lastSyncStatus) {
                                "SUCCESS" -> Teal500.copy(alpha = 0.2f)
                                "FAILED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else -> Slate700.copy(alpha = 0.4f)
                            }
                        ) {
                            Text(
                                text = lastSyncStatus,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = when (lastSyncStatus) {
                                    "SUCCESS" -> Teal300
                                    "FAILED" -> MaterialTheme.colorScheme.error
                                    else -> Slate400
                                },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    TelemetryRow(
                        icon = Icons.Default.EventNote,
                        iconTint = Teal300,
                        label = "Local Diagnostic Logs",
                        value = "${syncLogs.size} logs stored"
                    )
                }
            }

            // Section 2: Database Stats Card
            DashboardSectionHeader(title = "Database Telemetry")
            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TelemetryRow(
                        icon = Icons.Default.CloudQueue,
                        iconTint = AllSetLavender,
                        label = "Pending Firestore Syncs",
                        value = "$pendingCallsCount calls",
                        valueColor = if (pendingCallsCount > 0) AllSetAmber else Slate50
                    )

                    TelemetryRow(
                        icon = Icons.Default.Storage,
                        iconTint = AllSetLavender,
                        label = "Pending Supabase Syncs",
                        value = "$pendingSalesCallsCount records",
                        valueColor = if (pendingSalesCallsCount > 0) AllSetAmber else Slate50
                    )

                    TelemetryRow(
                        icon = Icons.Default.SnippetFolder,
                        iconTint = AllSetLavender,
                        label = "Local SQLite DB Size",
                        value = dbSize
                    )
                }
            }

            // Section 3: System properties Card
            DashboardSectionHeader(title = "Hardware & Environment Spec")
            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TelemetryRow(
                        icon = Icons.Default.DeveloperMode,
                        iconTint = Slate400,
                        label = "Hardware Model",
                        value = "${Build.MANUFACTURER} ${Build.MODEL}"
                    )

                    TelemetryRow(
                        icon = Icons.Default.Android,
                        iconTint = Slate400,
                        label = "Android Release Version",
                        value = "OS ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )

                    TelemetryRow(
                        icon = Icons.Default.Code,
                        iconTint = Slate400,
                        label = "Application Version",
                        value = "1.0.0 (Release)"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Panel
            Button(
                onClick = onNavigateToLogs,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal500, contentColor = Slate50)
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Sync Timeline Logs", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onNavigateToRecordingDiagnostics,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal500, contentColor = Slate50)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Recording Diagnostics", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.forceReSync() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Force Reset & Sync Databases", fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.forceLogsCleanup() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cleanup Logs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.clearSyncLogs() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Logs", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DashboardSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Slate50,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
fun TelemetryRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    valueColor: Color = Slate50
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = Slate400,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = valueColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
