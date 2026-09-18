package com.example.callog.presentation.screens.developer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallState
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.SimulatedCallConfig
import com.example.callog.domain.model.SimulationPreset
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.di.TelecomEntryPoint
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel
import com.example.callog.sim.SimInfo
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSimulatorScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    callViewModel: CallViewModel = hiltViewModel(),
    simulatorManager: CallSimulatorManager? = null
) {
    BackHandler {
        onBackClick()
    }

    val context = LocalContext.current
    val simManager = simulatorManager ?: remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            TelecomEntryPoint::class.java
        ).callSimulatorManager()
    }
    val engineState by simManager.engineState.collectAsState()
    val activeSims by callViewModel.activeSims.collectAsState()
    val allCalls by callViewModel.callLogs.collectAsState()

    // Form states for Custom Simulation
    var selectedDirection by remember { mutableStateOf(CallDirection.INCOMING) }
    var callerName by remember { mutableStateOf("Sophia Patel") }
    var phoneNumber by remember { mutableStateOf("+1 (555) 349-8821") }
    var companyName by remember { mutableStateOf("NexaCorp Global") }
    var selectedLeadStatus by remember { mutableStateOf(LeadStatus.HOT) }
    var selectedPriority by remember { mutableStateOf(LeadPriority.HIGH) }
    var selectedSimSlot by remember { mutableIntStateOf(0) }

    var enableAutoAnswer by remember { mutableStateOf(false) }
    var autoAnswerSec by remember { mutableFloatStateOf(3f) }

    var enableAutoHangup by remember { mutableStateOf(false) }
    var autoHangupSec by remember { mutableFloatStateOf(15f) }

    var recordToDatabase by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Call Simulation Studio", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (engineState.isRunning) "Simulation Active • ${engineState.currentCallState}" else "Ready to simulate calls",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (engineState.isRunning) Teal300 else Slate400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (engineState.isRunning) {
                        TextButton(
                            onClick = { simManager.cancelSimulation() },
                            colors = ButtonDefaults.textButtonColors(contentColor = Red500)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Simulation", fontWeight = FontWeight.Bold)
                        }
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Live Simulation Status Banner / HUD
            if (engineState.isRunning) {
                LiveSimulationHudCard(
                    engineState = engineState,
                    onRemoteAnswer = { simManager.remoteAnswer() },
                    onRemoteHangup = { simManager.remoteHangup() },
                    onSpawnSecondaryCall = {
                        simManager.simulateSecondaryIncomingCall(
                            callerName = "David Kim (Secondary Call)",
                            phoneNumber = "+1 (555) 782-9014",
                            companyName = "Apex Retail",
                            simSlot = 1
                        )
                    },
                    onOpenInCallScreen = {
                        try {
                            val intent = android.content.Intent(context, com.example.callog.presentation.call.InCallActivity::class.java).apply {
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("CallSimulatorScreen", "Failed to open InCallActivity", e)
                        }
                    }
                )
            }

            // Section 1: Quick Presets Carousel
            DashboardSectionHeader(title = "1-Tap Quick Presets")
            Text(
                text = "Simulate realistic incoming and outgoing CRM interactions with rich caller data, ringtones, and multi-call line cards.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate400
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(SimulationPreset.entries) { preset ->
                    PresetSimulationCard(
                        preset = preset,
                        isRunning = engineState.isRunning && engineState.activeConfig?.preset == preset,
                        onLaunch = {
                            simManager.launchPreset(preset, selectedSimSlot)
                        }
                    )
                }
            }

            // Section 2: Custom Simulation Builder
            DashboardSectionHeader(title = "Custom Simulation Builder")

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Direction Switcher Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .padding(4.dp)
                    ) {
                        val isIncoming = selectedDirection == CallDirection.INCOMING
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDirection = CallDirection.INCOMING },
                            color = if (isIncoming) Teal500 else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallReceived,
                                    contentDescription = null,
                                    tint = if (isIncoming) Slate50 else Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Incoming Call",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncoming) Slate50 else Slate400,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        val isOutgoing = selectedDirection == CallDirection.OUTGOING
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedDirection = CallDirection.OUTGOING },
                            color = if (isOutgoing) AllSetBlue else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallMade,
                                    contentDescription = null,
                                    tint = if (isOutgoing) Slate50 else Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Outgoing Call",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutgoing) Slate50 else Slate400,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Caller Name & Phone Number
                    OutlinedTextField(
                        value = callerName,
                        onValueChange = { callerName = it },
                        label = { Text(if (selectedDirection == CallDirection.INCOMING) "Caller Name" else "Recipient Name") },
                        placeholder = { Text("e.g. Sarah Connor") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Teal300) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Phone Number") },
                        placeholder = { Text("+1 (555) 000-1234") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Teal300) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    OutlinedTextField(
                        value = companyName,
                        onValueChange = { companyName = it },
                        label = { Text("Company / Organization (Optional)") },
                        placeholder = { Text("e.g. Cyberdyne Systems") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Teal300) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal300,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    // CRM Lead Status & Priority Pickers
                    Text("CRM Lead Status", style = MaterialTheme.typography.labelMedium, color = Slate400)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(LeadStatus.HOT, LeadStatus.WARM, LeadStatus.COLD, LeadStatus.UNKNOWN).forEach { status ->
                            val isSelected = selectedLeadStatus == status
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLeadStatus = status },
                                label = { Text(status.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = when (status) {
                                        LeadStatus.HOT -> Red500.copy(alpha = 0.25f)
                                        LeadStatus.WARM -> Amber500.copy(alpha = 0.25f)
                                        LeadStatus.COLD -> Teal300.copy(alpha = 0.25f)
                                        else -> Slate700
                                    },
                                    selectedLabelColor = when (status) {
                                        LeadStatus.HOT -> Red500
                                        LeadStatus.WARM -> Amber500
                                        LeadStatus.COLD -> Teal300
                                        else -> Slate50
                                    }
                                )
                            )
                        }
                    }

                    Text("Lead Priority", style = MaterialTheme.typography.labelMedium, color = Slate400)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(LeadPriority.URGENT, LeadPriority.HIGH, LeadPriority.MEDIUM, LeadPriority.LOW).forEach { priority ->
                            val isSelected = selectedPriority == priority
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPriority = priority },
                                label = { Text(priority.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (priority == LeadPriority.URGENT || priority == LeadPriority.HIGH) Amber500.copy(alpha = 0.25f) else Slate700,
                                    selectedLabelColor = if (priority == LeadPriority.URGENT || priority == LeadPriority.HIGH) Amber500 else Slate50
                                )
                            )
                        }
                    }

                    // SIM Card Selector
                    Text("SIM Card Route", style = MaterialTheme.typography.labelMedium, color = Slate400)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val simList = if (activeSims.isNotEmpty()) activeSims else listOf(
                            SimInfo(subscriptionId = 1, slotIndex = 0, carrierName = "SIM 1 (Personal)", displayName = "SIM 1", phoneNumber = "+1 (555) 000-1", isActive = true),
                            SimInfo(subscriptionId = 2, slotIndex = 1, carrierName = "SIM 2 (Business)", displayName = "SIM 2", phoneNumber = "+1 (555) 000-2", isActive = true)
                        )

                        simList.forEachIndexed { index, sim ->
                            val isSelected = selectedSimSlot == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSimSlot = index }
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Teal300 else Slate700,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                color = if (isSelected) Teal500.copy(alpha = 0.15f) else Slate800
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SimCard,
                                        contentDescription = null,
                                        tint = if (isSelected) Teal300 else Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = sim.displayName.ifBlank { "SIM ${index + 1}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Slate50 else Slate300
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 4.dp))

                    // Automation Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Auto-Answer Delay", fontWeight = FontWeight.SemiBold, color = Slate50)
                            Text(
                                text = if (enableAutoAnswer) "Answers automatically in ${autoAnswerSec.toInt()}s" else "Manual interactive answer on screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                        Switch(
                            checked = enableAutoAnswer,
                            onCheckedChange = { enableAutoAnswer = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Teal300, checkedTrackColor = Teal500)
                        )
                    }

                    if (enableAutoAnswer) {
                        Slider(
                            value = autoAnswerSec,
                            onValueChange = { autoAnswerSec = it },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = Teal300, activeTrackColor = Teal500)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Auto-Hangup Duration", fontWeight = FontWeight.SemiBold, color = Slate50)
                            Text(
                                text = if (enableAutoHangup) "Ends call after ${autoHangupSec.toInt()}s" else "Manual interactive hangup",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                        Switch(
                            checked = enableAutoHangup,
                            onCheckedChange = { enableAutoHangup = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Teal300, checkedTrackColor = Teal500)
                        )
                    }

                    if (enableAutoHangup) {
                        Slider(
                            value = autoHangupSec,
                            onValueChange = { autoHangupSec = it },
                            valueRange = 5f..60f,
                            steps = 10,
                            colors = SliderDefaults.colors(thumbColor = Teal300, activeTrackColor = Teal500)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Save to Room DB & Call Logs", fontWeight = FontWeight.SemiBold, color = Slate50)
                            Text("Inserts CallEntity & SalesCallEntity upon wrap-up", style = MaterialTheme.typography.bodySmall, color = Slate400)
                        }
                        Switch(
                            checked = recordToDatabase,
                            onCheckedChange = { recordToDatabase = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Teal300, checkedTrackColor = Teal500)
                        )
                    }

                    // Launch Simulation Button
                    Button(
                        onClick = {
                            val simSlotInfo = activeSims.getOrNull(selectedSimSlot)
                            val customConfig = SimulatedCallConfig(
                                callId = "sim_custom_${System.currentTimeMillis()}",
                                direction = selectedDirection,
                                callerName = callerName.ifBlank { "Test Caller" },
                                phoneNumber = phoneNumber.ifBlank { "+1 (555) 000-1234" },
                                companyName = companyName.takeIf { it.isNotBlank() },
                                crmStatus = selectedLeadStatus,
                                priority = selectedPriority,
                                simSlot = selectedSimSlot,
                                simDisplayName = simSlotInfo?.displayName ?: "SIM ${selectedSimSlot + 1}",
                                autoAnswerDelaySec = if (enableAutoAnswer) autoAnswerSec.toInt() else 0,
                                autoHangupDurationSec = if (enableAutoHangup) autoHangupSec.toInt() else 0,
                                recordToDatabase = recordToDatabase
                            )
                            simManager.startSimulation(customConfig)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDirection == CallDirection.INCOMING) Teal500 else AllSetBlue,
                            contentColor = Slate50
                        )
                    ) {
                        Icon(
                            imageVector = if (selectedDirection == CallDirection.INCOMING) Icons.Default.CallReceived else Icons.Default.CallMade,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (selectedDirection == CallDirection.INCOMING) "Launch Incoming Call Simulation" else "Launch Outgoing Call Simulation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetSimulationCard(
    preset: SimulationPreset,
    isRunning: Boolean,
    onLaunch: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        modifier = Modifier
            .width(260.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isRunning) 2.dp else 1.dp,
                color = if (isRunning) Teal300 else Slate700,
                shape = RoundedCornerShape(16.dp)
            ),
        color = if (isRunning) Slate800 else Slate900.copy(alpha = 0.85f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when (preset.direction) {
                                    CallDirection.INCOMING -> Teal500.copy(alpha = 0.2f)
                                    CallDirection.OUTGOING -> AllSetBlue.copy(alpha = 0.2f)
                                    else -> Slate700
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (preset) {
                                SimulationPreset.VIP_INCOMING -> Icons.Default.Star
                                SimulationPreset.SALES_OUTBOUND -> Icons.Default.TrendingUp
                                SimulationPreset.NEW_UNKNOWN_LEAD -> Icons.Default.PersonAdd
                                SimulationPreset.MISSED_CALL_TEST -> Icons.Default.PhoneMissed
                                SimulationPreset.CALL_WAITING_DUAL -> Icons.Default.PhoneCallback
                            },
                            contentDescription = null,
                            tint = when (preset.direction) {
                                CallDirection.INCOMING -> Teal300
                                CallDirection.OUTGOING -> AllSetLavender
                                else -> Slate300
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (preset.defaultLeadStatus) {
                            LeadStatus.HOT -> Red500.copy(alpha = 0.2f)
                            LeadStatus.WARM -> Amber500.copy(alpha = 0.2f)
                            else -> Teal500.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = preset.defaultLeadStatus.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when (preset.defaultLeadStatus) {
                                LeadStatus.HOT -> Red500
                                LeadStatus.WARM -> Amber500
                                else -> Teal300
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = preset.title,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Teal300 else Teal500,
                    contentColor = if (isRunning) Slate950 else Slate50
                )
            ) {
                Icon(
                    imageVector = if (preset.direction == CallDirection.INCOMING) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "Active..." else "Launch Preset",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LiveSimulationHudCard(
    engineState: com.example.callog.domain.model.SimulatedEngineState,
    onRemoteAnswer: () -> Unit,
    onRemoteHangup: () -> Unit,
    onSpawnSecondaryCall: () -> Unit,
    onOpenInCallScreen: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(2.dp, Teal300, RoundedCornerShape(16.dp)),
        color = Slate900,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (engineState.currentCallState == CallState.ACTIVE) Green500 else Amber500)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE SIMULATION: ${engineState.currentCallState.name}",
                        fontWeight = FontWeight.Bold,
                        color = Slate50,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Teal500.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (engineState.currentCallState == CallState.ACTIVE) "${engineState.elapsedActiveSeconds}s active" else "Ringing...",
                        color = Teal300,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val config = engineState.activeConfig
            if (config != null) {
                Text(
                    text = "${config.callerName} • ${config.phoneNumber} (${config.direction.name})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate300
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (engineState.currentCallState == CallState.RINGING || engineState.currentCallState == CallState.CONNECTING) {
                    Button(
                        onClick = onRemoteAnswer,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Green500, contentColor = Slate950)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remote Answer", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onRemoteHangup,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Red500, contentColor = Slate50)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remote Hang Up", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSpawnSecondaryCall,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Teal300)
                ) {
                    Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("2nd Call Waiting", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = onOpenInCallScreen,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate50)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open In-Call UI", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
