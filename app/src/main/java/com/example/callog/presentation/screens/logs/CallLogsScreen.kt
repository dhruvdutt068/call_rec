package com.example.callog.presentation.screens.logs

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhoneMissed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.components.CallCard
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.ExpressiveSegmentedButtonGroup
import com.example.callog.presentation.components.ExpressiveSegmentedButtonItem
import com.example.callog.presentation.components.SearchBarField
import com.example.callog.presentation.components.pullrefresh.ElasticPullRefreshLayout
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@Composable
fun CallLogsScreen(
    viewModel: CallViewModel,
    onCallClick: (Long) -> Unit,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val callLogs by viewModel.callLogs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilter by viewModel.callTypeFilter.collectAsState()
    val activeSort by viewModel.sortBy.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    CallLogsContent(
        callLogs = callLogs,
        searchQuery = searchQuery,
        activeFilter = activeFilter,
        activeSort = activeSort,
        isSyncing = isSyncing,
        onSearchQueryChange = { viewModel.setSearchQuery(it) },
        onFilterChange = { viewModel.setCallTypeFilter(it) },
        onSortChange = { viewModel.setSortBy(it) },
        onSyncClick = { viewModel.syncLogs() },
        onFavoriteToggle = { viewModel.toggleFavorite(it) },
        onCallClick = onCallClick,
        onBackClick = onBackClick,
        onMenuClick = onMenuClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogsContent(
    callLogs: List<CallLogEntry>,
    searchQuery: String,
    activeFilter: String?,
    activeSort: String,
    isSyncing: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onSortChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onCallClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val filterOptions = listOf(
        "ALL" to "All",
        "INCOMING" to "Incoming",
        "OUTGOING" to "Outgoing",
        "MISSED" to "Missed",
        "REJECTED" to "Rejected",
        "RECORDED" to "Recorded"
    )

    val sortOptions = listOf(
        "NEWEST" to "Newest First",
        "OLDEST" to "Oldest First",
        "LONGEST" to "Longest Duration",
        "SHORTEST" to "Shortest Duration"
    )

    ElasticPullRefreshLayout(
        isRefreshing = isSyncing,
        onRefresh = onSyncClick,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Call Logs",
                        style = CallogTypography.sectionTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else if (onMenuClick != null) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu"
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSyncClick,
                        enabled = !isSyncing,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Logs",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            SearchBarField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search name, number, notes, tags...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            // Filter chips and Sort button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Horizontal scrolling filters
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(filterOptions) { (key, label) ->
                        val isSelected = (activeFilter ?: "ALL") == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFilterChange(key) },
                            label = { Text(label, style = CallogTypography.statusLabel) },
                            shape = CallogShapes.pill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                // Sort Selector Trigger
                Box {
                    Surface(
                        shape = CallogShapes.interactive,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .clip(CallogShapes.interactive)
                            .clickable { showSortMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Sort order",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOptions.firstOrNull { it.first == activeSort }?.second?.split(" ")?.first() ?: "Sort",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        shape = CallogShapes.dialog,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (activeSort == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (activeSort == key) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSortChange(key)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Logs list
            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveEmptyState(
                        title = "No call logs match",
                        description = "Try adjusting your search terms or filters, or tap the Sync button to refresh logs from your device.",
                        icon = Icons.Outlined.PhoneMissed,
                        actionLabel = if (!isSyncing) "Sync Logs" else null,
                        onActionClick = onSyncClick
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
                        items = callLogs,
                        key = { it.id }
                    ) { call ->
                        CallCard(
                            call = call,
                            onClick = { onCallClick(call.id) },
                            onFavoriteToggle = { onFavoriteToggle(call.id) }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Call Logs Screen - Light", showBackground = true)
@Preview(name = "Call Logs Screen - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CallLogsScreenPreview() {
    CallogTheme {
        CallLogsContent(
            callLogs = listOf(
                CallLogEntry(
                    id = 1L,
                    name = "Alice Smith",
                    number = "+1 (555) 234-5678",
                    duration = 145,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    callType = "INCOMING",
                    recordingPath = "/path/to/rec.m4a",
                    isFavorite = true,
                    notes = "Discussed Q3 sales contract",
                    tags = listOf("work"),
                    contactPhotoUri = null,
                    syncStatus = "SYNCED"
                ),
                CallLogEntry(
                    id = 2L,
                    name = "Unknown Caller",
                    number = "+1 (555) 999-8888",
                    duration = 0,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 120,
                    callType = "MISSED",
                    recordingPath = null,
                    isFavorite = false,
                    notes = null,
                    tags = listOf("spam"),
                    contactPhotoUri = null,
                    syncStatus = "PENDING"
                )
            ),
            searchQuery = "",
            activeFilter = "ALL",
            activeSort = "NEWEST",
            isSyncing = false,
            onSearchQueryChange = {},
            onFilterChange = {},
            onSortChange = {},
            onSyncClick = {},
            onFavoriteToggle = {},
            onCallClick = {}
        )
    }
}


