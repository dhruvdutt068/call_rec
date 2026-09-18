package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.callog.core.extensions.toDateTimeString
import com.example.callog.core.extensions.toDurationString
import com.example.callog.core.extensions.toRelativeTimeSpan
import com.example.callog.data.local.dao.ReminderWithCall
import com.example.callog.data.local.entity.CallEntity
import com.example.callog.data.local.entity.ReminderEntity
import com.example.callog.domain.model.CallLogEntry
import com.example.callog.presentation.theme.*

/**
 * Material 3 Expressive Card with subtle tonal surface elevation and delicate outline.
 */
@Composable
fun GlassyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderWidth: Float = 1f,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(CallogShapes.card)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                borderWidth.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                CallogShapes.card
            )
    } else {
        modifier
            .clip(CallogShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                borderWidth.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                CallogShapes.card
            )
    }

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

/**
 * Contact Avatar with gradient fallback and photo loading.
 */
@Composable
fun ContactAvatar(
    name: String?,
    initials: String,
    photoUri: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CallogShapes.avatar)
            .background(
                Brush.linearGradient(
                    colors = listOf(AllSetBlue, AllSetLavender)
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
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Call Direction & Lifecycle Icon using CallogSemanticColors.
 */
@Composable
fun CallTypeIcon(
    callType: String,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (callType.uppercase()) {
        "INCOMING" -> Icons.Default.CallReceived to CallogSemanticColors.CallIncoming
        "OUTGOING" -> Icons.Default.CallMade to CallogSemanticColors.CallOutgoing
        "MISSED" -> Icons.Default.CallMissed to CallogSemanticColors.CallMissed
        "REJECTED", "BLOCKED" -> Icons.Default.Block to CallogSemanticColors.CallRejected
        else -> Icons.Default.Call to Slate400
    }

    Icon(
        imageVector = icon,
        contentDescription = "Call Type: $callType",
        tint = color,
        modifier = modifier.size(18.dp)
    )
}

/**
 * Search Bar Input with Material 3 styling and quick-clear action.
 */
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
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = CallogShapes.interactive,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Expressive KPI Stat Card featuring displayMetric typography.
 */
@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(CallogShapes.container)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CallogShapes.container),
        shape = CallogShapes.container,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = CallogTypography.displayMetric,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CallogShapes.interactive)
                    .background(iconColor.copy(alpha = 0.14f)),
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

/**
 * CRM Category Tag Chip.
 */
@Composable
fun TagChip(
    tag: String = "",
    text: String = tag,
    modifier: Modifier = Modifier,
    isRemovable: Boolean = false,
    onRemove: (() -> Unit)? = null
) {
    val displayTag = if (tag.isNotEmpty()) tag else text
    val backgroundColor = when (displayTag.lowercase()) {
        "work" -> AllSetBlue.copy(alpha = 0.15f)
        "family", "personal" -> AllSetAmber.copy(alpha = 0.15f)
        "spam" -> Red500.copy(alpha = 0.15f)
        "friend" -> AllSetTeal.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (displayTag.lowercase()) {
        "work" -> AllSetBlue
        "family", "personal" -> AllSetAmber
        "spam" -> Red500
        "friend" -> AllSetTeal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier.clip(CallogShapes.subtle),
        shape = CallogShapes.subtle,
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = displayTag,
                style = CallogTypography.statusLabel,
                color = textColor
            )
            if (isRemovable && onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove tag",
                        tint = textColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

/**
 * Call Card with Spring-animated favorite toggle and semantic status indicators.
 */
@Composable
fun CallCard(
    call: CallLogEntry,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeString = call.timestamp.toRelativeTimeSpan()
    val durationString = call.duration.toDurationString()

    // Tactile spring scale for favorite star
    val starScale by animateFloatAsState(
        targetValue = if (call.isFavorite) 1.2f else 1.0f,
        animationSpec = CallogMotion.bouncySpring(),
        label = "starScale"
    )

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
                        style = CallogTypography.entityName,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (call.recordingPath != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Has Recording",
                            tint = AllSetTeal,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CallTypeIcon(call.callType)

                    val statusText = when {
                        call.duration > 0 -> durationString
                        call.callType.equals("MISSED", ignoreCase = true) -> "Missed"
                        else -> "Not Connected"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (call.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
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

            val (syncIcon, syncColor, syncDesc) = when (call.syncStatus.uppercase()) {
                "SYNCED" -> Triple(Icons.Default.CloudDone, CallogSemanticColors.SyncSuccess, "Synced to Cloud")
                "UPLOADING" -> Triple(Icons.Default.CloudUpload, CallogSemanticColors.SyncInProgress, "Syncing...")
                "FAILED" -> Triple(Icons.Default.CloudOff, CallogSemanticColors.SyncFailed, "Sync failed")
                else -> Triple(Icons.Default.Cloud, CallogSemanticColors.SyncPending, "Pending sync")
            }

            Icon(
                imageVector = syncIcon,
                contentDescription = syncDesc,
                tint = syncColor,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 4.dp)
            )

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (call.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (call.isFavorite) "Remove Favorite" else "Add Favorite",
                    tint = if (call.isFavorite) AllSetAmber else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.scale(starScale)
                )
            }
        }
    }
}

/**
 * Reminder Card with tactile action buttons.
 */
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
                    .clip(CallogShapes.avatar)
                    .background(AllSetAmber.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = AllSetAmber,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Callback: ${reminder.call.name ?: reminder.call.number}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Scheduled: $dateTimeString",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!reminder.reminder.notes.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reminder.reminder.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onCompleteClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Mark Complete",
                    tint = AllSetTeal
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Reminder",
                    tint = Red500
                )
            }
        }
    }
}

/**
 * Backward-compatible EmptyStateView delegating to ExpressiveEmptyState.
 */
@Composable
fun EmptyStateView(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    ExpressiveEmptyState(
        icon = icon,
        title = title,
        description = description,
        modifier = modifier
    )
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun CallCardPreview() {
    CallogTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CallCard(
                call = CallLogEntry(
                    id = 1L,
                    name = "Alice Smith",
                    number = "+1 (555) 234-5678",
                    duration = 145,
                    timestamp = System.currentTimeMillis() - 1000 * 60 * 15,
                    callType = "INCOMING",
                    recordingPath = "/path/to/recording.m4a",
                    isFavorite = true,
                    notes = "Discussed Q3 sales contract",
                    tags = listOf("work", "client"),
                    contactPhotoUri = null,
                    syncStatus = "SYNCED"
                ),
                onClick = {},
                onFavoriteToggle = {}
            )
        }
    }
}

@Preview(name = "Stat Cards", showBackground = true)
@Composable
fun StatCardPreview() {
    CallogTheme {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Total Calls",
                value = "128",
                icon = Icons.Default.Call,
                iconColor = AllSetBlue,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Avg Duration",
                value = "3m 42s",
                icon = Icons.Default.HourglassEmpty,
                iconColor = AllSetTeal,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview(name = "Tag Chips", showBackground = true)
@Composable
fun TagChipsPreview() {
    CallogTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagChip(tag = "work")
            TagChip(tag = "family")
            TagChip(tag = "spam")
            TagChip(tag = "friend")
        }
    }
}
