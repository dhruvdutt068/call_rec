package com.example.callog.presentation.screens.dialer

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callog.core.telecom.T9MatchResult
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.theme.*
import com.example.callog.sim.SimInfo

@Composable
fun DialerScreen(
    onNavigateToContact: ((String) -> Unit)? = null,
    onMenuClick: (() -> Unit)? = null,
    viewModel: DialerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val inputNumber by viewModel.inputNumber.collectAsState()
    val t9Results by viewModel.t9Results.collectAsState()
    val activeSims by viewModel.activeSims.collectAsState()
    val clipboardSuggestion by viewModel.clipboardSuggestion.collectAsState()
    val isDefaultDialer = remember { com.example.callog.core.telecom.TelecomRoleHelper.isDefaultDialer(context) }

    LaunchedEffect(Unit) {
        viewModel.refreshSims()
        viewModel.checkClipboard()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
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
                    text = "Dialer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDefaultDialer) Green500.copy(alpha = 0.15f) else Amber500.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isDefaultDialer) Green500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.PhoneCallback,
                        contentDescription = null,
                        tint = if (isDefaultDialer) Green500 else Amber500,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isDefaultDialer) "Default Phone" else "System Dialer",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDefaultDialer) Green500 else Amber500
                    )
                }
            }
        }

        // Top Section: T9 Search Results / Suggestions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            // Clipboard Paste Suggestion Chip
            AnimatedVisibility(
                visible = !clipboardSuggestion.isNullOrBlank() && inputNumber.isBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    onClick = { viewModel.onPasteClipboard() },
                    shape = RoundedCornerShape(12.dp),
                    color = Teal500.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Teal500.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Teal300,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Paste from clipboard: ${clipboardSuggestion ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Teal300,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // T9 Matching Contacts List
            AnimatedVisibility(
                visible = t9Results.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(t9Results, key = { it.phoneNumber + (it.personId ?: "") }) { match ->
                        T9ContactItem(
                            match = match,
                            onItemClick = {
                                viewModel.setInputNumber(match.phoneNumber)
                            },
                            onCallClick = { sim ->
                                viewModel.placeCall(match.phoneNumber, sim)
                            },
                            activeSims = activeSims
                        )
                    }
                }
            }
        }

        // Middle Section: Number Display & Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = inputNumber.ifEmpty { "Enter phone number" },
                    style = if (inputNumber.length > 14) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (inputNumber.isNotEmpty()) Slate50 else Slate400,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                if (inputNumber.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.onBackspaceClick() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Slate400,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Keypad Grid
        DialpadGrid(
            onDigitClick = { digit -> viewModel.onDigitClick(digit) },
            onDigitLongClick = { digit ->
                if (digit == '0') {
                    viewModel.onPlusLongClick()
                } else if (digit in '1'..'9') {
                    viewModel.onSpeedDialLongPress(digit.digitToInt())
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Section: Dual-SIM Call Buttons
        DualSimCallButtons(
            inputNumber = inputNumber,
            activeSims = activeSims,
            onCallClick = { sim ->
                viewModel.placeCall(inputNumber, sim)
            }
        )
    }
}

@Composable
private fun T9ContactItem(
    match: T9MatchResult,
    onItemClick: () -> Unit,
    onCallClick: (SimInfo?) -> Unit,
    activeSims: List<SimInfo>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate900.copy(alpha = 0.9f))
            .border(1.dp, Slate800, RoundedCornerShape(12.dp))
            .clickable(onClick = onItemClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ContactAvatar(
            name = match.displayName,
            initials = match.displayName.take(2).uppercase(),
            photoUri = null,
            modifier = Modifier.size(40.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Name with T9 highlighted range
                val annotatedName = buildAnnotatedString {
                    val name = match.displayName
                    val range = match.matchedNameRange
                    if (range != null && range.first in name.indices && range.last < name.length) {
                        append(name.substring(0, range.first))
                        withStyle(SpanStyle(color = Teal300, fontWeight = FontWeight.Bold)) {
                            append(name.substring(range.first, range.last + 1))
                        }
                        append(name.substring(range.last + 1))
                    } else {
                        append(name)
                    }
                }

                Text(
                    text = annotatedName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate50,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (match.crmStatus != LeadStatus.UNKNOWN) {
                    val statusColor = when (match.crmStatus) {
                        LeadStatus.HOT -> Red500
                        LeadStatus.WARM -> Amber500
                        LeadStatus.COLD -> AllSetBlue
                        else -> Slate400
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = match.crmStatus.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Phone Number & Company
            val subText = buildString {
                match.companyName?.takeIf { it.isNotBlank() }?.let { append("$it • ") }
                append(match.phoneNumber)
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick Call Button
        IconButton(
            onClick = { onCallClick(activeSims.firstOrNull()) },
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Green500.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call",
                tint = Green500,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DialpadGrid(
    onDigitClick: (Char) -> Unit,
    onDigitLongClick: (Char) -> Unit
) {
    val keypadLayout = listOf(
        listOf('1' to "", '2' to "ABC", '3' to "DEF"),
        listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
        listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
        listOf('*' to "", '0' to "+", '#' to "")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keypadLayout.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { (digit, letters) ->
                    DialpadButton(
                        digit = digit,
                        letters = letters,
                        onClick = { onDigitClick(digit) },
                        onLongClick = { onDigitLongClick(digit) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadButton(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Slate900)
            .border(1.dp, Slate800, CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate50,
                fontSize = 24.sp
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun DualSimCallButtons(
    inputNumber: String,
    activeSims: List<SimInfo>,
    onCallClick: (SimInfo?) -> Unit
) {
    val isEnabled = inputNumber.isNotBlank()

    if (activeSims.size >= 2) {
        // Dual SIM Layout: Side-by-side call buttons with carrier indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sim1 = activeSims[0]
            val sim2 = activeSims[1]

            Button(
                onClick = { onCallClick(sim1) },
                enabled = isEnabled,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call SIM 1",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SIM 1",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Slate400
                        )
                        Text(
                            text = sim1.carrierName.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isEnabled) Slate300 else Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Button(
                onClick = { onCallClick(sim2) },
                enabled = isEnabled,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Teal500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call SIM 2",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "SIM 2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEnabled) Color.White else Slate400
                        )
                        Text(
                            text = sim2.carrierName.take(8),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isEnabled) Slate300 else Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // Single SIM Layout
        val singleSim = activeSims.firstOrNull()
        val carrierLabel = singleSim?.let { " (${it.carrierName})" } ?: ""

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { onCallClick(singleSim) },
                enabled = isEnabled,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Green500,
                    disabledContainerColor = Slate800
                ),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(58.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call",
                        tint = if (isEnabled) Color.White else Slate400,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Call$carrierLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color.White else Slate400
                    )
                }
            }
        }
    }
}
