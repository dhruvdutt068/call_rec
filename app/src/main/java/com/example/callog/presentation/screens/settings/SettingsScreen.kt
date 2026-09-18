package com.example.callog.presentation.screens.settings

import androidx.compose.foundation.BorderStroke
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CallViewModel,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onNavigateToDeveloperDashboard: () -> Unit,
    onNavigateToRingtoneSettings: (() -> Unit)? = null,
    onNavigateToEnvironmentPresets: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var developerModeClicks by remember { mutableStateOf(0) }
    var showDeveloperPinDialog by remember { mutableStateOf(false) }
    var developerPinInput by remember { mutableStateOf("") }
    val isDeveloperModeActive by viewModel.isDeveloperModeActive.collectAsState()
    val autoLockTimeoutMinutes by viewModel.autoLockTimeoutMinutes.collectAsState()

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

    var isDefaultDialer by remember { mutableStateOf(com.example.callog.core.telecom.TelecomRoleHelper.isDefaultDialer(context)) }
    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isDefaultDialer = com.example.callog.core.telecom.TelecomRoleHelper.isDefaultDialer(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

        // 1.2. Call Experience & Default Phone Role
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Call Experience",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDefaultDialer) Green500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneInTalk,
                                    contentDescription = null,
                                    tint = if (isDefaultDialer) Green500 else Amber500,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Default Phone App", color = Slate50, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isDefaultDialer) "Enabled — Custom CRM call UI & status ringtones active" else "Not default — Required for custom call screen",
                                    color = if (isDefaultDialer) Green500 else Slate400,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (!isDefaultDialer) {
                            Button(
                                onClick = {
                                    val intent = com.example.callog.core.telecom.TelecomRoleHelper.createRequestDialerRoleIntent(context)
                                    if (intent != null) {
                                        roleLauncher.launch(intent)
                                    } else {
                                        Toast.makeText(context, "Role manager not available", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Set Default", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Green500.copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Green500
                                )
                            }
                        }
                    }

                    Text(
                        text = "Set Callog as your default phone app to enable custom CRM caller identification, active in-call controls, locked-screen overlay, and status-based ringtones. Emergency calls will always route normally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )

                    if (onNavigateToRingtoneSettings != null) {
                        HorizontalDivider(color = Slate800)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToRingtoneSettings() }
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AllSetBlue.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = AllSetLavender,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text("CRM Ringtone Rules", color = Slate50, fontWeight = FontWeight.SemiBold)
                                    Text("Distinct tones for Hot, Warm & Customer calls", color = Slate400, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
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

                        // Auto-Lock Inactivity Setting
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Auto-Lock Environment",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate50
                                )
                                Text(
                                    text = "${autoLockTimeoutMinutes} min idle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Teal300,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Locks preferences & developer tools automatically when not in use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(1, 3, 5, 10, 15).forEach { minutes ->
                                    val isSelected = autoLockTimeoutMinutes == minutes
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setAutoLockTimeoutMinutes(minutes)
                                            Toast.makeText(context, "Auto-lock set to $minutes min", Toast.LENGTH_SHORT).show()
                                        },
                                        label = { Text("${minutes}m") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Teal500.copy(alpha = 0.25f),
                                            selectedLabelColor = Teal300,
                                            containerColor = Slate800,
                                            labelColor = Slate300
                                        )
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.recordDeveloperActivity()
                                onNavigateToDeveloperDashboard()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Teal500,
                                contentColor = Slate50
                            )
                        ) {
                            Text("Open Developer Tools", fontWeight = FontWeight.Bold)
                        }

                        if (onNavigateToEnvironmentPresets != null) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.recordDeveloperActivity()
                                    onNavigateToEnvironmentPresets()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Teal300
                                ),
                                border = BorderStroke(1.dp, Teal500.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Manage Environment Presets", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.setDeveloperModeActive(false)
                                Toast.makeText(context, "Preferences & Tools locked", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Amber500
                            ),
                            border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lock Environment Now", fontWeight = FontWeight.SemiBold)
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
                            viewModel.setDeveloperModeActive(true)
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