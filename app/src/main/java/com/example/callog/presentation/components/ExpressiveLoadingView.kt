package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
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
 * Material 3 Expressive Loading View.
 * Provides clear, contextual loading feedback with progress indicator and explanatory status.
 */
@Composable
fun ExpressiveLoadingView(
    message: String,
    modifier: Modifier = Modifier,
    subMessage: String? = null,
    icon: ImageVector? = Icons.Outlined.Sync,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CallogShapes.container)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        if (!subMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Loading View - Light", showBackground = true)
@Composable
fun ExpressiveLoadingViewLightPreview() {
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpressiveLoadingView(
                message = "Syncing Call History",
                subMessage = "Uploading call audio and metadata to Firestore & Supabase"
            )
        }
    }
}

@Preview(name = "Loading View - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExpressiveLoadingViewDarkPreview() {
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ExpressiveLoadingView(
                message = "Scanning Audio Recordings",
                subMessage = "Matching audio duration to phonebook call logs"
            )
        }
    }
}
