package com.example.callog.presentation.screens.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.domain.model.ContactDirectoryItem
import com.example.callog.domain.model.ContactSource
import com.example.callog.presentation.components.*
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import com.example.callog.presentation.viewmodel.ContactsEvent
import com.example.callog.presentation.viewmodel.ContactsUiState
import com.example.callog.presentation.viewmodel.ContactsViewModel

/**
 * Top-level Contacts Directory screen providing two strictly-separated tabs:
 * 1. Cloud: Supabase canonical people + phone numbers + aliases (CRM Directory).
 * 2. Device: Local Android ContactsContract address book.
 *
 * Architectural Invariant:
 * Cloud tab never falls back to device contacts, and Device tab never displays Supabase-only contacts.
 */
@Composable
fun ContactsScreen(
    contactsViewModel: ContactsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    onContactClick: ((personId: String) -> Unit)? = null,
    onDeviceContactClick: ((androidContactId: String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by contactsViewModel.uiState.collectAsState()

    // Monitor Contacts Permission for Device Tab
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        contactsViewModel.setContactsPermission(granted)
    }

    ContactsContent(
        state = uiState,
        onEvent = { contactsViewModel.onEvent(it) },
        onCallClick = { phone ->
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        },
        onContactClick = { contactItem ->
            when (contactItem) {
                is ContactDirectoryItem.Cloud -> {
                    onContactClick?.invoke(contactItem.person.id)
                }
                is ContactDirectoryItem.Device -> {
                    if (onDeviceContactClick != null) {
                        onDeviceContactClick(contactItem.contactId)
                    } else {
                        // Fallback to canonical detail if device click is not wired
                        onContactClick?.invoke(contactItem.linkedPersonId ?: contactItem.contactId)
                    }
                }
            }
        },
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}

/**
 * Compatibility overload for existing Navigation / Activity callers passing CallViewModel.
 */
@Composable
fun ContactsScreen(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onContactClick: ((contactId: String) -> Unit)? = null,
    onDeviceContactClick: ((androidContactId: String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    contactsViewModel: ContactsViewModel = hiltViewModel()
) {
    ContactsScreen(
        contactsViewModel = contactsViewModel,
        modifier = modifier,
        onContactClick = onContactClick,
        onDeviceContactClick = onDeviceContactClick,
        onMenuClick = onMenuClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsContent(
    state: ContactsUiState,
    onEvent: (ContactsEvent) -> Unit,
    onCallClick: (String) -> Unit,
    onContactClick: (ContactDirectoryItem) -> Unit,
    modifier: Modifier = Modifier,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showAddContactDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onEvent(ContactsEvent.PermissionChanged(granted))
        if (granted) {
            onEvent(ContactsEvent.Refresh)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts Directory", style = CallogTypography.sectionTitle) },
                navigationIcon = {
                    if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(ContactsEvent.Refresh) },
                        enabled = !state.isRefreshing && state.syncStatus != "SYNCING"
                    ) {
                        if (state.isRefreshing || state.syncStatus == "SYNCING") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = if (state.source == ContactSource.CLOUD) Icons.Default.CloudSync else Icons.Default.Refresh,
                                contentDescription = "Refresh Contacts",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        floatingActionButton = {
            if (state.source == ContactSource.CLOUD) {
                FloatingActionButton(
                    onClick = { showAddContactDialog = true },
                    containerColor = AllSetBlue,
                    contentColor = Color.White,
                    shape = CallogShapes.avatar
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "Add Global CRM Contact"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Material 3 Primary Tab Row: Cloud vs Device
            ContactSourceTabs(
                selected = state.source,
                onSelected = { onEvent(ContactsEvent.SelectSource(it)) }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search Bar
                val searchPlaceholder = if (state.source == ContactSource.CLOUD) {
                    "Search cloud CRM, company, aliases..."
                } else {
                    "Search phone contacts & numbers..."
                }

                SearchBarField(
                    query = state.query,
                    onQueryChange = { onEvent(ContactsEvent.SearchChanged(it)) },
                    placeholder = searchPlaceholder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )

                // Sync status banner for Cloud Tab
                AnimatedVisibility(
                    visible = state.source == ContactSource.CLOUD && (state.syncStatus == "SYNCING" || (state.syncStatus?.startsWith("ERROR") == true))
                ) {
                    Surface(
                        shape = CallogShapes.subtle,
                        color = if (state.syncStatus == "SYNCING") AllSetBlue.copy(alpha = 0.12f) else Red500.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.syncStatus == "SYNCING") Icons.Default.Sync else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (state.syncStatus == "SYNCING") AllSetBlue else Red500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.syncStatus == "SYNCING") "Syncing global contacts from Supabase..." else (state.syncStatus ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Check Device tab permissions state
                if (state.source == ContactSource.DEVICE && !state.hasContactsPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PermContactCalendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Phone Contacts Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Contacts on your phone aren't available yet. Allow contacts permission to browse your phone address book.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                shape = CallogShapes.interactive,
                                colors = ButtonDefaults.buttonColors(containerColor = AllSetBlue)
                            ) {
                                Text("Allow Contacts Access")
                            }
                        }
                    }
                } else if (state.contacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyTitle = when {
                            state.query.isNotEmpty() -> "No Matching Contacts"
                            state.source == ContactSource.CLOUD -> "No Cloud Contacts"
                            else -> "No Phone Contacts"
                        }
                        val emptyDesc = when {
                            state.query.isNotEmpty() -> "No contacts match '${state.query}'"
                            state.source == ContactSource.CLOUD -> "No cloud contacts cached yet. Connect to Supabase and sync to load your CRM contacts."
                            else -> "No phone contacts found on this device."
                        }

                        ExpressiveEmptyState(
                            icon = if (state.source == ContactSource.CLOUD) Icons.Outlined.CloudOff else Icons.Outlined.PersonSearch,
                            title = emptyTitle,
                            description = emptyDesc,
                            actionLabel = if (state.query.isNotEmpty()) "Clear Search" else if (state.source == ContactSource.CLOUD) "Add Contact" else null,
                            actionIcon = if (state.query.isNotEmpty()) Icons.Default.Clear else if (state.source == ContactSource.CLOUD) Icons.Default.PersonAdd else null,
                            onAction = {
                                if (state.query.isNotEmpty()) {
                                    onEvent(ContactsEvent.SearchChanged(""))
                                } else if (state.source == ContactSource.CLOUD) {
                                    showAddContactDialog = true
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = state.contacts,
                            key = { item ->
                                when (item) {
                                    is ContactDirectoryItem.Cloud -> "cloud_${item.person.id}"
                                    is ContactDirectoryItem.Device -> "device_${item.contactId}"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ContactDirectoryItem.Cloud -> {
                                    CloudContactCard(
                                        item = item,
                                        onClick = { onContactClick(item) },
                                        onCallClick = onCallClick
                                    )
                                }
                                is ContactDirectoryItem.Device -> {
                                    DeviceContactCard(
                                        item = item,
                                        onClick = { onContactClick(item) },
                                        onCallClick = onCallClick
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        AddGlobalContactDialog(
            onDismiss = { showAddContactDialog = false },
            onConfirm = { name, phone, company, notes ->
                onEvent(
                    ContactsEvent.CreateGlobalContact(name, phone, company, notes) { success ->
                        if (success) {
                            showAddContactDialog = false
                        }
                    }
                )
            }
        )
    }
}

/**
 * Material 3 Primary Tab Row cleanly switching between Cloud CRM and Device Contacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSourceTabs(
    selected: ContactSource,
    onSelected: (ContactSource) -> Unit
) {
    PrimaryTabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Tab(
            selected = selected == ContactSource.CLOUD,
            onClick = { onSelected(ContactSource.CLOUD) },
            text = {
                Text("Cloud", fontWeight = if (selected == ContactSource.CLOUD) FontWeight.Bold else FontWeight.Normal)
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Cloud,
                    contentDescription = "Cloud Contacts"
                )
            }
        )

        Tab(
            selected = selected == ContactSource.DEVICE,
            onClick = { onSelected(ContactSource.DEVICE) },
            text = {
                Text("Device", fontWeight = if (selected == ContactSource.DEVICE) FontWeight.Bold else FontWeight.Normal)
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = "Device Contacts"
                )
            }
        )
    }
}

/**
 * Card for Cloud CRM Person.
 */
@Composable
private fun CloudContactCard(
    item: ContactDirectoryItem.Cloud,
    onClick: () -> Unit,
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = remember(item.displayName) {
        val parts = item.displayName.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else {
            item.displayName.take(1).uppercase()
        }
    }

    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                name = item.displayName,
                initials = initials,
                photoUri = null,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.displayName,
                        style = CallogTypography.entityName,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = AllSetTeal.copy(alpha = 0.15f),
                        shape = CallogShapes.subtle
                    ) {
                        Text(
                            text = "Cloud CRM",
                            style = CallogTypography.statusLabel,
                            color = AllSetTeal,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (item.aliasCount > 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = AllSetBlue.copy(alpha = 0.12f),
                            shape = CallogShapes.subtle
                        ) {
                            Text(
                                text = "${item.aliasCount} aliases",
                                style = CallogTypography.statusLabel,
                                color = AllSetBlue,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!item.companyName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.companyName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                val displayPhone = item.primaryPhone ?: "No phone"
                Text(
                    text = displayPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.primaryPhone.isNullOrBlank()) {
                IconButton(
                    onClick = { onCallClick(item.primaryPhone) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call ${item.displayName}",
                        tint = AllSetTeal
                    )
                }
            }
        }
    }
}

/**
 * Card for Device Contact.
 */
@Composable
private fun DeviceContactCard(
    item: ContactDirectoryItem.Device,
    onClick: () -> Unit,
    onCallClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val initials = remember(item.displayName) {
        val parts = item.displayName.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else {
            item.displayName.take(1).uppercase()
        }
    }

    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                name = item.displayName,
                initials = initials,
                photoUri = item.photoUri,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.displayName,
                        style = CallogTypography.entityName,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CallogShapes.subtle
                    ) {
                        Text(
                            text = "Device",
                            style = CallogTypography.statusLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    if (item.linkedPersonId != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = AllSetTeal.copy(alpha = 0.15f),
                            shape = CallogShapes.subtle
                        ) {
                            Text(
                                text = "✓ Linked",
                                style = CallogTypography.statusLabel,
                                color = AllSetTeal,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                val displayPhone = item.primaryPhone ?: "No phone"
                Text(
                    text = displayPhone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!item.primaryPhone.isNullOrBlank()) {
                IconButton(
                    onClick = { onCallClick(item.primaryPhone) },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "Call ${item.displayName}",
                        tint = AllSetTeal
                    )
                }
            }
        }
    }
}

@Composable
private fun AddGlobalContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, company: String?, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Global CRM Contact", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    shape = CallogShapes.interactive,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    shape = CallogShapes.interactive,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company / Account") },
                    singleLine = true,
                    shape = CallogShapes.interactive,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Initial Notes") },
                    maxLines = 3,
                    shape = CallogShapes.interactive,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && phone.isNotBlank()) {
                        isSubmitting = true
                        onConfirm(name, phone, company.ifBlank { null }, notes.ifBlank { null })
                    }
                },
                enabled = name.isNotBlank() && phone.isNotBlank() && !isSubmitting,
                shape = CallogShapes.interactive,
                colors = ButtonDefaults.buttonColors(containerColor = AllSetBlue)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text("Save Contact")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Cancel")
            }
        },
        shape = CallogShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface
    )
}
