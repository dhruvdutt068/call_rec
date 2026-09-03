package com.example.callog.presentation.screens.contacts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.components.EmptyStateView
import com.example.callog.presentation.components.GlassyCard
import com.example.callog.presentation.theme.*
import com.example.callog.presentation.viewmodel.CallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppScreen(
    contactId: String,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsState()
    val contact = remember(contacts, contactId) { contacts.find { it.contactId == contactId } }
    var messageText by remember { mutableStateOf("Hi ${contact?.name ?: ""}, following up from AllSet CRM.") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp • ${contact?.name ?: contactId}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassyCard {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("Client Contact", style = MaterialTheme.typography.labelSmall, color = Slate400)
                    Text(contact?.name ?: "Unknown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Slate50)
                    Text(contact?.phoneNumbers?.firstOrNull() ?: "No phone", color = Teal300)
                }
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message to Send via WhatsApp") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    val phone = contact?.phoneNumbers?.firstOrNull()?.replace("[^0-9+]".toRegex(), "") ?: ""
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(messageText)}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green500, contentColor = Slate900)
            ) {
                Icon(Icons.Default.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in WhatsApp", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditContactScreen(
    contactId: String,
    viewModel: CallViewModel,
    onBackClick: () -> Unit,
    onSaveSuccess: (updatedName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by viewModel.contacts.collectAsState()
    val contact = remember(contacts, contactId) { contacts.find { it.contactId == contactId } }
    var name by remember { mutableStateOf(contact?.name ?: "") }
    var email by remember { mutableStateOf(contact?.emails?.firstOrNull() ?: "") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Client Details", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Editing Canonical Contact ID: $contactId", color = Teal300, style = MaterialTheme.typography.labelMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Client Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onSaveSuccess(name.trim())
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedbackScreen(
    contactId: String,
    onBackClick: () -> Unit,
    onFeedbackSubmitted: (rating: Int, notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rating by remember { mutableStateOf(5) }
    var notes by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Client Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Client Satisfaction Rating", style = MaterialTheme.typography.titleMedium, color = Slate50)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(onClick = { rating = star }) {
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star Stars",
                            tint = Amber500
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Feedback Notes / Key Highlights") },
                placeholder = { Text("e.g. Expressed high interest in enterprise plan...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onFeedbackSubmitted(rating, notes.trim()) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Teal300, contentColor = Slate900)
            ) {
                Text("Submit Feedback", fontWeight = FontWeight.Bold)
            }
        }
    }
}
