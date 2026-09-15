package com.example.callog.presentation.screens.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.callog.data.provider.ContactDto
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.components.SearchBarField
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onContactClick: ((contactId: String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val syncStatus by viewModel.contactsSyncStatus.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "SUPABASE", "DEVICE", "FAVORITES"
    var showAddContactDialog by remember { mutableStateOf(false) }

    val filterOptions = listOf(
        "ALL" to "All Contacts",
        "SUPABASE" to "Cloud (Supabase)",
        "DEVICE" to "Device Phonebook",
        "FAVORITES" to "Starred"
    )

    val filteredContacts = remember(contacts, searchQuery, selectedFilter) {
        val list = when (selectedFilter) {
            "SUPABASE" -> contacts.filter { it.contactId.startsWith("P") || it.contactId.startsWith("DEV_") }
            "DEVICE" -> contacts.filter { !it.contactId.startsWith("P") }
            "FAVORITES" -> contacts.filter { it.isFavorite }
            else -> contacts
        }

        if (searchQuery.isEmpty()) {
            list
        } else {
            val q = searchQuery.trim().lowercase()
            list.filter { contact ->
                contact.name.lowercase().contains(q) ||
                contact.phoneNumbers.any { it.contains(q) } ||
                contact.emails.any { it.lowercase().contains(q) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts", fontWeight = FontWeight.Bold) },
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
                        onClick = { viewModel.refreshGlobalContacts() },
                        enabled = syncStatus != "SYNCING"
                    ) {
                        if (syncStatus == "SYNCING") {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync Contacts",
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
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = AllSetBlue,
                contentColor = Slate50,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Global Contact"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Header
            SearchBarField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search contacts...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            // Sync Status Banner (when syncing or error)
            AnimatedVisibility(visible = syncStatus == "SYNCING" || (syncStatus?.startsWith("ERROR") == true)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (syncStatus == "SYNCING") AllSetBlue.copy(alpha = 0.15f) else Red500.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (syncStatus == "SYNCING") Icons.Default.Sync else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (syncStatus == "SYNCING") AllSetBlue else Red500,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (syncStatus == "SYNCING") "Syncing global contacts from Supabase..." else (syncStatus ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate50,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Filter Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { (key, label) ->
                    val isSelected = selectedFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AllSetBlue,
                            selectedLabelColor = Slate50,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) AllSetBlue else MaterialTheme.colorScheme.outline,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }

            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateView(
                        title = "No Contacts Found",
                        description = if (searchQuery.isNotEmpty()) "No contacts match '$searchQuery'" else "Click '+' to create a new global contact or click Sync to refresh from Supabase.",
                        icon = Icons.Default.People
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
                        items = filteredContacts,
                        key = { it.contactId }
                    ) { contact ->
                        GlobalContactCard(
                            contact = contact,
                            isSupabaseGlobal = contact.contactId.startsWith("P") || contact.contactId.startsWith("DEV_"),
                            onClick = { onContactClick?.invoke(contact.contactId) },
                            onCallClick = { phone ->
                                viewModel.initiateCall(context, phone)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddContactDialog) {
        AddGlobalContactDialog(
            onDismiss = { showAddContactDialog = false },
            onConfirm = { name, phone, company, notes ->
                viewModel.createGlobalContact(name, phone, company, notes) { success ->
                    if (success) {
                        showAddContactDialog = false
                    }
                }
            }
        )
    }
}

@Composable
private fun GlobalContactCard(
    contact: ContactDto,
    isSupabaseGlobal: Boolean,
    onClick: () -> Unit = {},
    onCallClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val initials = remember(contact.name) {
        val parts = contact.name.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else {
            contact.name.take(1).uppercase()
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
                name = contact.name,
                initials = initials,
                photoUri = contact.photoUri,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isSupabaseGlobal) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Green500.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Cloud CRM",
                                style = MaterialTheme.typography.labelSmall,
                                color = Green500,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    if (contact.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred Contact",
                            tint = Amber500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary phone number
                val primaryPhone = contact.phoneNumbers.firstOrNull() ?: "No phone"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = Teal300,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = primaryPhone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400
                    )
                }
            }

            // Quick Call Action
            if (contact.phoneNumbers.isNotEmpty()) {
                IconButton(
                    onClick = { onCallClick(contact.phoneNumbers.first()) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Green500.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Contact",
                        tint = Green500,
                        modifier = Modifier.size(18.dp)
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AllSetBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Global Contact", fontWeight = FontWeight.Bold)
            }
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, phone, company, notes) },
                enabled = name.isNotBlank() && phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AllSetBlue)
            ) {
                Text("Save to Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
