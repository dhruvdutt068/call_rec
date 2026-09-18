package com.example.callog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.tooling.preview.Preview
import android.content.res.Configuration
import com.example.callog.data.provider.SimInfo
import com.example.callog.presentation.theme.*

@Composable
fun BusinessSimWizardDialog(
    activeSims: List<SimInfo>,
    onSelectSim: (SimInfo) -> Unit,
    onSelectSyncAll: () -> Unit,
    isSuspendedDueToChange: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = { 
            // Prevent dismiss if configuration is mandatory
            if (onDismiss != null) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = onDismiss != null,
            dismissOnClickOutside = onDismiss != null,
            usePlatformDefaultWidth = false
        )
    ) {
        BusinessSimWizardContent(
            activeSims = activeSims,
            onSelectSim = onSelectSim,
            onSelectSyncAll = onSelectSyncAll,
            isSuspendedDueToChange = isSuspendedDueToChange,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun BusinessSimWizardContent(
    activeSims: List<SimInfo>,
    onSelectSim: (SimInfo) -> Unit,
    onSelectSyncAll: () -> Unit,
    isSuspendedDueToChange: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSim by remember { mutableStateOf<SimInfo?>(activeSims.firstOrNull()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate900.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth(0.92f)
            .wrapContentHeight()
            .border(1.dp, Teal500.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Teal500.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SimCard,
                    contentDescription = null,
                    tint = Teal300,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = if (isSuspendedDueToChange) "Business SIM Changed" else "Configure Business SIM",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate50,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isSuspendedDueToChange) {
                    "Your active SIM cards have changed. Please select which SIM should log business calls to resume syncing."
                } else {
                    "Select the SIM card used for business calls. Only calls associated with this SIM will be synchronized to Room, Firebase, and Supabase."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Slate400,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // SIM Options list
            if (activeSims.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Red500.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No active SIM card detected. Call log filtering is suspended.",
                        color = Red500,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                activeSims.forEach { sim ->
                    val isSelected = selectedSim?.subscriptionId == sim.subscriptionId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Teal500.copy(alpha = 0.15f) else Slate800
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSim = sim
                            }
                            .border(
                                1.dp,
                                if (isSelected) Teal300 else Color.Transparent,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) Teal500.copy(alpha = 0.25f)
                                            else Slate700.copy(alpha = 0.4f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (isSelected) Teal300 else Slate400,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = sim.carrierName,
                                        color = Slate50,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "SIM ${sim.slotIndex + 1} • ${sim.phoneNumber}",
                                        color = Slate400,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    selectedSim = sim
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Teal300,
                                    unselectedColor = Slate400
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onDismiss != null) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Slate400
                        ),
                        border = borderStroke(1.dp, Slate700)
                    ) {
                        Text("Cancel")
                    }
                }

                Button(
                    onClick = {
                        selectedSim?.let { onSelectSim(it) }
                    },
                    enabled = selectedSim != null,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Teal500,
                        disabledContainerColor = Slate800,
                        disabledContentColor = Slate400
                    )
                ) {
                    Text(
                        text = "Save",
                        fontWeight = FontWeight.Bold,
                        color = if (selectedSim != null) Slate50 else Slate400
                    )
                }
            }
        }
    }
}

private fun borderStroke(width: androidx.compose.ui.unit.Dp, color: Color) = 
    androidx.compose.foundation.BorderStroke(width, color)

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Dual SIM Setup", showBackground = true)
@Preview(name = "Dual SIM Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun BusinessSimWizardPreview() {
    CallogTheme {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            BusinessSimWizardContent(
                activeSims = listOf(
                    SimInfo(
                        subscriptionId = 1,
                        slotIndex = 0,
                        carrierName = "Jio 5G",
                        displayName = "Jio Business",
                        phoneNumber = "+91 98765 43210"
                    ),
                    SimInfo(
                        subscriptionId = 2,
                        slotIndex = 1,
                        carrierName = "Airtel",
                        displayName = "Airtel Personal",
                        phoneNumber = "+91 91234 56789"
                    )
                ),
                onSelectSim = {},
                onSelectSyncAll = {},
                onDismiss = {}
            )
        }
    }
}

