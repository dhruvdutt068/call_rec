package com.example.callog.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.callog.core.extensions.toRelativeTimeSpan
import com.example.callog.core.extensions.toDurationString
import com.example.callog.core.extensions.toDateTimeString
import androidx.compose.ui.text.style.TextAlign
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.theme.*

@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                borderWidth.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
    } else {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                borderWidth.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
    }

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

@Composable
fun ContactAvatar(
    name: String?,
    initials: String,
    photoUri: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Teal500, Teal300)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrEmpty()) {
            AsyncImage(
                model = photoUri,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                color = MidnightNavy,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CallTypeIcon(
    callType: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (callType.uppercase()) {
        "INCOMING" -> Icons.Default.CallReceived to Green500
        "OUTGOING" -> Icons.Default.CallMade to Teal300
        "MISSED" -> Icons.Default.CallMissed to Red500
        "REJECTED", "BLOCKED" -> Icons.Default.Block to Amber500
        else -> Icons.Default.Call to Slate400
    }

    Icon(
        imageVector = icon,
        contentDescription = callType,
        tint = color,
        modifier = modifier.size(18.dp)
    )
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = Slate400) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Slate400
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = Slate400
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Slate800,
            unfocusedContainerColor = Slate800,
            focusedBorderColor = Teal500,
            unfocusedBorderColor = Slate700,
            focusedTextColor = Slate50,
            unfocusedTextColor = Slate50
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    GlassyCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate400
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate50
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (tag.lowercase()) {
        "work" -> Teal500.copy(alpha = 0.2f)
        "family", "personal" -> Amber500.copy(alpha = 0.2f)
        "spam" -> Red500.copy(alpha = 0.2f)
        "friend" -> Green500.copy(alpha = 0.2f)
        else -> Slate700.copy(alpha = 0.4f)
    }

    val textColor = when (tag.lowercase()) {
        "work" -> Teal300
        "family", "personal" -> Amber500
        "spam" -> Red500
        "friend" -> Green500
        else -> Slate400
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = tag,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CallCard(
    call: CallLogEntry,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeString = call.timestamp.toRelativeTimeSpan()
    val durationString = call.duration.toDurationString()

    GlassyCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                name = call.name,
                initials = call.initials,
                photoUri = call.contactPhotoUri,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = call.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate50,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (call.recordingPath != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Recorded",
                            tint = Teal300,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CallTypeIcon(call.callType)
                    
                    Text(
                        text = if (call.duration > 0) durationString else "Missed",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                    
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate700
                    )

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
                
                if (call.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        call.tags.take(3).forEach { tag ->
                            TagChip(tag = tag)
                        }
                    }
                }
            }

            val syncIcon = when (call.syncStatus.uppercase()) {
                "SYNCED" -> Icons.Default.CloudDone
                "UPLOADING" -> Icons.Default.CloudUpload
                "FAILED" -> Icons.Default.CloudOff
                else -> Icons.Default.Cloud
            }
            val syncColor = when (call.syncStatus.uppercase()) {
                "SYNCED" -> Teal300
                "UPLOADING" -> Amber500
                "FAILED" -> Red500
                else -> Slate400
            }
            val syncDesc = when (call.syncStatus.uppercase()) {
                "SYNCED" -> "Synced to Firestore"
                "UPLOADING" -> "Syncing..."
                "FAILED" -> "Sync failed"
                else -> "Pending sync"
            }

            Icon(
                imageVector = syncIcon,
                contentDescription = syncDesc,
                tint = syncColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 4.dp)
            )

            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (call.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (call.isFavorite) Amber500 else Slate400
                )
            }
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: ReminderWithCall,
    onCompleteClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateTimeString = reminder.reminder.reminderTime.toDateTimeString()

    GlassyCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Amber500.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Amber500,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Callback: ${reminder.call.name ?: reminder.call.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate50
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scheduled: $dateTimeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
                if (!reminder.reminder.notes.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reminder.reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Teal300,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onCompleteClick) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Mark Complete",
                    tint = Green500
                )
            }

            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Reminder",
                    tint = Red500
                )
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Slate700,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Slate50
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = Slate400,
            textAlign = TextAlign.Center
        )
    }
}
