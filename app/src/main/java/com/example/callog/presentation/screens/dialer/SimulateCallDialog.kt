package com.example.callog.presentation.screens.dialer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.domain.model.SimulatedCallConfig
import com.example.callog.domain.model.SimulationPreset
import com.example.callog.domain.service.simulator.CallSimulatorManager
import com.example.callog.presentation.theme.*
import com.example.callog.sim.SimInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulateCallBottomSheet(
    initialNumber: String,
    activeSims: List<SimInfo>,
    simulatorManager: CallSimulatorManager,
    onDismiss: () -> Unit,
    onNavigateToStudio: () -> Unit
) {
    var phoneNumberInput by remember { mutableStateOf(initialNumber.ifBlank { "+1 (555) 349-8821" }) }
    var callerNameInput by remember { mutableStateOf("Simulated Contact") }
    var selectedSimSlot by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        contentColor = Slate50,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate700) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Quick Call Simulator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Slate50
                    )
                    Text(
                        text = "Test incoming & outgoing in-call flows instantly",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        onNavigateToStudio()
                    }
                ) {
                    Text("Open Studio", color = Teal300, fontWeight = FontWeight.Bold)
                }
            }

            // Target Number Field
            OutlinedTextField(
                value = phoneNumberInput,
                onValueChange = { phoneNumberInput = it },
                label = { Text("Target Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Teal300) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal300,
                    unfocusedBorderColor = Slate700
                )
            )

            // SIM Card Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val simList = if (activeSims.isNotEmpty()) activeSims else listOf(
                    SimInfo(subscriptionId = 1, slotIndex = 0, carrierName = "SIM 1", displayName = "SIM 1 (Personal)", phoneNumber = "+1 (555) 000-1", isActive = true),
                    SimInfo(subscriptionId = 2, slotIndex = 1, carrierName = "SIM 2", displayName = "SIM 2 (Business)", phoneNumber = "+1 (555) 000-2", isActive = true)
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
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = if (isSelected) Teal300 else Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
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

            // Quick Simulation Action Buttons
            Text(
                text = "Simulation Triggers",
                style = MaterialTheme.typography.labelMedium,
                color = Slate400
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "Simulate Incoming",
                    subtitle = "Ringing -> Answer/Decline",
                    icon = Icons.Default.CallReceived,
                    color = Teal500,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val config = SimulatedCallConfig(
                            callId = "sim_inc_${System.currentTimeMillis()}",
                            direction = CallDirection.INCOMING,
                            callerName = callerNameInput,
                            phoneNumber = phoneNumberInput,
                            crmStatus = LeadStatus.HOT,
                            priority = LeadPriority.HIGH,
                            simSlot = selectedSimSlot
                        )
                        simulatorManager.startSimulation(config)
                        onDismiss()
                    }
                )

                QuickActionButton(
                    title = "Simulate Outgoing",
                    subtitle = "Dialing -> Remote Answer",
                    icon = Icons.Default.CallMade,
                    color = AllSetBlue,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val config = SimulatedCallConfig(
                            callId = "sim_out_${System.currentTimeMillis()}",
                            direction = CallDirection.OUTGOING,
                            callerName = callerNameInput,
                            phoneNumber = phoneNumberInput,
                            crmStatus = LeadStatus.WARM,
                            priority = LeadPriority.HIGH,
                            simSlot = selectedSimSlot,
                            autoAnswerDelaySec = 2
                        )
                        simulatorManager.startSimulation(config)
                        onDismiss()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    title = "Missed Call",
                    subtitle = "Rings 4s then hangs up",
                    icon = Icons.Default.PhoneMissed,
                    color = Amber500,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val config = SimulatedCallConfig(
                            callId = "sim_missed_${System.currentTimeMillis()}",
                            direction = CallDirection.INCOMING,
                            callerName = "Missed Caller",
                            phoneNumber = phoneNumberInput,
                            crmStatus = LeadStatus.COLD,
                            priority = LeadPriority.LOW,
                            simSlot = selectedSimSlot,
                            autoHangupDurationSec = 4
                        )
                        simulatorManager.startSimulation(config)
                        onDismiss()
                    }
                )

                QuickActionButton(
                    title = "VIP Inbound",
                    subtitle = "Hot Lead CRM Preset",
                    icon = Icons.Default.Star,
                    color = Teal300,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        simulatorManager.launchPreset(SimulationPreset.VIP_INCOMING, selectedSimSlot)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        color = Slate800,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Text(text = title, fontWeight = FontWeight.Bold, color = Slate50, style = MaterialTheme.typography.bodyMedium)
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = Slate400)
        }
    }
}
