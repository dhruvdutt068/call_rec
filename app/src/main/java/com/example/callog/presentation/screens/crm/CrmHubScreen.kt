package com.example.callog.presentation.screens.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.screens.analytics.AnalyticsScreen
import com.example.callog.presentation.screens.dashboard.DashboardScreen
import com.example.callog.presentation.screens.meetings.MeetingListScreen
import com.example.callog.presentation.screens.tasks.TaskListScreen
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.AnalyticsViewModel
import com.example.callog.presentation.viewmodel.CallViewModel

enum class CrmSubTab(
    val title: String,
    val icon: ImageVector
) {
    PIPELINE("Pipeline", Icons.Default.FilterList),
    OVERVIEW("Overview", Icons.Default.Dashboard),
    TASKS("Tasks", Icons.Default.CheckCircle),
    MEETINGS("Meetings", Icons.Default.Event),
    ANALYTICS("Analytics", Icons.Default.BarChart)
}

@Composable
fun CrmHubScreen(
    callViewModel: CallViewModel,
    analyticsViewModel: AnalyticsViewModel,
    onViewAllLogsClick: () -> Unit,
    onCallClick: (Long) -> Unit,
    onCreateTaskClick: () -> Unit,
    onTaskClick: (String) -> Unit,
    onScheduleMeetingClick: () -> Unit,
    onMeetingClick: (String) -> Unit,
    onContactClick: ((String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(CrmSubTab.PIPELINE) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate900)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    text = "CRM Workspace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            }

            IconButton(onClick = onViewAllLogsClick) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Call Logs",
                    tint = Teal300
                )
            }
        }

        // Top Scrollable TabRow for CRM sub-features
        Surface(
            color = Slate900,
            modifier = Modifier.fillMaxWidth()
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedSubTab.ordinal,
                containerColor = Slate900,
                contentColor = Slate50,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    if (selectedSubTab.ordinal < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedSubTab.ordinal]),
                            color = AllSetBlue
                        )
                    }
                }
            ) {
                CrmSubTab.values().forEach { tab ->
                    val isSelected = selectedSubTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedSubTab = tab },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) AllSetLavender else Slate400
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
                CrmSubTab.PIPELINE -> {
                    PipelineBoardScreen(
                        callViewModel = callViewModel,
                        onContactClick = onContactClick,
                        onCreateLeadClick = {
                            // Can switch to tasks or trigger lead creation
                        }
                    )
                }
                CrmSubTab.OVERVIEW -> {
                    DashboardScreen(
                        callViewModel = callViewModel,
                        analyticsViewModel = analyticsViewModel,
                        onViewAllLogsClick = onViewAllLogsClick,
                        onCallClick = onCallClick
                    )
                }
                CrmSubTab.TASKS -> {
                    TaskListScreen(
                        onCreateTaskClick = onCreateTaskClick,
                        onTaskClick = onTaskClick
                    )
                }
                CrmSubTab.MEETINGS -> {
                    MeetingListScreen(
                        onScheduleMeetingClick = onScheduleMeetingClick,
                        onMeetingClick = onMeetingClick
                    )
                }
                CrmSubTab.ANALYTICS -> {
                    AnalyticsScreen(
                        viewModel = analyticsViewModel
                    )
                }
            }
        }
    }
}

data class PipelineStage(
    val id: String,
    val name: String,
    val icon: ImageVector,
    val color: androidx.compose.ui.graphics.Color
)

data class PipelineDeal(
    val id: String,
    val personId: String,
    val contactName: String,
    val company: String,
    val phoneNumber: String,
    val dealAmount: String,
    val stageId: String,
    val priority: String, // HIGH, MEDIUM, LOW
    val lastCallTime: String
)

@Composable
fun PipelineBoardScreen(
    callViewModel: CallViewModel,
    onContactClick: ((String) -> Unit)? = null,
    onCreateLeadClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val contacts by callViewModel.contacts.collectAsState()
    val callLogs by callViewModel.callLogs.collectAsState()

    val stages = remember {
        listOf(
            PipelineStage("NEW", "New Leads", Icons.Default.FiberNew, Color(0xFF4A4FD8)),
            PipelineStage("CONTACTED", "Contacted", Icons.Default.PhoneCallback, Color(0xFF45C79A)),
            PipelineStage("QUALIFIED", "Qualified", Icons.Default.Stars, Color(0xFFF4B544)),
            PipelineStage("PROPOSAL", "Proposal", Icons.Default.Description, Color(0xFFB8BCFF)),
            PipelineStage("WON", "Closed / Won", Icons.Default.CheckCircle, Color(0xFF45C79A))
        )
    }

    var selectedStageId by remember { mutableStateOf("NEW") }

    // Generate dynamic pipeline deals from active contacts & call logs
    val deals = remember(contacts, callLogs) {
        val sampleStages = listOf("NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "WON")
        val sampleAmounts = listOf("₹45,000", "₹1,20,000", "₹85,000", "₹2,50,000", "₹60,000", "₹1,80,000", "₹95,000")
        val samplePriorities = listOf("HIGH", "MEDIUM", "LOW")
        val dateFormat = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())

        contacts.take(15).mapIndexed { index, contact ->
            val assignedStage = sampleStages[index % sampleStages.size]
            val amount = sampleAmounts[index % sampleAmounts.size]
            val priority = samplePriorities[index % samplePriorities.size]
            val phone = contact.phoneNumbers.firstOrNull() ?: "+91 98765 43210"
            val lastCall = callLogs.find { it.number.contains(phone.takeLast(6)) }?.let {
                dateFormat.format(java.util.Date(it.timestamp))
            } ?: "Recently"

            PipelineDeal(
                id = "DEAL_$index",
                personId = contact.contactId,
                contactName = contact.name,
                company = if (index % 2 == 0) "Enterprise Client" else "Retail Customer",
                phoneNumber = phone,
                dealAmount = amount,
                stageId = assignedStage,
                priority = priority,
                lastCallTime = lastCall
            )
        }
    }

    val activeStageDeals = remember(deals, selectedStageId) {
        deals.filter { it.stageId == selectedStageId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pipeline summary card
        GlassyCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sales Pipeline Volume",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    Text(
                        text = "₹8,35,000",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Green500
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Active Deals", style = MaterialTheme.typography.labelSmall, color = Slate400)
                        Text("${deals.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate50)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Win Rate", style = MaterialTheme.typography.labelSmall, color = Slate400)
                        Text("68%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AllSetLavender)
                    }
                }
            }
        }

        // Horizontal Stages Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            stages.forEach { stage ->
                val isSelected = selectedStageId == stage.id
                val countInStage = deals.count { it.stageId == stage.id }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) stage.color.copy(alpha = 0.2f) else Slate900,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) stage.color else Slate800
                    ),
                    modifier = Modifier.clickable { selectedStageId = stage.id }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = stage.icon,
                            contentDescription = null,
                            tint = if (isSelected) stage.color else Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stage.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Slate50 else Slate300
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) stage.color else Slate800,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                text = "$countInStage",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Slate300,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Stage Deals List
        if (activeStageDeals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No deals in this stage yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
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
                items(activeStageDeals, key = { it.id }) { deal ->
                    PipelineDealCard(
                        deal = deal,
                        onClick = { onContactClick?.invoke(deal.personId) },
                        onCallClick = {
                            callViewModel.initiateCall(context, deal.phoneNumber)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PipelineDealCard(
    deal: PipelineDeal,
    onClick: () -> Unit = {},
    onCallClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deal.contactName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = deal.company,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (deal.priority) {
                        "HIGH" -> Red500.copy(alpha = 0.2f)
                        "MEDIUM" -> Amber500.copy(alpha = 0.2f)
                        else -> Slate700
                    }
                ) {
                    Text(
                        text = deal.priority,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (deal.priority) {
                            "HIGH" -> Red500
                            "MEDIUM" -> Amber500
                            else -> Slate300
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = Slate800)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MonetizationOn,
                        contentDescription = null,
                        tint = Green500,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = deal.dealAmount,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Green500
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deal.lastCallTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )

                    IconButton(
                        onClick = onCallClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(AllSetBlue.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Call",
                            tint = AllSetBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
