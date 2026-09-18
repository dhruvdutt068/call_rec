package com.example.callog.presentation.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.domain.call.CallDirection
import com.example.callog.domain.call.CallSessionState
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import java.util.Locale

enum class CallOutcome(val label: String) {
    CONNECTED("Connected"),
    CALLBACK_REQUIRED("Callback Required"),
    FOLLOW_UP("Follow-up Required"),
    BUSY("Busy"),
    NO_ANSWER("No Answer"),
    LOST("Not Interested")
}

@Composable
fun getOutcomeColor(outcome: CallOutcome): Color = when (outcome) {
    CallOutcome.CONNECTED -> Green500
    CallOutcome.CALLBACK_REQUIRED -> Amber500
    CallOutcome.FOLLOW_UP -> Teal300
    CallOutcome.BUSY -> Slate400
    CallOutcome.NO_ANSWER -> Red500
    CallOutcome.LOST -> Red500
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostCallSummaryScreen(
    session: CallSessionState,
    onSaveWrapUp: (outcome: CallOutcome, notes: String, isFavorite: Boolean, scheduleFollowUpDays: Int?) -> Unit,
    onOpenPersonDetails: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOutcome by remember {
        mutableStateOf(
            if (session.durationSeconds > 0) CallOutcome.CONNECTED else CallOutcome.NO_ANSWER
        )
    }
    var notesText by remember { mutableStateOf("") }
    var isFavorite by remember { mutableStateOf(false) }
    var scheduleFollowUpDays by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Call Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
                Text(
                    text = "Log disposition & CRM follow-up",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Slate800)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Slate300,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Caller & Metrics Card
        GlassyCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(AllSetBlue.copy(alpha = 0.2f))
                            .border(1.5.dp, AllSetBlue.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = session.initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = AllSetBlue
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.callerDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate50
                        )

                        if (!session.companyName.isNullOrBlank()) {
                            Text(
                                text = session.companyName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }

                        Text(
                            text = session.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate300
                        )
                    }

                    IconButton(
                        onClick = { isFavorite = !isFavorite }
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Amber500 else Slate400
                        )
                    }
                }

                Divider(color = Slate800, thickness = 1.dp)

                // Call metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (session.direction == CallDirection.INCOMING) Icons.Default.CallReceived else Icons.Default.CallMade,
                            contentDescription = null,
                            tint = if (session.direction == CallDirection.INCOMING) Teal300 else AllSetBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (session.direction == CallDirection.INCOMING) "Incoming Call" else "Outgoing Call",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate300
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (session.durationSeconds > 0) formatDuration(session.durationSeconds) else "Missed / No Talk Time",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (session.durationSeconds > 0) Green500 else Slate400
                        )
                    }

                    session.simInfo?.let { sim ->
                        Text(
                            text = "SIM ${sim.slotIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Teal300,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Teal500.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Call Outcome Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Call Outcome",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CallOutcome.values().forEach { outcome ->
                    val isSelected = selectedOutcome == outcome
                    val color = getOutcomeColor(outcome)
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedOutcome = outcome },
                        label = {
                            Text(
                                text = outcome.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color,
                            containerColor = Slate900,
                            labelColor = Slate400
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) color else Slate800
                        )
                    )
                }
            }
        }

        // Notes Input Section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Interaction Notes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                placeholder = {
                    Text("Add client discussion points, objections, next actions...", color = Slate400)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Slate50,
                    unfocusedTextColor = Slate50,
                    focusedBorderColor = AllSetBlue,
                    unfocusedBorderColor = Slate800,
                    focusedContainerColor = Slate900,
                    unfocusedContainerColor = Slate900
                )
            )
        }

        // Follow-Up Scheduling
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Schedule Follow-up Task",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Slate50
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    1 to "Tomorrow",
                    3 to "In 3 Days",
                    7 to "In 1 Week"
                ).forEach { (days, label) ->
                    val isSelected = scheduleFollowUpDays == days
                    OutlinedButton(
                        onClick = {
                            scheduleFollowUpDays = if (isSelected) null else days
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) AllSetBlue.copy(alpha = 0.2f) else Slate900,
                            contentColor = if (isSelected) AllSetBlue else Slate400
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) AllSetBlue else Slate800)
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    onSaveWrapUp(selectedOutcome, notesText.trim(), isFavorite, scheduleFollowUpDays)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AllSetBlue,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save & Done",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            session.personId?.let { personId ->
                OutlinedButton(
                    onClick = { onOpenPersonDetails(personId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Slate900,
                        contentColor = AllSetLavender
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Slate800)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open CRM Profile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip & Dismiss",
                    color = Slate400,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Post Call Summary Screen", showBackground = true)
@Composable
fun PostCallSummaryScreenPreview() {
    CallogTheme {
        PostCallSummaryScreen(
            session = CallSessionState(
                callId = "call_wrapup_01",
                phoneNumber = "+91 98765 43210",
                callerDisplayName = "Sarah Connor",
                companyName = "Cyberdyne Systems",
                direction = CallDirection.INCOMING,
                durationSeconds = 184,
                initials = "SC",
                personId = "person_456",
                simInfo = com.example.callog.sim.SimInfo(
                    subscriptionId = 1,
                    slotIndex = 0,
                    carrierName = "Jio 5G",
                    displayName = "Work SIM",
                    phoneNumber = "+919876543210"
                )
            ),
            onSaveWrapUp = { _, _, _, _ -> },
            onOpenPersonDetails = {},
            onDismiss = {}
        )
    }
}
