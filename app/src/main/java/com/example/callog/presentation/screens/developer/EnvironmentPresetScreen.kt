package com.example.callog.presentation.screens.developer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.domain.model.preset.*
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.PresetUiEvent
import com.example.callog.presentation.viewmodel.PresetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentPresetScreen(
    onBackClick: () -> Unit,
    viewModel: PresetViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val presets by viewModel.presets.collectAsState()
    val activePreset by viewModel.activePreset.collectAsState()
    val activeSecrets by viewModel.activeSecrets.collectAsState()
    val isTestingConnection by viewModel.isTestingConnection.collectAsState()
    val connectionTestResult by viewModel.connectionTestResult.collectAsState()

    // Dialog & Sheet States
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showEditPresetSheet by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<AppPreset?>(null) }
    var editingSecrets by remember { mutableStateOf<PresetSecrets?>(null) }
    var showProdConfirmDialog by remember { mutableStateOf<AppPreset?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<AppPreset?>(null) }
    var showExportDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // (json, presetName)
    var showImportDialog by remember { mutableStateOf(false) }
    var exportIncludeSecrets by remember { mutableStateOf(false) }

    // Handle UI Events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is PresetUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is PresetUiEvent.RequestProductionConfirmation -> {
                    showProdConfirmDialog = event.preset
                }
                is PresetUiEvent.PresetExported -> {
                    showExportDialog = Pair(event.json, event.presetName)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Environment Presets", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Centralized backend & credential profiles",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Import Preset", tint = Teal300)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Slate900,
                    titleContentColor = Slate50,
                    navigationIconContentColor = Slate50
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplateDialog = true },
                containerColor = Teal500,
                contentColor = Slate50,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Preset", fontWeight = FontWeight.Bold) }
            )
        },
        containerColor = Slate950
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            // 1. Active Environment Showcase Card
            item {
                ActiveEnvironmentShowcaseCard(
                    preset = activePreset,
                    secrets = activeSecrets,
                    isTesting = isTestingConnection,
                    testResult = connectionTestResult,
                    onTestConnection = { url, key ->
                        viewModel.testSupabaseConnection(url, key)
                    },
                    onEditActive = {
                        activePreset?.let { active ->
                            coroutineScope.launch {
                                editingPreset = active
                                editingSecrets = viewModel.getSecretsForPreset(active.id)
                                showEditPresetSheet = true
                            }
                        }
                    }
                )
            }

            // 2. Section Header: Available Presets
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Available Environments (${presets.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                }
            }

            // 3. Preset Cards List
            items(presets, key = { it.id }) { preset ->
                PresetCard(
                    preset = preset,
                    isActive = preset.isActive,
                    onSelect = { viewModel.requestActivatePreset(preset) },
                    onEdit = {
                        coroutineScope.launch {
                            editingPreset = preset
                            editingSecrets = viewModel.getSecretsForPreset(preset.id)
                            showEditPresetSheet = true
                        }
                    },
                    onDuplicate = { viewModel.duplicatePreset(preset) },
                    onExport = {
                        viewModel.exportPreset(preset.id, includeSecrets = false)
                    },
                    onDelete = {
                        showDeleteConfirmDialog = preset
                    }
                )
            }
        }
    }

    // ==========================================
    // DIALOGS & BOTTOM SHEETS
    // ==========================================

    // Production Warning Confirmation Dialog
    showProdConfirmDialog?.let { targetPreset ->
        AlertDialog(
            onDismissRequest = { showProdConfirmDialog = null },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Switch to Production?",
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "You are about to connect Callog to the LIVE Production backend (${targetPreset.name}).",
                        color = Slate300
                    )
                    Text(
                        text = "⚠️ All uploaded calls, contacts, and recordings will directly alter live production tables.",
                        color = Amber500,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.activatePreset(targetPreset.id)
                        showProdConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber500, contentColor = Slate950)
                ) {
                    Text("Switch to Production", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProdConfirmDialog = null }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { presetToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Red500)
            },
            title = { Text("Delete Preset?", color = Slate50, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete '${presetToDelete.name}'? This cannot be undone.",
                    color = Slate300
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePreset(presetToDelete.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red500, contentColor = Slate50)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel", color = Slate400)
                }
            },
            containerColor = Slate900
        )
    }

    // Template Selector Dialog
    if (showTemplateDialog) {
        TemplateSelectionDialog(
            onSelectEnvironment = { env ->
                showTemplateDialog = false
                val template = viewModel.createPresetTemplate(env)
                editingPreset = template
                editingSecrets = PresetSecrets()
                showEditPresetSheet = true
            },
            onDismiss = { showTemplateDialog = false }
        )
    }

    // Edit/Create Preset Sheet
    if (showEditPresetSheet && editingPreset != null) {
        EditPresetBottomSheet(
            preset = editingPreset!!,
            initialSecrets = editingSecrets ?: PresetSecrets(),
            onDismiss = {
                showEditPresetSheet = false
                editingPreset = null
                editingSecrets = null
            },
            onSave = { updatedPreset, updatedSecrets ->
                viewModel.savePreset(updatedPreset, updatedSecrets)
                showEditPresetSheet = false
                editingPreset = null
                editingSecrets = null
            }
        )
    }

    // Export Preset Dialog
    showExportDialog?.let { (jsonString, name) ->
        ExportPresetDialog(
            presetName = name,
            jsonString = jsonString,
            onDismiss = { showExportDialog = null },
            onCopy = {
                clipboardManager.setText(AnnotatedString(jsonString))
                Toast.makeText(context, "Preset JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                showExportDialog = null
            }
        )
    }

    // Import Preset Dialog
    if (showImportDialog) {
        ImportPresetDialog(
            onDismiss = { showImportDialog = false },
            onImport = { jsonInput ->
                viewModel.importPreset(jsonInput)
                showImportDialog = false
            }
        )
    }
}

// ==========================================
// ACTIVE ENVIRONMENT SHOWCASE CARD
// ==========================================

@Composable
private fun ActiveEnvironmentShowcaseCard(
    preset: AppPreset?,
    secrets: PresetSecrets?,
    isTesting: Boolean,
    testResult: Pair<Boolean, String>?,
    onTestConnection: (String, String) -> Unit,
    onEditActive: () -> Unit
) {
    val env = preset?.environment ?: PresetEnvironment.DEVELOPMENT
    val accentColor = when (env) {
        PresetEnvironment.DEVELOPMENT -> Teal400
        PresetEnvironment.STAGING -> Teal300
        PresetEnvironment.PRODUCTION -> Amber500
        PresetEnvironment.CUSTOM -> AllSetLavender
    }

    GlassyCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Active Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (env) {
                                PresetEnvironment.DEVELOPMENT -> Icons.Default.Code
                                PresetEnvironment.STAGING -> Icons.Default.Science
                                PresetEnvironment.PRODUCTION -> Icons.Default.RocketLaunch
                                PresetEnvironment.CUSTOM -> Icons.Default.Settings
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = preset?.name ?: "Loading...",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Slate50
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = env.label.uppercase(),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            text = preset?.description ?: "Active Environment Profile",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onEditActive,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = Slate800,
                        contentColor = Slate50
                    )
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Active")
                }
            }

            HorizontalDivider(color = Slate800)

            // Key Endpoint Summary
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EndpointRow(
                    label = "Supabase URL",
                    value = preset?.configuration?.supabaseUrl?.takeIf { it.isNotBlank() } ?: "Not configured"
                )
                EndpointRow(
                    label = "Firebase Project",
                    value = preset?.configuration?.firebaseProjectId?.takeIf { it.isNotBlank() } ?: "Not configured"
                )
                EndpointRow(
                    label = "Firebase App ID",
                    value = preset?.configuration?.firebaseAppId?.takeIf { it.isNotBlank() } ?: "Not configured"
                )
                EndpointRow(
                    label = "Storage Bucket",
                    value = preset?.configuration?.storageBucket?.takeIf { it.isNotBlank() } ?: "Not configured"
                )
            }

            // Connection Test Feedback
            testResult?.let { (success, msg) ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (success) Green500.copy(alpha = 0.12f) else Red500.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (success) Green500.copy(alpha = 0.3f) else Red500.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (success) Green500 else Red500,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) Green500 else Red500
                        )
                    }
                }
            }

            // Test Connection Button
            Button(
                onClick = {
                    val url = preset?.configuration?.supabaseUrl ?: ""
                    val key = secrets?.supabaseAnonKey ?: ""
                    onTestConnection(url, key)
                },
                enabled = !isTesting && preset?.configuration?.supabaseUrl?.isNotBlank() == true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate800,
                    contentColor = accentColor
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing Connection...", color = accentColor)
                } else {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Backend Connection", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EndpointRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Slate400
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = Slate50,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ==========================================
// PRESET CARD COMPONENT
// ==========================================

@Composable
private fun PresetCard(
    preset: AppPreset,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val env = preset.environment
    val (accentColor, icon) = when (env) {
        PresetEnvironment.DEVELOPMENT -> Pair(Teal400, Icons.Default.Code)
        PresetEnvironment.STAGING -> Pair(Teal300, Icons.Default.Science)
        PresetEnvironment.PRODUCTION -> Pair(Amber500, Icons.Default.RocketLaunch)
        PresetEnvironment.CUSTOM -> Pair(AllSetLavender, Icons.Default.Settings)
    }

    var showMenu by remember { mutableStateOf(false) }

    GlassyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Environment Icon Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) accentColor.copy(alpha = 0.2f) else Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isActive) accentColor else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = Slate50
                        )

                        if (isActive) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accentColor.copy(alpha = 0.2f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = preset.configuration.supabaseUrl?.ifBlank { "No Supabase URL" } ?: "No Supabase URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Action Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = Slate400
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Slate900)
                ) {
                    if (!isActive) {
                        DropdownMenuItem(
                            text = { Text("Activate", color = accentColor, fontWeight = FontWeight.Bold) },
                            onClick = {
                                showMenu = false
                                onSelect()
                            },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Edit Preset", color = Slate50) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Slate300) }
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate", color = Slate50) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Slate300) }
                    )
                    DropdownMenuItem(
                        text = { Text("Export JSON", color = Slate50) },
                        onClick = {
                            showMenu = false
                            onExport()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = Slate300) }
                    )
                    HorizontalDivider(color = Slate800)
                    DropdownMenuItem(
                        text = { Text("Delete", color = Red500) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Red500) }
                    )
                }
            }
        }
    }
}

// ==========================================
// TEMPLATE SELECTION DIALOG
// ==========================================

@Composable
private fun TemplateSelectionDialog(
    onSelectEnvironment: (PresetEnvironment) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Environment Preset", color = Slate50, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose a template profile to get started:",
                    color = Slate400,
                    style = MaterialTheme.typography.bodySmall
                )

                PresetEnvironment.entries.forEach { env ->
                    TemplateOptionCard(
                        env = env,
                        onClick = { onSelectEnvironment(env) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        },
        containerColor = Slate900
    )
}

@Composable
private fun TemplateOptionCard(
    env: PresetEnvironment,
    onClick: () -> Unit
) {
    val (accentColor, icon) = when (env) {
        PresetEnvironment.DEVELOPMENT -> Pair(Teal400, Icons.Default.Code)
        PresetEnvironment.STAGING -> Pair(Teal300, Icons.Default.Science)
        PresetEnvironment.PRODUCTION -> Pair(Amber500, Icons.Default.RocketLaunch)
        PresetEnvironment.CUSTOM -> Pair(AllSetLavender, Icons.Default.Settings)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = Slate800.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, Slate700)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = env.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = env.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }
    }
}

// ==========================================
// EDIT / CREATE PRESET BOTTOM SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPresetBottomSheet(
    preset: AppPreset,
    initialSecrets: PresetSecrets,
    onDismiss: () -> Unit,
    onSave: (AppPreset, PresetSecrets) -> Unit
) {
    var name by remember { mutableStateOf(preset.name) }
    var selectedEnv by remember { mutableStateOf(preset.environment) }
    var description by remember { mutableStateOf(preset.description ?: "") }

    var supabaseUrl by remember { mutableStateOf(preset.configuration.supabaseUrl ?: "") }
    var supabaseAnonKey by remember { mutableStateOf(initialSecrets.supabaseAnonKey ?: "") }
    var isKeyVisible by remember { mutableStateOf(false) }

    var firebaseProjectId by remember { mutableStateOf(preset.configuration.firebaseProjectId ?: "") }
    var firebaseAppId by remember { mutableStateOf(preset.configuration.firebaseAppId ?: "") }
    var firebaseApiKey by remember { mutableStateOf(initialSecrets.firebaseApiKey ?: "") }
    var isFirebaseApiKeyVisible by remember { mutableStateOf(false) }
    var storageBucket by remember { mutableStateOf(preset.configuration.storageBucket ?: "") }
    var gcmSenderId by remember { mutableStateOf(preset.configuration.gcmSenderId ?: "") }
    var databaseUrl by remember { mutableStateOf(preset.configuration.databaseUrl ?: "") }
    var apiBaseUrl by remember { mutableStateOf(preset.configuration.apiBaseUrl ?: "") }

    val featuresState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(preset.configuration.features)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate600) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Edit Environment Preset",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            // Preset Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Preset Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            // Environment Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Environment Target",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate300
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetEnvironment.entries.forEach { env ->
                        val isSelected = selectedEnv == env
                        val envColor = when (env) {
                            PresetEnvironment.DEVELOPMENT -> Teal400
                            PresetEnvironment.STAGING -> Teal300
                            PresetEnvironment.PRODUCTION -> Amber500
                            PresetEnvironment.CUSTOM -> AllSetLavender
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedEnv = env },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) envColor.copy(alpha = 0.2f) else Slate800,
                            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) envColor else Slate700)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = env.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) envColor else Slate300
                                )
                            }
                        }
                    }
                }
            }

            // Description
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            // Section: Supabase Credentials
            Text(
                text = "Supabase Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Teal300
            )

            OutlinedTextField(
                value = supabaseUrl,
                onValueChange = { supabaseUrl = it },
                label = { Text("Supabase Project URL") },
                placeholder = { Text("https://xxx.supabase.co", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = supabaseAnonKey,
                onValueChange = { supabaseAnonKey = it },
                label = { Text("Supabase Anonymous Key (Encrypted in Keystore)") },
                singleLine = true,
                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Key Visibility",
                            tint = Slate400
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            // Section: Firebase Configuration
            Text(
                text = "Firebase & Storage Services",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Teal300
            )

            OutlinedTextField(
                value = firebaseProjectId,
                onValueChange = { firebaseProjectId = it },
                label = { Text("Firebase Project ID") },
                placeholder = { Text("e.g., callog-vault-prod", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = firebaseAppId,
                onValueChange = { firebaseAppId = it },
                label = { Text("Firebase Application ID (Must-Have)") },
                placeholder = { Text("e.g., 1:123456789012:android:abcdef123456", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = firebaseApiKey,
                onValueChange = { firebaseApiKey = it },
                label = { Text("Firebase API Key (Encrypted in Keystore)") },
                placeholder = { Text("e.g., AIzaSyB1234567890abcdef...", color = Slate500) },
                singleLine = true,
                visualTransformation = if (isFirebaseApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isFirebaseApiKeyVisible = !isFirebaseApiKeyVisible }) {
                        Icon(
                            imageVector = if (isFirebaseApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle API Key Visibility",
                            tint = Slate400
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = storageBucket,
                onValueChange = { storageBucket = it },
                label = { Text("Cloud Storage Bucket Name") },
                placeholder = { Text("e.g., callog-vault.firebasestorage.app", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = gcmSenderId,
                onValueChange = { gcmSenderId = it },
                label = { Text("Messaging Sender ID / Project Number (Optional)") },
                placeholder = { Text("e.g., 123456789012", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            OutlinedTextField(
                value = databaseUrl,
                onValueChange = { databaseUrl = it },
                label = { Text("Realtime Database URL (Optional)") },
                placeholder = { Text("e.g., https://xxx-default-rtdb.firebaseio.com", color = Slate500) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = Teal400,
                    unfocusedBorderColor = Slate700
                )
            )

            // Section: Feature Flags
            Text(
                text = "Environment Feature Flags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Teal300
            )

            FeatureFlagToggle(
                title = "Developer Diagnostics",
                description = "Enable telemetry & background sync inspector",
                enabled = featuresState[PresetConfiguration.FEATURE_DEV_DIAGNOSTICS] ?: true,
                onToggle = { featuresState[PresetConfiguration.FEATURE_DEV_DIAGNOSTICS] = it }
            )

            FeatureFlagToggle(
                title = "Call Simulator",
                description = "Allow simulation of incoming/outgoing calls",
                enabled = featuresState[PresetConfiguration.FEATURE_CALL_SIMULATOR] ?: true,
                onToggle = { featuresState[PresetConfiguration.FEATURE_CALL_SIMULATOR] = it }
            )

            FeatureFlagToggle(
                title = "Debug Logging",
                description = "Log detailed SQL and network payloads",
                enabled = featuresState[PresetConfiguration.FEATURE_DEBUG_LOGGING] ?: true,
                onToggle = { featuresState[PresetConfiguration.FEATURE_DEBUG_LOGGING] = it }
            )

            FeatureFlagToggle(
                title = "Experimental Call UI",
                description = "Test cutting-edge Jetpack Compose dialer layouts",
                enabled = featuresState[PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI] ?: false,
                onToggle = { featuresState[PresetConfiguration.FEATURE_EXPERIMENTAL_CALL_UI] = it }
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val updatedConfig = preset.configuration.copy(
                            supabaseUrl = supabaseUrl.trim(),
                            firebaseProjectId = firebaseProjectId.trim(),
                            firebaseAppId = firebaseAppId.trim(),
                            storageBucket = storageBucket.trim(),
                            gcmSenderId = gcmSenderId.trim(),
                            databaseUrl = databaseUrl.trim(),
                            apiBaseUrl = apiBaseUrl.trim(),
                            features = featuresState.toMap()
                        )
                        val updatedPreset = preset.copy(
                            name = name.trim().ifBlank { "Untitled Preset" },
                            environment = selectedEnv,
                            description = description.trim().ifBlank { null },
                            configuration = updatedConfig,
                            updatedAt = System.currentTimeMillis()
                        )
                        val updatedSecrets = initialSecrets.copy(
                            supabaseAnonKey = supabaseAnonKey.trim(),
                            firebaseApiKey = firebaseApiKey.trim()
                        )
                        onSave(updatedPreset, updatedSecrets)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal500, contentColor = Slate50)
                ) {
                    Text("Save Preset", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FeatureFlagToggle(
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Slate50, fontWeight = FontWeight.Medium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Slate400)
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Teal300,
                checkedTrackColor = Teal500,
                uncheckedThumbColor = Slate400,
                uncheckedTrackColor = Slate800
            )
        )
    }
}

// ==========================================
// EXPORT & IMPORT DIALOGS
// ==========================================

@Composable
private fun ExportPresetDialog(
    presetName: String,
    jsonString: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Export Preset: $presetName", color = Slate50, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Copy the JSON below to transfer or backup this environment profile:",
                    color = Slate400,
                    style = MaterialTheme.typography.bodySmall
                )
                SelectionContainer {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate950,
                        border = BorderStroke(1.dp, Slate800),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Text(
                            text = jsonString,
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Teal300,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(containerColor = Teal500, contentColor = Slate50)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy JSON", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Slate400)
            }
        },
        containerColor = Slate900
    )
}

@Composable
private fun ImportPresetDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var jsonInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Import Environment Preset", color = Slate50, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste the exported JSON string below to import the configuration profile:",
                    color = Slate400,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    placeholder = { Text("{\n  \"version\": 1,\n  \"preset\": ...\n}", color = Slate600) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Slate50,
                        unfocusedTextColor = Slate50,
                        focusedBorderColor = Teal400,
                        unfocusedBorderColor = Slate700
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(jsonInput.trim()) },
                enabled = jsonInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal500, contentColor = Slate50)
            ) {
                Text("Import Preset", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        },
        containerColor = Slate900
    )
}
