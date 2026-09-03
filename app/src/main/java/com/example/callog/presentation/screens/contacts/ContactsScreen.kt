package com.example.callog.presentation.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun ContactsScreen(
    viewModel: CallViewModel,
    modifier: Modifier = Modifier,
    onContactClick: ((contactId: String) -> Unit)? = null
) {
    val contacts by viewModel.contacts.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isEmpty()) {
            contacts
        } else {
            val q = searchQuery.trim().lowercase()
            contacts.filter { contact ->
                contact.name.lowercase().contains(q) ||
                contact.phoneNumbers.any { it.contains(q) } ||
                contact.emails.any { it.lowercase().contains(q) }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search Header
        SearchBarField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search contacts by name, number...",
            modifier = Modifier.padding(top = 12.dp)
        )

        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "No Contacts Found",
                    description = "Check permissions or adjust your search queries.",
                    icon = Icons.Default.People
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = filteredContacts,
                    key = { it.contactId }
                ) { contact ->
                    ContactCard(
                        contact = contact,
                        onClick = { onContactClick?.invoke(contact.contactId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: ContactDto,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Generate initials for avatar fallback
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
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                name = contact.name,
                initials = initials,
                photoUri = contact.photoUri,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

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
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (contact.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred Contact",
                            tint = Amber500,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Numbers list
                contact.phoneNumbers.forEach { number ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Teal300,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400
                        )
                    }
                }

                // Emails list
                contact.emails.forEach { email ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Amber500,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
