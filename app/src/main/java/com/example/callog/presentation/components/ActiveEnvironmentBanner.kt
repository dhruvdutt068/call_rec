package com.example.callog.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.domain.model.preset.AppPreset
import com.example.callog.domain.model.preset.PresetEnvironment
import com.example.callog.presentation.theme.*

@Composable
fun ActiveEnvironmentBanner(
    activePreset: AppPreset?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDetailedBanner: Boolean = false
) {
    if (activePreset == null) return

    val env = activePreset.environment
    val (badgeColor, icon, label) = when (env) {
        PresetEnvironment.DEVELOPMENT -> Triple(Teal400, Icons.Default.Code, "DEVELOPMENT")
        PresetEnvironment.STAGING -> Triple(AllSetTeal, Icons.Default.Science, "STAGING")
        PresetEnvironment.PRODUCTION -> Triple(Amber500, Icons.Default.RocketLaunch, "PRODUCTION")
        PresetEnvironment.CUSTOM -> Triple(AllSetLavender, Icons.Default.Settings, "CUSTOM")
    }

    if (showDetailedBanner && env == PresetEnvironment.PRODUCTION) {
        // High visibility warning banner for production mode
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(8.dp),
            color = Amber500.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Amber500.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Amber500,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LIVE PRODUCTION ENVIRONMENT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Amber500
                    )
                }
                Text(
                    text = "Preset: ${activePreset.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate300
                )
            }
        }
    } else {
        // Compact Pill Badge
        Surface(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() },
            shape = RoundedCornerShape(20.dp),
            color = badgeColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$label: ${activePreset.name}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}
