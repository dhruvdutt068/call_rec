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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var duplicateGroupForSelection by remember { mutableStateOf<CloudContactGroup?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onEvent(ContactsEvent.PermissionChanged(granted))
        if (granted) {
            onEvent(ContactsEvent.Refresh)
        }
    }

    val searchPlaceholder = if (state.source == ContactSource.CLOUD) {
        "Search cloud CRM, aliases..."
    } else {
        "Search phone contacts & numbers..."
    }

    val cloudGroups = remember(state.contacts, state.source) {
        if (state.source == ContactSource.CLOUD) {
            groupCloudContacts(state.contacts.filterIsInstance<ContactDirectoryItem.Cloud>())
        } else {
            emptyList()
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CallogAppBarWithSearch(
                query = state.query,
                onQueryChange = { onEvent(ContactsEvent.SearchChanged(it)) },
                placeholder = searchPlaceholder,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (onMenuClick != null) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu"
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
                }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Material 3 Primary Tab Row: Cloud vs Device
            ContactSourceTabs(
                selected = state.source,
                onSelected = { onEvent(ContactsEvent.SelectSource(it)) }
            )

            // Sync status banner for Cloud Tab (only occupies space when visible)
            AnimatedVisibility(
                visible = state.source == ContactSource.CLOUD && (state.syncStatus == "SYNCING" || (state.syncStatus?.startsWith("ERROR") == true))
            ) {
                Surface(
                    shape = CallogShapes.subtle,
                    color = if (state.syncStatus == "SYNCING") AllSetBlue.copy(alpha = 0.12f) else Red500.copy(alpha = 0.12f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp)
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
                        .weight(1f)
                        .padding(horizontal = 16.dp),
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
                        .weight(1f)
                        .padding(horizontal = 16.dp),
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
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 80.dp)
                ) {
                    if (state.source == ContactSource.CLOUD) {
                        items(
                            items = cloudGroups,
                            key = { "cloud_group_${it.key}" }
                        ) { group ->
                            CloudContactCard(
                                item = group.primaryItem,
                                duplicateCount = group.totalCount,
                                onClick = {
                                    if (group.isDuplicateGroup) {
                                        duplicateGroupForSelection = group
                                    } else {
                                        onContactClick(group.primaryItem)
                                    }
                                },
                                onCallClick = onCallClick
                            )
                        }
                    } else {
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

    duplicateGroupForSelection?.let { group ->
        DuplicateContactSelectionDialog(
            group = group,
            onDismiss = { duplicateGroupForSelection = null },
            onSelectContact = { selectedItem ->
                duplicateGroupForSelection = null
                onContactClick(selectedItem)
            }
        )
    }
}

/**
 * Data holder representing a consolidated group of duplicate Cloud CRM contacts.
 */
data class CloudContactGroup(
    val key: String,
    val primaryItem: ContactDirectoryItem.Cloud,
    val allProfiles: List<ContactDirectoryItem.Cloud>
) {
    val totalCount: Int get() = allProfiles.size
    val isDuplicateGroup: Boolean get() = totalCount > 1
    val displayName: String get() = primaryItem.displayName
    val primaryPhone: String? get() = primaryItem.primaryPhone
    val companyName: String? get() = primaryItem.companyName
}

/**
 * Clusters Cloud contacts into groups based on matching normalized names or overlapping phone numbers.
 */
fun groupCloudContacts(items: List<ContactDirectoryItem.Cloud>): List<CloudContactGroup> {
    if (items.isEmpty()) return emptyList()

    val groups = mutableListOf<MutableList<ContactDirectoryItem.Cloud>>()

    fun getNormalizedPhones(item: ContactDirectoryItem.Cloud): Set<String> {
        val phones = mutableSetOf<String>()
        item.primaryPhone?.let { p ->
            val digits = p.filter { it.isDigit() }
            if (digits.length >= 7) phones.add(digits.takeLast(10))
            else if (digits.isNotEmpty()) phones.add(digits)
        }
        item.person.phoneNumbers.forEach { pn ->
            val digits = pn.normalizedNumber.ifBlank { pn.phoneNumber }.filter { it.isDigit() }
            if (digits.length >= 7) phones.add(digits.takeLast(10))
            else if (digits.isNotEmpty()) phones.add(digits)
        }
        return phones
    }

    fun areDuplicates(a: ContactDirectoryItem.Cloud, b: ContactDirectoryItem.Cloud): Boolean {
        if (a.person.id == b.person.id) return true

        val nameA = a.displayName.trim().lowercase()
        val nameB = b.displayName.trim().lowercase()
        val nameMatch = nameA.isNotBlank() && nameA == nameB

        val phonesA = getNormalizedPhones(a)
        val phonesB = getNormalizedPhones(b)
        val phoneMatch = phonesA.isNotEmpty() && phonesB.isNotEmpty() && phonesA.intersect(phonesB).isNotEmpty()

        return nameMatch || phoneMatch
    }

    for (item in items) {
        val matchingGroup = groups.firstOrNull { group ->
            group.any { existing -> areDuplicates(existing, item) }
        }
        if (matchingGroup != null) {
            matchingGroup.add(item)
        } else {
            groups.add(mutableListOf(item))
        }
    }

    return groups.map { groupList ->
        CloudContactGroup(
            key = groupList.first().person.id,
            primaryItem = groupList.first(),
            allProfiles = groupList
        )
    }
}

/**
 * Dialog popup prompting user to select the exact profile when multiple duplicate cloud contacts match.
 */
@Composable
private fun DuplicateContactSelectionDialog(
    group: CloudContactGroup,
    onDismiss: () -> Unit,
    onSelectContact: (ContactDirectoryItem.Cloud) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = AllSetBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Select Contact Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${group.totalCount} matching profiles found for '${group.displayName}'. Select which one to view:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = group.allProfiles,
                    key = { it.person.id }
                ) { profile ->
                    Surface(
                        shape = CallogShapes.interactive,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectContact(profile)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val initials = remember(profile.displayName) {
                                val parts = profile.displayName.trim().split("\\s+".toRegex())
                                if (parts.size >= 2) {
                                    "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
                                } else {
                                    profile.displayName.take(1).uppercase()
                                }
                            }
                            ContactAvatar(
                                name = profile.displayName,
                                initials = initials,
                                photoUri = null,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = CallogShapes.subtle
                                    ) {
                                        Text(
                                            text = "#${profile.person.id.take(6)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (!profile.companyName.isNullOrBlank()) {
                                    Text(
                                        text = profile.companyName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val phone = profile.primaryPhone ?: profile.person.phoneNumbers.firstOrNull()?.phoneNumber ?: "No phone"
                                Text(
                                    text = phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!profile.person.notes.isNullOrBlank()) {
                                    Text(
                                        text = "Note: ${profile.person.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Select profile",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = CallogShapes.dialog,
        containerColor = MaterialTheme.colorScheme.surface
    )
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
    duplicateCount: Int = 1,
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

                    if (duplicateCount > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CallogShapes.subtle
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "$duplicateCount profiles",
                                    style = CallogTypography.statusLabel,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else {
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
