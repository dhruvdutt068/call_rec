package com.example.callog.presentation.screens.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.screens.dialer.DialerScreen
import com.example.callog.presentation.screens.favorites.FavoritesScreen
import com.example.callog.presentation.screens.logs.CallLogsScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

enum class CallsSubTab(
    val title: String,
    val icon: ImageVector
) {
    LOGS("History", Icons.Default.History),
    DIALER("Keypad", Icons.Default.Dialpad),
    FAVORITES("Favorites", Icons.Default.Star)
}

@Composable
fun CallsHubScreen(
    viewModel: CallViewModel,
    onCallClick: (Long) -> Unit,
    onContactClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(CallsSubTab.LOGS) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Top Segmented Bar for Calls sub-features
        Surface(
            color = Slate900,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = selectedSubTab.ordinal,
                containerColor = Slate900,
                contentColor = Slate50,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedSubTab.ordinal]),
                        color = Teal500
                    )
                }
            ) {
                CallsSubTab.values().forEach { tab ->
                    val isSelected = selectedSubTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSubTab = tab },
                        text = {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) Teal300 else Slate400
                                )
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Slate50 else Slate400
                                )
                            }
                        }
                    )
                }
            }
        }

        // Sub-screen content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                CallsSubTab.LOGS -> {
                    CallLogsScreen(
                        viewModel = viewModel,
                        onCallClick = onCallClick,
                        onBackClick = null
                    )
                }
                CallsSubTab.DIALER -> {
                    DialerScreen(
                        onNavigateToContact = { contactId -> onContactClick?.invoke(contactId) }
                    )
                }
                CallsSubTab.FAVORITES -> {
                    FavoritesScreen(
                        viewModel = viewModel,
                        onCallClick = onCallClick,
                        onContactClick = onContactClick
                    )
                }
            }
        }
    }
}
