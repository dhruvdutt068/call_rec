package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.theme.*

/**
 * Material 3 Expressive Empty State Component.
 * Presents a cohesive, informative placeholder across all views without emojis.
 */
@Composable
fun ExpressiveEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val effectiveAction = onActionClick ?: onAction

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Tonal Icon Container
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CallogShapes.container)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (actionLabel != null && effectiveAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = effectiveAction,
                shape = CallogShapes.interactive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (actionIcon != null) {
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Empty State - Light", showBackground = true)
@Composable
fun ExpressiveEmptyStateLightPreview() {
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpressiveEmptyState(
                icon = Icons.Outlined.MicNone,
                title = "No Recordings Found",
                description = "Call recordings matched to your call history will appear here once audio files are discovered.",
                actionLabel = "Scan Directory",
                actionIcon = Icons.Outlined.Refresh,
                onAction = {}
            )
        }
    }
}

@Preview(name = "Empty State - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExpressiveEmptyStateDarkPreview() {
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpressiveEmptyState(
                icon = Icons.Outlined.PeopleOutline,
                title = "No Contacts Found",
                description = "Search across local phonebook and Cloud CRM database.",
                actionLabel = "Add New Lead",
                actionIcon = Icons.Outlined.PersonAdd,
                onAction = {}
            )
        }
    }
}
