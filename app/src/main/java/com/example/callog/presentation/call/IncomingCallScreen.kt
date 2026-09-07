package com.example.callog.presentation.call

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.call.CallAction
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*

@Composable
fun IncomingCallScreen(
    session: CallSessionState,
    onAction: (CallAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // Subtle pulsating animation around caller avatar during incoming ringing
    val infiniteTransition = rememberInfiniteTransition(label = "ring_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

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
            .padding(horizontal = 24.dp, vertical = 40.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Ringing Label
            Text(
                text = "INCOMING CALL",
                style = MaterialTheme.typography.labelMedium,
                color = AllSetLavender,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Animated Pulsing Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(AllSetLavender.copy(alpha = 0.2f))
                )
                ContactAvatar(
                    name = session.callerDisplayName,
                    initials = session.initials,
                    photoUri = session.avatarUrl,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Canonical Caller Name (EB Garamond title)
            Text(
                text = session.callerDisplayName,
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

            // Phone Number
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = session.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Badges Row: CRM Status, Priority, SIM
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CRM Status Badge
                if (session.crmStatus != LeadStatus.UNKNOWN) {
                    CrmStatusChip(status = session.crmStatus)
                }

                // CRM Priority Badge
                if (session.priority == LeadPriority.URGENT || session.priority == LeadPriority.HIGH) {
                    CrmPriorityChip(priority = session.priority)
                }

                // SIM slot badge
                session.simInfo?.let { sim ->
                    SimBadge(sim = sim)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // CRM Context Card (Recent interactions & pending follow-up)
            if (session.recentInteractionSummary != null || session.pendingFollowUp != null) {
                GlassyCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CRM Context",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (session.recentInteractionSummary != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = session.recentInteractionSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (session.pendingFollowUp != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = null,
                                tint = AllSetAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = session.pendingFollowUp,
                                style = MaterialTheme.typography.bodySmall,
                                color = AllSetAmber,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Buttons: Reject (Red) & Answer (Green)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reject Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onAction(CallAction.Reject(session.callId)) },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Red500)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Answer Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { onAction(CallAction.Answer(session.callId)) },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Green500)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Answer Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Answer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CrmStatusChip(status: LeadStatus) {
    val (bg, text) = when (status) {
        LeadStatus.HOT -> Color(0xFFFFEBEB) to Color(0xFFD32F2F)
        LeadStatus.WARM -> Color(0xFFFFF7E6) to Color(0xFFD48806)
        LeadStatus.COLD -> Color(0xFFF0F5FF) to Color(0xFF1D39C4)
        else -> Color(0xFFF0F0F0) to Color(0xFF666666)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = text
        )
    }
}

@Composable
fun CrmPriorityChip(priority: LeadPriority) {
    val (bg, text) = when (priority) {
        LeadPriority.URGENT -> Color(0xFF3D1028) to Color(0xFFFF85C0)
        LeadPriority.HIGH -> Color(0xFFFFF0F6) to Color(0xFFC41D7F)
        else -> Color(0xFFE8EAF2) to Color(0xFF4A4FD8)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = priority.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = text
        )
    }
}

@Composable
fun SimBadge(sim: com.example.callog.sim.SimInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.SimCard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "SIM ${sim.slotIndex + 1} (${sim.carrierName})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
