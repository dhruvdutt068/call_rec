package com.example.callog.presentation.screens.settings

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
import com.example.callog.domain.model.FirebaseConfig
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.provider.DocumentsContract
import android.os.Environment
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.callog.core.diagnostics.DeveloperLogger
import java.io.File

@Composable
fun SettingsScreen(
    viewModel: CallViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onNavigateToDeveloperDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var developerModeClicks by remember { mutableStateOf(0) }
    var showDeveloperPinDialog by remember { mutableStateOf(false) }
    var developerPinInput by remember { mutableStateOf("") }
    var isDeveloperModeActive by remember { mutableStateOf(DeveloperLogger.isDeveloperModeEnabled) }
    
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val physicalPath = try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                val type = split[0]
                if (split.size > 1) {
                    val relativePath = split[1]
                    if ("primary".equals(type, ignoreCase = true)) {
                        Environment.getExternalStorageDirectory().toString() + "/" + relativePath
                    } else {
                        var sdCardPath: String? = null
                        val externalDirs = context.getExternalFilesDirs(null)
                        for (dir in externalDirs) {
                            if (dir != null) {
                                val path = dir.absolutePath
                                val index = path.indexOf("/Android/data/")
                                if (index != -1) {
                                    val root = path.substring(0, index)
                                    if (!root.contains("emulated")) {
                                        val testFile = File(root, relativePath)
                                        if (testFile.exists() || testFile.isDirectory) {
                                            sdCardPath = testFile.absolutePath
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        sdCardPath ?: uri.toString()
                    }
                } else {
                    uri.toString()
                }
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Failed to resolve tree URI to physical path", e)
                uri.toString()
            }
            
            viewModel.saveCustomRecordingPath(physicalPath)
        }
    }
    
    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    var showSimWizardDialog by remember { mutableStateOf(false) }
    val activeSims by viewModel.activeSims.collectAsState()
    val selectedSimId by viewModel.selectedSimId.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val selectedSimCarrier by viewModel.selectedSimCarrier.collectAsState()
    val selectedSimDisplayName by viewModel.selectedSimDisplayName.collectAsState()
    val selectedSimPhoneNumber by viewModel.selectedSimPhoneNumber.collectAsState()
    
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

        // 1.5. Business SIM Configuration
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Business SIM Filtering",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = Teal300,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Filter Calls by SIM", color = Slate50, fontWeight = FontWeight.SemiBold)
                                if (selectedSimId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                                    val simLabel = if (selectedSimDisplayName.isNotEmpty()) selectedSimDisplayName else selectedSimCarrier
                                    Text("Selected: $simLabel (SIM ${selectedSimSlot + 1})\n$selectedSimPhoneNumber", color = Teal300, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Not configured - Syncing suspended", color = Red500, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        
                        Button(
                            onClick = { showSimWizardDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Configure", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }

                }
            }
        }


        // 2b. Device Owner Configuration
        val deviceNumState by viewModel.devicePhoneNumber.collectAsState()
        val deviceOwnerNameState by viewModel.deviceOwnerName.collectAsState()

        var isConfigUnlocked by remember { mutableStateOf(false) }
        var showPasswordDialog by remember { mutableStateOf(false) }
        var passwordInput by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf(false) }

        var deviceNumberInput by remember { mutableStateOf("") }
        var deviceOwnerNameInput by remember { mutableStateOf("") }

        LaunchedEffect(deviceNumState, deviceOwnerNameState) {
            deviceNumberInput = deviceNumState
            deviceOwnerNameInput = deviceOwnerNameState
        }

        if (showPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPasswordDialog = false
                    passwordInput = ""
                    passwordError = false
                },
                title = {
                    Text(
                        text = "Admin Access Required",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Please enter the admin password to unlock device configurations.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                passwordError = false
                            },
                            label = {
                                Text(
                                    "Admin Password",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = passwordError,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            singleLine = true
                        )
                        if (passwordError) {
                            Text(
                                text = "Incorrect Admin Password",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (passwordInput == "C@11L0gs") {
                                isConfigUnlocked = true
                                showPasswordDialog = false
                                passwordInput = ""
                                passwordError = false
                            } else {
                                passwordError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Unlock", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPasswordDialog = false
                            passwordInput = ""
                            passwordError = false
                        }
                    ) {
                        Text(
                            "Cancel",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                tonalElevation = 6.dp
            )
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
                        text = "Specify the owner name and phone number of this device to identify and group synced logs in Firestore and local tracebacks. Admin access is required to edit.",
                        color = Slate400,
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = deviceNumberInput,
                        onValueChange = { deviceNumberInput = it },
                        readOnly = !isConfigUnlocked,
                        label = { Text("Phone Number of this Device", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isConfigUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isConfigUnlocked) Teal300 else Slate400
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            disabledTextColor = Slate400,
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Teal300,
                            cursorColor = Teal300
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = deviceOwnerNameInput,
                        onValueChange = { deviceOwnerNameInput = it },
                        readOnly = !isConfigUnlocked,
                        label = { Text("Owner Name of this Device", color = Slate400) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate50,
                            unfocusedTextColor = Slate50,
                            disabledTextColor = Slate400,
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700,
                            focusedLabelColor = Teal300,
                            cursorColor = Teal300
                        ),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isConfigUnlocked) {
                            Button(
                                onClick = { showPasswordDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Teal500,
                                    contentColor = Slate50
                                )
                            ) {
                                Text("Unlock Settings", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.saveDevicePhoneNumber(deviceNumberInput.trim())
                                    viewModel.saveDeviceOwnerName(deviceOwnerNameInput.trim())
                                    isConfigUnlocked = false
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Teal500,
                                    contentColor = Slate50
                                )
                            ) {
                                Text("Save Settings", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { isConfigUnlocked = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Slate700,
                                    contentColor = Slate50
                                )
                            ) {
                                Text("Lock Settings", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        val customPathState by viewModel.customRecordingPath.collectAsState()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recording Storage Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Select a folder from your device's storage to scan for call recordings.",
                        color = Slate400,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (customPathState.isNotEmpty()) "Selected Folder Path:" else "No folder selected",
                                color = Slate400,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (customPathState.isNotEmpty()) {
                                Text(
                                    text = customPathState,
                                    color = Slate50,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = { folderPickerLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal500,
                                contentColor = Slate50
                            )
                        ) {
                            Text(
                                text = if (customPathState.isNotEmpty()) "Change" else "Choose",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }


        // 5. Developer Diagnostics
        if (isDeveloperModeActive) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Developer Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )

                GlassyCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Access hidden telemetry, synchronization timelines, database logs and system metrics.",
                            color = Slate400,
                            style = MaterialTheme.typography.bodySmall
                        )

                        Button(
                            onClick = onNavigateToDeveloperDashboard,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal500,
                                contentColor = Slate50
                            )
                        ) {
                            Text("Open Developer Tools", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isDeveloperModeActive) {
                        developerModeClicks++
                        if (developerModeClicks >= 7) {
                            showDeveloperPinDialog = true
                            developerModeClicks = 0
                        }
                    }
                }
        )
    }

    if (showDeveloperPinDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperPinDialog = false },
            title = { Text("Developer Access", color = Slate50) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter developer PIN to access diagnostic tools:", color = Slate300)
                    OutlinedTextField(
                        value = developerPinInput,
                        onValueChange = { developerPinInput = it },
                        label = { Text("Developer PIN") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (developerPinInput == "9852") {
                            DeveloperLogger.isDeveloperModeEnabled = true
                            isDeveloperModeActive = true
                            showDeveloperPinDialog = false
                            developerPinInput = ""
                            Toast.makeText(context, "Developer tools unlocked!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500)
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeveloperPinDialog = false }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900
        )
    }

    if (showSimWizardDialog) {
        com.example.callog.presentation.components.BusinessSimWizardDialog(
            activeSims = activeSims,
            onSelectSim = { sim ->
                viewModel.saveBusinessSim(sim)
                showSimWizardDialog = false
            },
            onSelectSyncAll = {
                viewModel.saveSyncAll()
                showSimWizardDialog = false
            },
            isSuspendedDueToChange = false,
            onDismiss = { showSimWizardDialog = false }
        )
    }
}