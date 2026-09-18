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
import com.example.callog.presentation.components.ExpressiveEmptyState
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
    val phoneNumber = contact?.phoneNumbers?.firstOrNull() ?: ""

    // Collect Room Conversation & Messages Flow
    val conversation by viewModel.getConversationForPersonFlow(contactId).collectAsState(initial = null)
    val messages by if (conversation != null) {
        viewModel.getMessagesForConversationFlow(conversation!!.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }

    // Initialize conversation if not present
    LaunchedEffect(contactId, phoneNumber) {
        if (conversation == null && contactId.isNotBlank()) {
            viewModel.initializeConversation(contactId, phoneNumber.ifBlank { "Unknown" })
        }
    }

    var messageText by remember { mutableStateOf("") }
    var showHandoverDialog by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf(com.example.callog.domain.model.HandoverReason.MANUAL) }
    var repName by remember { mutableStateOf("Sales Rep") }

    val isAiHandling = conversation?.status == com.example.callog.domain.model.ConversationStatus.AI_HANDLING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "WhatsApp • ${contact?.name ?: contactId}",
                            style = CallogTypography.sectionTitle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isAiHandling) Icons.Default.SmartToy else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isAiHandling) CallogSemanticColors.LeadColors.Won else AllSetAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (isAiHandling) "AI Active • Auto-Replying" else "Human Handling • ${conversation?.assignedUserName ?: "Assigned"}",
                                style = CallogTypography.statusLabel,
                                color = if (isAiHandling) CallogSemanticColors.LeadColors.Won else AllSetAmber
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val cleanPhone = phoneNumber.replace("[^0-9+]".toRegex(), "")
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone")
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "External WhatsApp",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live Status & Handover Control Card
            GlassyCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAiHandling) CallogSemanticColors.LeadColors.Won.copy(alpha = 0.15f)
                                        else AllSetAmber.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAiHandling) Icons.Default.SmartToy else Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isAiHandling) CallogSemanticColors.LeadColors.Won else AllSetAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = if (isAiHandling) "AI Agent Active" else "Human Handling",
                                style = CallogTypography.entityName,
                                color = if (isAiHandling) CallogSemanticColors.LeadColors.Won else AllSetAmber
                            )
                        }

                        if (isAiHandling) {
                            Button(
                                onClick = { showHandoverDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AllSetAmber,
                                    contentColor = Slate900
                                ),
                                shape = CallogShapes.buttonShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Handover to Rep", style = CallogTypography.statusLabel)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showHandoverDialog = true },
                                    shape = CallogShapes.buttonShape,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Reassign", style = CallogTypography.statusLabel)
                                }
                                Button(
                                    onClick = { conversation?.id?.let { viewModel.releaseConversationToAi(it) } },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = CallogShapes.buttonShape,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Return to AI", style = CallogTypography.statusLabel)
                                }
                            }
                        }
                    }

                    Text(
                        text = if (isAiHandling) {
                            "Customer queries are automatically handled by the AllSet AI assistant. Take over if custom negotiation or escalation is required."
                        } else {
                            "Assigned to ${conversation?.assignedUserName ?: "Sales Rep"}. Reason: ${conversation?.handoverReason?.name ?: "Manual"}. Automated AI replies are paused."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Message Timeline Feed
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                reverseLayout = false
            ) {
                if (messages.isEmpty()) {
                    item {
                        ExpressiveEmptyState(
                            title = "No Messages Yet",
                            description = "Start the conversation or wait for incoming WhatsApp messages.",
                            icon = Icons.Default.ChatBubbleOutline,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                        )
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(msg = msg)
                    }
                }
            }

            // In-App Compose & Send Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Reply as Sales Rep...") },
                    modifier = Modifier.weight(1f),
                    shape = CallogShapes.inputShape,
                    maxLines = 3,
                    singleLine = false
                )

                IconButton(
                    onClick = {
                        conversation?.id?.let { convId ->
                            if (messageText.isNotBlank()) {
                                viewModel.sendHumanWhatsAppMessage(convId, messageText, repName)
                                messageText = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Reply",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    // Escalation / Handover Dialog
    if (showHandoverDialog) {
        AlertDialog(
            onDismissRequest = { showHandoverDialog = false },
            title = {
                Text(
                    text = "Escalate & Handover Conversation",
                    style = CallogTypography.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Select reason for escalating conversation to a human sales rep:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    com.example.callog.domain.model.HandoverReason.entries.forEach { reason ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reason.name.replace("_", " "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    OutlinedTextField(
                        value = repName,
                        onValueChange = { repName = it },
                        label = { Text("Assignee Name") },
                        singleLine = true,
                        shape = CallogShapes.inputShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        conversation?.id?.let { convId ->
                            viewModel.handoverConversationToHuman(
                                conversationId = convId,
                                reason = selectedReason,
                                assignedUserId = "USER_" + repName.replace(" ", "_").uppercase(),
                                assignedUserName = repName
                            )
                        }
                        showHandoverDialog = false
                    },
                    shape = CallogShapes.buttonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AllSetAmber,
                        contentColor = Slate900
                    )
                ) {
                    Text("Confirm Handover", style = CallogTypography.sectionTitle)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showHandoverDialog = false },
                    shape = CallogShapes.buttonShape
                ) {
                    Text("Cancel", style = CallogTypography.statusLabel)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = CallogShapes.modalShape
        )
    }
}

@Composable
private fun MessageBubble(msg: com.example.callog.domain.model.ConversationMessage) {
    val isCustomer = msg.senderType == com.example.callog.domain.model.SenderType.CUSTOMER
    val isSystem = msg.senderType == com.example.callog.domain.model.SenderType.SYSTEM
    val isAi = msg.senderType == com.example.callog.domain.model.SenderType.AI
    val isHuman = msg.senderType == com.example.callog.domain.model.SenderType.HUMAN

    if (isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                shape = CallogShapes.pillShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Text(
                    text = msg.messageText,
                    style = CallogTypography.statusLabel,
                    color = AllSetAmber,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isCustomer) Alignment.Start else Alignment.End
        ) {
            // Sender Badge with Vector Icon instead of Emoji
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (isAi) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                } else if (isHuman) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Human Rep",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = msg.senderName,
                    style = CallogTypography.statusLabel,
                    color = when {
                        isAi -> MaterialTheme.colorScheme.primary
                        isHuman -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            // Message Bubble Surface
            Surface(
                color = when {
                    isCustomer -> MaterialTheme.colorScheme.surfaceContainerHigh
                    isAi -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                },
                shape = CallogShapes.cardShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    when {
                        isCustomer -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        isAi -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = msg.messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
                title = {
                    Text(
                        text = "Edit Client Details",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            Text(
                text = "Editing Canonical Contact ID: $contactId",
                color = MaterialTheme.colorScheme.primary,
                style = CallogTypography.statusLabel
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Client Name") },
                shape = CallogShapes.inputShape,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                shape = CallogShapes.inputShape,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    onSaveSuccess(name.trim())
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CallogShapes.buttonShape,
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Save Changes", style = CallogTypography.sectionTitle)
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
                title = {
                    Text(
                        text = "Add Client Feedback",
                        style = CallogTypography.sectionTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            Text(
                text = "Client Satisfaction Rating",
                style = CallogTypography.heroTitle,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = { rating = star },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star Stars",
                            tint = AllSetAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Feedback Notes / Key Highlights") },
                placeholder = { Text("e.g. Expressed high interest in enterprise plan...") },
                shape = CallogShapes.inputShape,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { onFeedbackSubmitted(rating, notes.trim()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CallogShapes.buttonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Submit Feedback", style = CallogTypography.sectionTitle)
            }
        }
    }
}

