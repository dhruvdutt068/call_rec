package com.example.callog.presentation.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.presentation.call.InCallActivity
import com.example.callog.presentation.theme.*

/**
 * Floating / Top-level Persistent Ongoing Call Banner for the AllSet App.
 * Supports single and multi-call sessions (Active vs Held lines).
 * Provides live duration, caller context, line swapping, quick return to InCallActivity, and one-tap disconnect.
 */
@Composable
fun OngoingCallBanner(
    session: CallSessionState?,
    otherSessions: List<CallSessionState> = emptyList(),
    onSwapCalls: (() -> Unit)? = null,
    onDisconnect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = session != null && !session.isTerminated
    val context = LocalContext.current

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        if (session == null) return@AnimatedVisibility

        val durationFormatted = remember(session.durationSeconds) {
            val minutes = session.durationSeconds / 60
            val seconds = session.durationSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        }

        val statusText = when (session.state) {
            CallState.ACTIVE -> "Ongoing Call • $durationFormatted"
            CallState.ON_HOLD -> "Call On Hold"
            CallState.CONNECTING -> "Connecting..."
            CallState.RINGING -> "Incoming Call..."
            else -> durationFormatted
        }

        // Pulse animation for active call indicator
        val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )

        val displayName = session.callerDisplayName.ifBlank {
            session.phoneNumber.ifBlank { "Active Call" }
        }

        val surfaceColor = if (isDarkTheme) Color(0xFF1E2028) else Color(0xFFFFFFFF)
        val textColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
        val subTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)

        val heldSession = otherSessions.firstOrNull { it.callId != session.callId && !it.isTerminated }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (heldSession != null) AllSetAmber.copy(alpha = 0.8f) else AllSetTeal.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    try {
                        val inCallIntent = Intent(context, InCallActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            putExtra("call_id", session.callId)
                        }
                        context.startActivity(inCallIntent)
                    } catch (e: Exception) {
                        android.util.Log.e("OngoingCallBanner", "Failed to return to InCallActivity", e)
                    }
                },
            color = surfaceColor,
            shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Pulsing Green/Amber Phone Icon
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .scale(pulseScale)
                                .background(
                                    (if (session.isOnHold) AllSetAmber else AllSetTeal).copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .background(if (session.isOnHold) AllSetAmber else AllSetTeal, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = "Active Call",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Call Information
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!session.companyName.isNullOrBlank()) {
                                Text(
                                    text = "(${session.companyName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AllSetBlue,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (session.isOnHold) AllSetAmber else AllSetTeal
                            )
                            Text(
                                text = "• Tap to return to call",
                                style = MaterialTheme.typography.labelSmall,
                                color = subTextColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Return Icon Button
                    IconButton(
                        onClick = {
                            try {
                                val inCallIntent = Intent(context, InCallActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    putExtra("call_id", session.callId)
                                }
                                context.startActivity(inCallIntent)
                            } catch (e: Exception) {
                                android.util.Log.e("OngoingCallBanner", "Failed to return to InCallActivity", e)
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (isDarkTheme) Color(0xFF2D3139) else Color(0xFFF1F5F9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Return to Call",
                            tint = AllSetBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Quick Hangup Button
                    IconButton(
                        onClick = { onDisconnect(session.callId) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Red500, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Secondary / Held Call Line Strip (if multi-call active)
                if (heldSession != null) {
                    HorizontalDivider(
                        color = if (isDarkTheme) Color(0xFF2D3139) else Color(0xFFE2E8F0),
                        thickness = 1.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AllSetAmber.copy(alpha = 0.08f))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AllSetAmber.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "HELD",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AllSetAmber,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = heldSession.callerDisplayName.ifBlank { heldSession.phoneNumber },
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (onSwapCalls != null) {
                            TextButton(
                                onClick = onSwapCalls,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapCalls,
                                    contentDescription = "Swap Calls",
                                    tint = AllSetBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Swap",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = AllSetBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
