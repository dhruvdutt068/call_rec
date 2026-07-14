package com.example.callog.presentation.screens.onboarding

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@Composable
fun OnboardingScreen(
    viewModel: CallViewModel,
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var ownerName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        background,
                        secondary.copy(alpha = 0.05f),
                        primary.copy(alpha = 0.1f)
                    )
                )
            )
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().maxWidth(450.dp)
        ) {
            // Header Icon / Branding
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Title section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Device Setup Required",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Slate50,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Configure device details to link call logs and recording syncs to your corporate account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            GlassyCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Owner Name Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Device Owner Name",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            placeholder = { Text("e.g. John Doe") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Slate400)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Phone Number Field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Device Phone Number",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate400,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                // Allow only digits and '+'
                                if (input.all { it.isDigit() || it == '+' }) {
                                    phoneNumber = input
                                }
                            },
                            placeholder = { Text("e.g. +919876543210") },
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    // Explanation Notice
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Carrier security restrictions (like Jio & Airtel) prevent Android from auto-detecting your number. Please type it manually to continue.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Setup complete action button
                    Button(
                        onClick = {
                            val trimmedName = ownerName.trim()
                            val trimmedPhone = phoneNumber.trim()

                            if (trimmedName.isEmpty() || trimmedPhone.isEmpty()) {
                                Toast.makeText(context, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (trimmedPhone.length < 10) {
                                Toast.makeText(context, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            viewModel.saveDeviceOwnerName(trimmedName)
                            viewModel.saveDevicePhoneNumber(trimmedPhone)
                            
                            Toast.makeText(context, "Configuration Saved!", Toast.LENGTH_SHORT).show()
                            onSetupComplete()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Teal500,
                            contentColor = Slate50
                        )
                    ) {
                        Text(
                            text = "Save & Continue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// Utility extension for max width modifier
private fun Modifier.maxWidth(width: androidx.compose.ui.unit.Dp): Modifier =
    this.widthIn(max = width)
