package com.example.callog.presentation.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallScreen(
    session: CallSessionState,
    otherSessions: List<CallSessionState> = emptyList(),
    onAction: (CallAction) -> Unit,
    onOpenPersonDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showKeypadSheet by remember { mutableStateOf(false) }
    var showAudioRouteSheet by remember { mutableStateOf(false) }

    val formattedDuration = remember(session.durationSeconds) {
        val minutes = session.durationSeconds / 60
        val seconds = session.durationSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    val stateLabel = when (session.state) {
        CallState.ACTIVE -> formattedDuration
        CallState.ON_HOLD -> "On Hold"
        CallState.CONNECTING -> "Connecting..."
        CallState.DISCONNECTING, CallState.DISCONNECTED -> "Call Ended"
        else -> formattedDuration
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 36.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // State / Duration display
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (session.isOnHold) AllSetAmber else MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Avatar
            ContactAvatar(
                name = session.callerDisplayName,
                initials = session.initials,
                photoUri = session.avatarUrl,
                modifier = Modifier.size(96.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            val displayName = session.callerDisplayName.ifBlank {
                session.phoneNumber.ifBlank { "Active Call" }
            }

            // Caller Name
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Company Name
            if (!session.companyName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.companyName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            // Phone Number (if different from displayName)
            if (session.phoneNumber.isNotBlank() && session.phoneNumber != displayName) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = session.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (session.crmStatus != LeadStatus.UNKNOWN) {
                    CrmStatusChip(status = session.crmStatus)
                }
                if (session.priority == LeadPriority.URGENT || session.priority == LeadPriority.HIGH) {
                    CrmPriorityChip(priority = session.priority)
                }
                session.simInfo?.let { sim ->
                    SimBadge(sim = sim)
                }
            }

            // "Open in CRM" button if canonical person exists
            if (session.personId != null) {
                Spacer(modifier = Modifier.height(16.dp))
                FilledTonalButton(
                    onClick = { onOpenPersonDetails(session.personId) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "View CRM Profile",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Call Control Grid at Bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Multi-Call Active / Held Line Card
            if (otherSessions.isNotEmpty()) {
                MultiCallLineCard(
                    activeSession = session,
                    heldSessions = otherSessions,
                    onSwapCalls = { onAction(CallAction.SwapCalls) },
                    onMergeCalls = { c1, c2 -> onAction(CallAction.MergeCalls(c1, c2)) },
                    onEndHeldCall = { callId -> onAction(CallAction.Disconnect(callId)) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Primary In-Call Controls Row 1
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Button
                CallControlButton(
                    icon = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (session.isMuted) "Unmute" else "Mute",
                    isActive = session.isMuted,
                    onClick = { onAction(CallAction.ToggleMute(session.callId)) }
                )

                // Keypad Button
                CallControlButton(
                    icon = Icons.Default.Dialpad,
                    label = "Keypad",
                    isActive = showKeypadSheet,
                    onClick = { showKeypadSheet = true }
                )

                // Audio Route / Speaker Button
                CallControlButton(
                    icon = if (session.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                    label = if (session.isSpeakerOn) "Speaker" else "Audio",
                    isActive = session.isSpeakerOn,
                    onClick = { showAudioRouteSheet = true }
                )

                // Hold Button: ONLY shown if Telecom capability supports hold
                if (session.capabilities.canHold) {
                    CallControlButton(
                        icon = if (session.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                        label = if (session.isOnHold) "Unhold" else "Hold",
                        isActive = session.isOnHold,
                        onClick = { onAction(CallAction.ToggleHold(session.callId)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // End Call Button (Large Red Circle)
            IconButton(
                onClick = { onAction(CallAction.Disconnect(session.callId)) },
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Red500)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // DTMF Keypad Bottom Sheet
        if (showKeypadSheet) {
            ModalBottomSheet(
                onDismissRequest = { showKeypadSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                DtmfKeypadView(
                    onDigitClick = { digit ->
                        onAction(CallAction.SendDtmf(session.callId, digit))
                    },
                    onClose = { showKeypadSheet = false }
                )
            }
        }

        // Audio Route Selector Bottom Sheet
        if (showAudioRouteSheet) {
            AudioRouteBottomSheet(
                currentRoute = if (session.isSpeakerOn) com.example.callog.domain.call.AudioRoute.SPEAKER else com.example.callog.domain.call.AudioRoute.EARPIECE,
                supportedRoutes = listOf(
                    com.example.callog.domain.call.AudioRoute.EARPIECE,
                    com.example.callog.domain.call.AudioRoute.SPEAKER,
                    com.example.callog.domain.call.AudioRoute.BLUETOOTH,
                    com.example.callog.domain.call.AudioRoute.WIRED_HEADSET
                ),
                onSelectRoute = { selectedRoute ->
                    onAction(CallAction.SetAudioRoute(selectedRoute))
                },
                onDismiss = { showAudioRouteSheet = false }
            )
        }
    }
}

@Composable
fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val bgColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        val iconColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DtmfKeypadView(
    onDigitClick: (Char) -> Unit,
    onClose: () -> Unit
) {
    var typedDigits by remember { mutableStateOf("") }

    val digits = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('*', '0', '#')
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Keypad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Keypad")
            }
        }

        // Live Typed Digits Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (typedDigits.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = typedDigits,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { typedDigits = "" },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = "Touch digits to send DTMF tone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        digits.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                typedDigits += digit
                                onDigitClick(digit)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Active Call Screen Light", showBackground = true)
@Composable
fun ActiveCallScreenPreview() {
    CallogTheme {
        ActiveCallScreen(
            session = CallSessionState(
                callId = "call_101",
                phoneNumber = "+91 98765 43210",
                callerDisplayName = "Sarah Connor",
                companyName = "Cyberdyne Systems",
                crmStatus = LeadStatus.HOT,
                priority = LeadPriority.URGENT,
                initials = "SC",
                state = CallState.ACTIVE,
                durationSeconds = 145,
                personId = "person_123",
                capabilities = com.example.callog.domain.call.CallCapabilities(canHold = true, canMute = true),
                simInfo = com.example.callog.sim.SimInfo(
                    subscriptionId = 1,
                    slotIndex = 0,
                    carrierName = "Jio 5G",
                    displayName = "Work SIM",
                    phoneNumber = "+919876543210"
                )
            ),
            otherSessions = emptyList(),
            onAction = {},
            onOpenPersonDetails = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Active Call Screen Dark", showBackground = true)
@Composable
fun ActiveCallScreenDarkPreview() {
    CallogTheme(darkTheme = true) {
        ActiveCallScreen(
            session = CallSessionState(
                callId = "call_102",
                phoneNumber = "+91 91234 56789",
                callerDisplayName = "John Matrix",
                companyName = "Commando Logistics",
                crmStatus = LeadStatus.WARM,
                priority = LeadPriority.HIGH,
                initials = "JM",
                state = CallState.ACTIVE,
                durationSeconds = 85,
                isMuted = true,
                isSpeakerOn = true
            ),
            otherSessions = emptyList(),
            onAction = {},
            onOpenPersonDetails = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "DTMF Keypad Preview", showBackground = true)
@Composable
fun DtmfKeypadPreview() {
    CallogTheme {
        Surface {
            DtmfKeypadView(
                onDigitClick = {},
                onClose = {}
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Call Control Button", showBackground = true)
@Composable
fun CallControlButtonPreview() {
    CallogTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CallControlButton(
                icon = Icons.Default.Mic,
                label = "Mute",
                isActive = false,
                onClick = {}
            )
            CallControlButton(
                icon = Icons.Default.MicOff,
                label = "Unmute",
                isActive = true,
                onClick = {}
            )
        }
    }
}
