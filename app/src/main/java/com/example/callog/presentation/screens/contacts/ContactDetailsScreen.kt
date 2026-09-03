package com.example.callog.presentation.screens.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

/**
 * Contact Details Screen utilizing canonical [contactId] (String) for CRM client management.
 * Business logic remains decoupled in [viewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    contactId: String,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onScheduleMeetingClick: (String) -> Unit = {},
    onCreateTaskClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val allCallLogs by viewModel.callLogs.collectAsState()

    val contact = remember(contacts, contactId) {
        contacts.find { it.contactId == contactId }
    }

    // Filter call logs for this contact's phone numbers
    val contactCallLogs = remember(allCallLogs, contact) {
        if (contact == null) emptyList()
        else {
            val contactNumbers = contact.phoneNumbers.map { it.replace("[^0-9+]".toRegex(), "") }
            allCallLogs.filter { log ->
                val logNum = log.number.replace("[^0-9+]".toRegex(), "")
                contactNumbers.any { it.isNotEmpty() && (logNum.endsWith(it) || it.endsWith(logNum)) }
            }
        }
    }

    val initials = remember(contact?.name) {
        val name = contact?.name ?: "Contact"
        val parts = name.trim().split("\\s+".toRegex())
        if (parts.size >= 2) {
            "${parts[0].take(1)}${parts[1].take(1)}".uppercase()
        } else {
            name.take(1).uppercase()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.name ?: "Contact Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onScheduleMeetingClick(contactId) }) {
                        Icon(Icons.Default.Event, contentDescription = "Schedule Meeting", tint = Teal300)
                    }
                    IconButton(onClick = { onCreateTaskClick(contactId) }) {
                        Icon(Icons.Default.AddTask, contentDescription = "Create Task", tint = Amber500)
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
        if (contact == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Contact not found (ID: $contactId)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate400
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                // Profile Header Card
                item {
                    GlassyCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ContactAvatar(
                                name = contact.name,
                                initials = initials,
                                photoUri = contact.photoUri,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Slate50
                            )
                            Text(
                                text = "Canonical ID: $contactId",
                                style = MaterialTheme.typography.labelMedium,
                                color = Teal300
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Quick Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionButton(
                                    icon = Icons.Default.Phone,
                                    label = "Call",
                                    color = Teal300,
                                    onClick = {
                                        contact.phoneNumbers.firstOrNull()?.let { phone ->
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Message,
                                    label = "Message",
                                    color = AllSetBlue,
                                    onClick = {
                                        contact.phoneNumbers.firstOrNull()?.let { phone ->
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                                QuickActionButton(
                                    icon = Icons.Default.Email,
                                    label = "Email",
                                    color = Amber500,
                                    onClick = {
                                        contact.emails.firstOrNull()?.let { email ->
                                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Contact Details List
                item {
                    Text(
                        text = "Contact Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate300
                    )
                }

                item {
                    GlassyCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            contact.phoneNumbers.forEach { phone ->
                                InfoRow(icon = Icons.Default.Phone, label = "Phone", value = phone, tint = Teal300)
                            }
                            contact.emails.forEach { email ->
                                InfoRow(icon = Icons.Default.Email, label = "Email", value = email, tint = Amber500)
                            }
                        }
                    }
                }

                // Recent Call History with this contact
                item {
                    Text(
                        text = "Interaction History (${contactCallLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate300
                    )
                }

                if (contactCallLogs.isEmpty()) {
                    item {
                        GlassyCard {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No recent calls recorded with this contact", color = Slate400)
                            }
                        }
                    }
                } else {
                    items(contactCallLogs, key = { it.id }) { callLog ->
                        GlassyCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = callLog.callType.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate50
                                    )
                                    Text(
                                        text = "${callLog.duration}s duration",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                                Text(
                                    text = callLog.timestamp.toString().take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate400
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f))
        ) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate300)
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = Slate50)
        }
    }
}
