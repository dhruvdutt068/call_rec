package com.example.callog.presentation.screens.dialer

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PhoneMissed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.core.telecom.T9MatchResult
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.CallCard
import com.example.callog.presentation.components.CallogAppBarWithSearch
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.ExpressiveEmptyState
import com.example.callog.presentation.components.pullrefresh.ElasticPullRefreshLayout
import com.example.callog.presentation.theme.*
import com.example.callog.sim.SimInfo

/**
 * Modern unified Dialer Screen.
 *
 * Recent call history serves as the primary workspace by default.
 * The dialpad is available on-demand via an animated Floating Action Button (FAB).
 */
@Composable
fun DialerScreen(
    onCallClick: ((Long) -> Unit)? = null,
    onNavigateToContact: ((String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    onNavigateToCallSimulator: (() -> Unit)? = null,
    viewModel: DialerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSimulatorSheet by remember { mutableStateOf(false) }
    val callLogsListState = rememberLazyListState()

    // Intercept Back button when dialpad is open
    BackHandler(enabled = uiState.mode == DialerMode.Dialpad) {
        viewModel.onAction(DialerUiAction.CloseDialpad)
    }

    LaunchedEffect(Unit) {
        viewModel.onAction(DialerUiAction.RefreshSims)
        viewModel.onAction(DialerUiAction.CheckClipboard)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate950,
        floatingActionButton = {
            DialerFab(
                isDialpadOpen = uiState.mode == DialerMode.Dialpad,
                onToggle = { viewModel.onAction(DialerUiAction.ToggleDialpad) }
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        AnimatedContent(
            targetState = uiState.mode,
            transitionSpec = {
                if (targetState == DialerMode.Dialpad) {
                    (slideInVertically(animationSpec = tween(280)) { it / 4 } + fadeIn(animationSpec = tween(250)))
                        .togetherWith(slideOutVertically(animationSpec = tween(200)) { -it / 6 } + fadeOut(animationSpec = tween(180)))
                } else {
                    (slideInVertically(animationSpec = tween(280)) { -it / 6 } + fadeIn(animationSpec = tween(250)))
                        .togetherWith(slideOutVertically(animationSpec = tween(220)) { it / 4 } + fadeOut(animationSpec = tween(180)))
                }
            },
            label = "DialerModeTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { mode ->
            when (mode) {
                DialerMode.CallLogs -> {
                    CallLogsModeView(
                        uiState = uiState,
                        listState = callLogsListState,
                        onAction = viewModel::onAction,
                        onCallClick = onCallClick,
                        onMenuClick = onMenuClick,
                        onOpenSimulator = { showSimulatorSheet = true }
                    )
                }
                DialerMode.Dialpad -> {
                    DialpadModeView(
                        uiState = uiState,
                        onAction = viewModel::onAction,
                        onMenuClick = onMenuClick,
                        onOpenSimulator = { showSimulatorSheet = true },
                        onNavigateToContact = onNavigateToContact
                    )
                }
            }
        }

        if (showSimulatorSheet) {
            SimulateCallBottomSheet(
                initialNumber = uiState.inputNumber,
                activeSims = uiState.activeSims,
                simulatorManager = viewModel.simulatorManager,
                onDismiss = { showSimulatorSheet = false },
                onNavigateToStudio = {
                    showSimulatorSheet = false
                    onNavigateToCallSimulator?.invoke()
                }
            )
        }
    }
}

// ==========================================
// 1. ANIMATED FAB
// ==========================================

@Composable
private fun DialerFab(
    isDialpadOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onToggle,
        containerColor = if (isDialpadOpen) Slate800 else Teal500,
        contentColor = if (isDialpadOpen) Slate300 else Color.White,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp, end = 4.dp)
            .size(58.dp)
    ) {
        AnimatedContent(
            targetState = isDialpadOpen,
            transitionSpec = {
                (fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.8f))
                    .togetherWith(fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f))
            },
            label = "FabIconMorph"
        ) { isOpen ->
            if (isOpen) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Dialpad",
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Open Dialpad",
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ==========================================
// 2. CALL LOGS MODE VIEW
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallLogsModeView(
    uiState: DialerUiState,
    listState: LazyListState,
    onAction: (DialerUiAction) -> Unit,
    onCallClick: ((Long) -> Unit)?,
    onMenuClick: (() -> Unit)?,
    onOpenSimulator: () -> Unit
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
        isRefreshing = uiState.isSyncing,
        onRefresh = { onAction(DialerUiAction.SyncLogs) },
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CallogAppBarWithSearch(
                query = uiState.searchQuery,
                onQueryChange = { onAction(DialerUiAction.SetSearchQuery(it)) },
                placeholder = "Search name, number, notes...",
                navigationIcon = {
                    if (onMenuClick != null) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Navigation Menu",
                                tint = Slate300
                            )
                        }
                    }
                },
                actions = {
                    // Call Simulator Quick Button
                    IconButton(
                        onClick = onOpenSimulator,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Teal500.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = "Simulate Call",
                            tint = Teal300,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Sync Button
                    IconButton(
                        onClick = { onAction(DialerUiAction.SyncLogs) },
                        enabled = !uiState.isSyncing,
                        modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp)
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Teal400
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Logs",
                                tint = Teal400
                            )
                        }
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Filter chips and Sort button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 8.dp)
                ) {
                    items(filterOptions) { (key, label) ->
                        val isSelected = (uiState.activeFilter ?: "ALL") == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { onAction(DialerUiAction.SetFilter(key)) },
                            label = { Text(label, style = CallogTypography.statusLabel) },
                            shape = CallogShapes.pill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Teal500.copy(alpha = 0.25f),
                                selectedLabelColor = Teal300,
                                containerColor = Slate900,
                                labelColor = Slate400
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (isSelected) Teal500 else Slate800,
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
                        color = Slate900,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                        modifier = Modifier
                            .clip(CallogShapes.interactive)
                            .clickable { showSortMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Sort order",
                                tint = Teal400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sortOptions.firstOrNull { it.first == uiState.activeSort }?.second?.split(" ")?.first() ?: "Sort",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Slate300
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        shape = CallogShapes.dialog,
                        modifier = Modifier.background(Slate900)
                    ) {
                        sortOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = label,
                                        color = if (uiState.activeSort == key) Teal300 else Slate300,
                                        fontWeight = if (uiState.activeSort == key) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onAction(DialerUiAction.SetSort(key))
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Logs list with preserved scroll state
            if (uiState.callLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveEmptyState(
                        title = "No recent calls",
                        description = "Your recent calls and call logs will appear here. Tap the dialpad button below to make a call.",
                        icon = Icons.Outlined.PhoneMissed,
                        actionLabel = if (!uiState.isSyncing) "Sync Logs" else null,
                        onActionClick = { onAction(DialerUiAction.SyncLogs) }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp) // Room for FAB
                ) {
                    items(
                        items = uiState.callLogs,
                        key = { it.id }
                    ) { call ->
                        CallCard(
                            call = call,
                            onClick = { onCallClick?.invoke(call.id) },
                            onFavoriteToggle = { onAction(DialerUiAction.ToggleFavorite(call.id)) },
                            onCallClick = { onAction(DialerUiAction.DirectCall(call.number)) }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. DIALPAD MODE VIEW
// ==========================================

@Composable
private fun DialpadModeView(
    uiState: DialerUiState,
    onAction: (DialerUiAction) -> Unit,
    onMenuClick: (() -> Unit)?,
    onOpenSimulator: () -> Unit,
    onNavigateToContact: ((String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onMenuClick != null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = Slate300
                        )
                    }
                }
                Text(
                    text = "Dialer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Call Simulator Quick Button
                IconButton(
                    onClick = onOpenSimulator,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Teal500.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneCallback,
                        contentDescription = "Simulate Call",
                        tint = Teal300,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (uiState.isDefaultDialer) Green500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (uiState.isDefaultDialer) Green500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.PhoneCallback,
                            contentDescription = null,
                            tint = if (uiState.isDefaultDialer) Green500 else Amber500,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (uiState.isDefaultDialer) "Default Phone" else "System Dialer",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.isDefaultDialer) Green500 else Amber500
                        )
                    }
                }
            }
        }

        // Top Section: T9 Search Results / Clipboard Suggestions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            // Clipboard Suggestion Chip
            AnimatedVisibility(
                visible = !uiState.clipboardSuggestion.isNullOrBlank() && uiState.inputNumber.isBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    onClick = { onAction(DialerUiAction.PasteClipboard) },
                    shape = RoundedCornerShape(12.dp),
                    color = Teal500.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Teal500.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Teal300,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Paste: ${uiState.clipboardSuggestion ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal300,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // T9 Matching Contacts List
            AnimatedVisibility(
                visible = uiState.t9Results.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(uiState.t9Results, key = { it.phoneNumber + (it.personId ?: "") }) { match ->
                        T9ContactItem(
                            match = match,
                            onItemClick = {
                                onAction(DialerUiAction.SetInputNumber(match.phoneNumber))
                            },
                            onCallClick = { sim ->
                                onAction(DialerUiAction.PlaceCall(match.phoneNumber, sim))
                            },
                            activeSims = uiState.activeSims
                        )
                    }
                }
            }
        }

        // Middle Section: Number Display & Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = uiState.inputNumber.ifEmpty { "Enter phone number" },
                    style = if (uiState.inputNumber.length > 14) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.inputNumber.isNotEmpty()) Slate50 else Slate500,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                if (uiState.inputNumber.isNotEmpty()) {
                    IconButton(
                        onClick = { onAction(DialerUiAction.Backspace) }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Slate400,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Keypad Grid
        DialpadGrid(
            onDigitClick = { digit -> onAction(DialerUiAction.InputDigit(digit)) },
            onDigitLongClick = { digit ->
                if (digit == '0') {
                    onAction(DialerUiAction.LongPressZero)
                } else if (digit in '1'..'9') {
                    onAction(DialerUiAction.SpeedDial(digit.digitToInt()))
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom Section: Dual-SIM / Single-SIM Call Buttons
        DualSimCallButtons(
            inputNumber = uiState.inputNumber,
            activeSims = uiState.activeSims,
            onCallClick = { sim ->
                onAction(DialerUiAction.PlaceCall(uiState.inputNumber, sim))
            }
        )
    }
}

// ==========================================
// 4. HELPER COMPONENTS
// ==========================================

@Composable
private fun T9ContactItem(
    match: T9MatchResult,
    onItemClick: () -> Unit,
    onCallClick: (SimInfo?) -> Unit,
    activeSims: List<SimInfo>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate900.copy(alpha = 0.9f))
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .clickable(onClick = onItemClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ContactAvatar(
            name = match.displayName,
            initials = match.displayName.take(2).uppercase(),
            photoUri = null,
            modifier = Modifier.size(36.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val annotatedName = buildAnnotatedString {
                    val name = match.displayName
                    val range = match.matchedNameRange
                    if (range != null && range.first in name.indices && range.last < name.length) {
                        append(name.substring(0, range.first))
                        withStyle(SpanStyle(color = Teal300, fontWeight = FontWeight.Bold)) {
                            append(name.substring(range.first, range.last + 1))
                        }
                        append(name.substring(range.last + 1))
                    } else {
                        append(name)
                    }
                }

                Text(
                    text = annotatedName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (match.crmStatus != LeadStatus.UNKNOWN) {
                    val statusColor = when (match.crmStatus) {
                        LeadStatus.HOT -> Red500
                        LeadStatus.WARM -> Amber500
                        LeadStatus.COLD -> AllSetBlue
                        else -> Slate400
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = match.crmStatus.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            val subText = buildString {
                match.companyName?.takeIf { it.isNotBlank() }?.let { append("$it • ") }
                append(match.phoneNumber)
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { onCallClick(activeSims.firstOrNull()) },
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Green500.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = Green500,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DialpadGrid(
    onDigitClick: (Char) -> Unit,
    onDigitLongClick: (Char) -> Unit
) {
    val keypadLayout = listOf(
        listOf('1' to "", '2' to "ABC", '3' to "DEF"),
        listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
        listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
        listOf('*' to "", '0' to "+", '#' to "")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keypadLayout.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DialpadButton(
                        digit = digit,
                        letters = letters,
                        onClick = { onDigitClick(digit) },
                        onLongClick = { onDigitLongClick(digit) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadButton(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Slate900)
            .border(1.dp, Slate800, CircleShape)
            .semantics { role = Role.Button }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate50,
                fontSize = 24.sp
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun DualSimCallButtons(
    inputNumber: String,
    activeSims: List<SimInfo>,
    onCallClick: (SimInfo?) -> Unit
) {
    val isEnabled = inputNumber.isNotBlank()

    if (activeSims.size >= 2) {
        // Dual SIM Layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sim1 = activeSims[0]
            val sim2 = activeSims[1]

            Button(
                onClick = { onCallClick(sim1) },
                enabled = isEnabled,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call SIM 1",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SIM 1",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Slate400
                        )
                        Text(
                            text = sim1.carrierName.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isEnabled) Slate300 else Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Button(
                onClick = { onCallClick(sim2) },
                enabled = isEnabled,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call SIM 2",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SIM 2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Slate400
                        )
                        Text(
                            text = sim2.carrierName.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isEnabled) Slate300 else Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Single SIM Layout
        val singleSim = activeSims.firstOrNull()
        val carrierLabel = singleSim?.let { " (${it.carrierName})" } ?: ""

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { onCallClick(singleSim) },
                enabled = isEnabled,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Call$carrierLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color.White else Slate400
                    )
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Dialer Screen - Call Logs Mode", showBackground = true)
@Preview(name = "Dialer Screen - Call Logs Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DialerScreenCallLogsPreview() {
    CallogTheme {
        CallLogsModeView(
            uiState = DialerUiState(
                callLogs = listOf(
                    CallLogEntry(
                        id = 1L,
                        name = "Rahul Sharma",
                        number = "+91 98765 43210",
                        duration = 222,
                        timestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                        callType = "INCOMING",
                        recordingPath = null,
                        isFavorite = true,
                        notes = "Lead follow up",
                        tags = listOf("hot"),
                        contactPhotoUri = null,
                        syncStatus = "SYNCED"
                    )
                )
            ),
            listState = rememberLazyListState(),
            onAction = {},
            onCallClick = {},
            onMenuClick = {},
            onOpenSimulator = {}
        )
    }
}

@Preview(name = "Dialer Screen - Keypad Mode", showBackground = true)
@Composable
fun DialerScreenKeypadPreview() {
    CallogTheme {
        DialpadModeView(
            uiState = DialerUiState(
                mode = DialerMode.Dialpad,
                inputNumber = "987654",
                activeSims = listOf(
                    SimInfo(1, 0, "Jio 5G", "SIM 1", "+91 98765 43210", true)
                ),
                isDefaultDialer = true
            ),
            onAction = {},
            onMenuClick = {},
            onOpenSimulator = {},
            onNavigateToContact = {}
        )
    }
}
