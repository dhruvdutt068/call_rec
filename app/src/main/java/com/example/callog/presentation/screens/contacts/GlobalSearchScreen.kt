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
import com.example.callog.presentation.components.ExpressiveEmptyState
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
                title = {
                    Text(
                        text = "Global CRM Search",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Full Results for \"$query\"", style = CallogTypography.sectionTitle)
                }
            }

            Text(
                text = "Quick Suggestions",
                style = CallogTypography.sectionTitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (suggestions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) "Type a name or number to search CRM" else "No immediate suggestions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.name,
                                        style = CallogTypography.entityName,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = contact.phoneNumbers.firstOrNull() ?: "",
                                        style = CallogTypography.denseData,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
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
                title = {
                    Text(
                        text = "Results for \"$query\"",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (results.isEmpty()) {
            ExpressiveEmptyState(
                title = "No Matches Found",
                description = "No contacts matched your search query \"$query\". Try searching with a different keyword.",
                icon = Icons.Default.SearchOff,
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
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
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    style = CallogTypography.entityName,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "ID: ${contact.contactId}",
                                    style = CallogTypography.statusLabel,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = contact.phoneNumbers.joinToString(", "),
                                    style = CallogTypography.denseData,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

