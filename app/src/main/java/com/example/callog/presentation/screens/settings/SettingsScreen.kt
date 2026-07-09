package com.example.callog.presentation.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: CallViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    val savedConfig by viewModel.firebaseConfig.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val lastUploadError by viewModel.lastUploadError.collectAsState()

    var projectIdInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var appIdInput by remember { mutableStateOf("") }

    LaunchedEffect(savedConfig) {
        projectIdInput = savedConfig?.projectId ?: ""
        apiKeyInput = savedConfig?.apiKey ?: ""
        appIdInput = savedConfig?.appId ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Slate50
        )

        // 1. Theme Configuration
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Teal500.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brightness4,
                                contentDescription = null,
                                tint = Teal300,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Dark Theme Mode", color = Slate50, fontWeight = FontWeight.SemiBold)
                            Text("Force Slate dark aesthetics", color = Slate400, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = onDarkThemeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Teal300,
                            checkedTrackColor = Teal500,
                            uncheckedThumbColor = Slate400,
                            uncheckedTrackColor = Slate800
                        )
                    )
                }
            }
        }


        // 2b. Device Owner Configuration
        val deviceNumState by viewModel.devicePhoneNumber.collectAsState()
        var deviceNumberInput by remember { mutableStateOf("") }
        LaunchedEffect(deviceNumState) {
            deviceNumberInput = deviceNumState
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Device Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Specify the phone number of this device to bifurcate the synced logs in Firestore under this device.",
                        color = Slate400,
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = deviceNumberInput,
                        onValueChange = { deviceNumberInput = it },
                        label = { Text("Phone Number of this Device", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Teal300,
                            cursorColor = Teal300
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            viewModel.saveDevicePhoneNumber(deviceNumberInput.trim())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal500,
                            contentColor = Slate50
                        )
                    ) {
                        Text("Save Phone Number", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 3. Database Management Controls
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Database Management",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Wipe Database Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showWipeConfirmDialog = true }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Red500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    tint = Red500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Clear Local Database", color = Red500, fontWeight = FontWeight.SemiBold)
                                Text("Wipe all logs, notes, and reminders", color = Slate400, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                    }
                }
            }
        }

        // 4. Technical Portfolio Attribution Card
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "System Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Teal300,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "CallVault Portfolio App",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Built using Clean Architecture + MVVM, Jetpack Compose, Room Database, Dagger Hilt DI, Coroutines Flow, Media3 audio player, custom canvas analytics rendering, and local callback schedulers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        textAlign = TextAlign.Center,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.3
                    )
                }
            }
        }
    }

    // Confirm dialog for Wiping Database
    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            title = { Text("Wipe Call Logs Database?", color = Slate50, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This action is irreversible. All local database logs, call notes, tags, and pending callback schedules will be deleted.",
                    color = Slate400
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showWipeConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red500)
                ) {
                    Text("Wipe All Data", color = Slate50, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate800
        )
    }
}