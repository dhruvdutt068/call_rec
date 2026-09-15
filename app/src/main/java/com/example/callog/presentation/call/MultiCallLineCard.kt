package com.example.callog.presentation.call

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.call.CallSessionState
import com.example.callog.domain.call.CallState
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.theme.*

@Composable
fun MultiCallLineCard(
    activeSession: CallSessionState?,
    heldSessions: List<CallSessionState>,
    onSwapCalls: () -> Unit,
    onMergeCalls: (String, String) -> Unit,
    onEndHeldCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = heldSessions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            heldSessions.forEach { heldSession ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate900.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ContactAvatar(
                            name = heldSession.callerDisplayName,
                            initials = heldSession.initials,
                            photoUri = heldSession.avatarUrl,
                            modifier = Modifier.size(38.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = heldSession.callerDisplayName.ifBlank { heldSession.phoneNumber },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate50,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Amber500.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (heldSession.state == CallState.ON_HOLD) "ON HOLD" else heldSession.state.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Amber500,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            val min = heldSession.durationSeconds / 60
                            val sec = heldSession.durationSeconds % 60
                            val durationText = "%02d:%02d".format(min, sec)

                            Text(
                                text = "${heldSession.phoneNumber} • $durationText",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400,
                                maxLines = 1
                            )
                        }

                        // Multi-Call Control Actions (Swap, Merge, End)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Swap Line Action
                            IconButton(
                                onClick = onSwapCalls,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Teal500.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SyncAlt,
                                    contentDescription = "Swap Calls",
                                    tint = Teal300,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Merge Conference Action
                            if (activeSession != null) {
                                IconButton(
                                    onClick = { onMergeCalls(activeSession.callId, heldSession.callId) },
                                    modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AllSetBlue.copy(alpha = 0.2f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CallMerge,
                                        contentDescription = "Merge Calls",
                                        tint = AllSetBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // End Held Line
                            IconButton(
                                onClick = { onEndHeldCall(heldSession.callId) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Red500.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Held Call",
                                    tint = Red500,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
