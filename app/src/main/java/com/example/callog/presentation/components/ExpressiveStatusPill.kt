package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.theme.*

/**
 * Material 3 Expressive Status Pill.
 * Provides clear, accessible status communication using color, icon, and text tokens.
 */
@Composable
fun ExpressiveStatusPill(
    label: String,
    accentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CallogShapes.pill,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = label,
                style = CallogTypography.statusLabel,
                color = accentColor
            )
        }
    }
}

@Composable
fun LeadStatusPill(
    status: com.example.callog.domain.model.LeadStatus,
    modifier: Modifier = Modifier
) {
    LeadStatusPill(status = status.name, modifier = modifier)
}

@Composable
fun LeadStatusPill(
    status: String,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (status.uppercase()) {
        "HOT" -> CallogSemanticColors.LeadHot to Icons.Default.Whatshot
        "WARM" -> CallogSemanticColors.LeadWarm to Icons.Default.WbSunny
        "COLD" -> CallogSemanticColors.LeadCold to Icons.Default.AcUnit
        "FOLLOW_UP", "FOLLOWUP" -> CallogSemanticColors.LeadFollowUp to Icons.Default.Schedule
        "CUSTOMER" -> CallogSemanticColors.LeadCustomer to Icons.Default.BusinessCenter
        "VIP" -> CallogSemanticColors.LeadVip to Icons.Default.Star
        "WON" -> CallogSemanticColors.LeadWon to Icons.Default.CheckCircle
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Info
    }
    ExpressiveStatusPill(
        label = status.uppercase(),
        accentColor = color,
        icon = icon,
        modifier = modifier
    )
}

@Composable
fun SyncStatusPill(
    syncStatus: String = "",
    status: String = syncStatus,
    modifier: Modifier = Modifier
) {
    val effectiveStatus = if (syncStatus.isNotEmpty()) syncStatus else status
    val (label, color, icon) = when (effectiveStatus.uppercase()) {
        "SYNCED" -> Triple("Synced", CallogSemanticColors.SyncSuccess, Icons.Default.CloudDone)
        "UPLOADING" -> Triple("Syncing...", CallogSemanticColors.SyncInProgress, Icons.Default.CloudUpload)
        "FAILED" -> Triple("Failed", CallogSemanticColors.SyncFailed, Icons.Default.CloudOff)
        else -> Triple("Pending", CallogSemanticColors.SyncPending, Icons.Default.CloudQueue)
    }
    ExpressiveStatusPill(
        label = label,
        accentColor = color,
        icon = icon,
        modifier = modifier
    )
}

@Composable
fun PriorityPill(
    priority: com.example.callog.domain.model.LeadPriority,
    modifier: Modifier = Modifier
) {
    PriorityPill(priority = priority.name, modifier = modifier)
}

@Composable
fun PriorityPill(
    priority: String,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (priority.uppercase()) {
        "HIGH" -> Red500 to Icons.Default.PriorityHigh
        "MEDIUM" -> Amber500 to Icons.Default.Remove
        "LOW" -> Green500 to Icons.Default.ArrowDownward
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Flag
    }
    ExpressiveStatusPill(
        label = priority,
        accentColor = color,
        icon = icon,
        modifier = modifier
    )
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Status Pills - Light", showBackground = true)
@Composable
fun ExpressiveStatusPillsLightPreview() {
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeadStatusPill(status = "HOT")
                LeadStatusPill(status = "WARM")
                SyncStatusPill(syncStatus = "SYNCED")
                PriorityPill(priority = "High")
            }
        }
    }
}

@Preview(name = "Status Pills - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExpressiveStatusPillsDarkPreview() {
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LeadStatusPill(status = "HOT")
                LeadStatusPill(status = "WARM")
                SyncStatusPill(syncStatus = "SYNCED")
                PriorityPill(priority = "High")
            }
        }
    }
}
