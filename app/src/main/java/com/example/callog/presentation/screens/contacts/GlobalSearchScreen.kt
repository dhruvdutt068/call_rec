package com.example.callog.presentation.screens.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.components.SearchBarField
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    initialQuery: String,
    onBackClick: () -> Unit,
    onSearchSubmitted: (query: String) -> Unit,
    onContactSelected: (contactId: String) -> Unit,
    viewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf(initialQuery) }
    val contacts by viewModel.contacts.collectAsState()

    val suggestions = remember(query, contacts) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.trim().lowercase()
            contacts.filter { it.name.lowercase().contains(q) || it.phoneNumbers.any { num -> num.contains(q) } }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global CRM Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SearchBarField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search clients, numbers, companies...",
                modifier = Modifier.fillMaxWidth()
            )

            if (query.isNotBlank()) {
                Button(
                    onClick = { onSearchSubmitted(query.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Full Results for \"$query\"", fontWeight = FontWeight.Bold)
                }
            }

            Text("Quick Suggestions", style = MaterialTheme.typography.titleSmall, color = Slate300)

            if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (query.isBlank()) "Type a name or number to search CRM" else "No immediate suggestions",
                        color = Slate400
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions, key = { it.contactId }) { contact ->
                        GlassyCard(onClick = { onContactSelected(contact.contactId) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Teal300)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.name, fontWeight = FontWeight.SemiBold, color = Slate50)
                                    Text(
                                        contact.phoneNumbers.firstOrNull() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Slate400
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Slate400)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsScreen(
    query: String,
    onBackClick: () -> Unit,
    onContactSelected: (contactId: String) -> Unit,
    viewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.contacts.collectAsState()
    val results = remember(query, contacts) {
        val q = query.trim().lowercase()
        contacts.filter {
            it.name.lowercase().contains(q) ||
            it.phoneNumbers.any { num -> num.contains(q) } ||
            it.emails.any { email -> email.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results for \"$query\"", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (results.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateView(
                    title = "No Matches Found",
                    description = "No contacts matched your search query \"$query\".",
                    icon = Icons.Default.SearchOff
                )
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(results, key = { it.contactId }) { contact ->
                    GlassyCard(onClick = { onContactSelected(contact.contactId) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate50)
                                Text("ID: ${contact.contactId}", style = MaterialTheme.typography.labelSmall, color = Teal300)
                                Text(contact.phoneNumbers.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = Slate400)
                            }
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Teal300)
                        }
                    }
                }
            }
        }
    }
}
