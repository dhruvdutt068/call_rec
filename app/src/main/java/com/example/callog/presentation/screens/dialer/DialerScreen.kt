package com.example.callog.presentation.screens.dialer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.core.utils.PhoneNumberNormalizer
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.domain.model.LeadPriority
import com.example.callog.domain.model.LeadStatus
import com.example.callog.presentation.components.ContactAvatar
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@Composable
fun DialerScreen(
    viewModel: CallViewModel,
    onCallClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var inputNumber by remember { mutableStateOf("") }
    val callLogs by viewModel.callLogs.collectAsState()
    val activeSims by viewModel.activeSims.collectAsState()
    var selectedSimSlot by remember { mutableStateOf(0) }

    // Matching suggestions from recent call logs and contacts
    val matchingLogs = remember(inputNumber, callLogs) {
        if (inputNumber.isBlank()) {
            emptyList()
        } else {
            val normQuery = PhoneNumberNormalizer.normalize(inputNumber)
            callLogs.filter { log ->
                val normLog = PhoneNumberNormalizer.normalize(log.number)
                normLog.contains(normQuery) || 
                (log.name?.contains(inputNumber, ignoreCase = true) == true) ||
                log.number.contains(inputNumber)
            }.distinctBy { it.number }.take(4)
        }
    }

    val selectedSim = activeSims.getOrNull(selectedSimSlot)

    fun makeCall(numberToCall: String) {
        if (numberToCall.isBlank()) return
        viewModel.initiateCall(context, numberToCall.trim(), selectedSimSlot)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Suggestions / Matching Contacts List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            AnimatedVisibility(visible = matchingLogs.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(matchingLogs) { log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate900.copy(alpha = 0.8f))
                                .clickable {
                                    inputNumber = log.number
                                    makeCall(log.number)
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ContactAvatar(
                                name = log.displayName,
                                initials = log.initials,
                                photoUri = log.contactPhotoUri,
                                modifier = Modifier.size(36.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate50,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = log.number,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate400
                                )
                            }
                            IconButton(
                                onClick = { makeCall(log.number) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = Green500,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Dialed Number Display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = inputNumber.ifEmpty { "Enter phone number" },
                    style = if (inputNumber.length > 12) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (inputNumber.isNotEmpty()) Slate50 else Slate400,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                if (inputNumber.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            if (inputNumber.isNotEmpty()) {
                                inputNumber = inputNumber.dropLast(1)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            tint = Slate400,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // SIM Card Selector Row (if multi-SIM available)
            if (activeSims.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    activeSims.forEachIndexed { index, sim ->
                        val isSelected = selectedSimSlot == index
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSimSlot = index },
                            label = {
                                Text(
                                    text = "SIM ${index + 1} (${sim.carrierName})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Teal500.copy(alpha = 0.2f),
                                selectedLabelColor = Teal300,
                                containerColor = Slate900,
                                labelColor = Slate400
                            )
                        )
                    }
                }
            }
        }

        // 12-Key Dialpad
        DialpadGrid(
            onDigitClick = { digit ->
                inputNumber += digit
            },
            onPlusLongClick = {
                inputNumber += "+"
            }
        )

        // Bottom Action Bar: Call Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { makeCall(inputNumber) },
                enabled = inputNumber.isNotBlank(),
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(if (inputNumber.isNotBlank()) Green500 else Slate800)
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Start Call",
                    tint = if (inputNumber.isNotBlank()) Color.White else Slate400,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun DialpadGrid(
    onDigitClick: (Char) -> Unit,
    onPlusLongClick: () -> Unit
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
                        onLongClick = if (digit == '0') onPlusLongClick else null
                    )
                }
            }
        }
    }
}

@Composable
private fun DialpadButton(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(Slate900)
            .border(1.dp, Slate800, CircleShape)
            .clickable(onClick = onClick),
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
                color = Slate50
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate400,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
