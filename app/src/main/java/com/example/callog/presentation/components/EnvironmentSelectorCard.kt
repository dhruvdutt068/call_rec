package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.presentation.theme.*

@Composable
fun EnvironmentSelectorCard(
    currentEnvironment: AppEnvironment,
    onEnvironmentSelected: (AppEnvironment) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDev = currentEnvironment == AppEnvironment.DEVELOPMENT
    val activeAccentColor = if (isDev) MaterialTheme.colorScheme.primary else AllSetAmber

    GlassyCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Cloud Icon + Title & Current Env Badge + Description Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(activeAccentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        tint = activeAccentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Server Environment",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Current Environment Pill Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeAccentColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, activeAccentColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = currentEnvironment.label,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = activeAccentColor
                            )
                        }
                    }

                    Text(
                        text = "Switches Supabase + Firebase configuration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Segmented Horizontal Environment Selector Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EnvironmentOptionButton(
                    modifier = Modifier.weight(1f),
                    title = "Development",
                    icon = Icons.Outlined.Code,
                    isSelected = isDev,
                    selectedAccentColor = MaterialTheme.colorScheme.primary,
                    onClick = { onEnvironmentSelected(AppEnvironment.DEVELOPMENT) }
                )

                EnvironmentOptionButton(
                    modifier = Modifier.weight(1f),
                    title = "Deployment",
                    icon = Icons.Outlined.RocketLaunch,
                    isSelected = !isDev,
                    selectedAccentColor = AllSetAmber,
                    onClick = { onEnvironmentSelected(AppEnvironment.DEPLOYMENT) }
                )
            }
        }
    }
}

@Composable
private fun EnvironmentOptionButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedAccentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            selectedAccentColor.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        label = "btnBgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            selectedAccentColor
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        },
        label = "btnBorderColor"
    )
    val iconAndTextColor by animateColorAsState(
        targetValue = if (isSelected) {
            selectedAccentColor
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "btnContentColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconAndTextColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = selectedAccentColor,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "1. Development - Light", showBackground = true)
@Composable
fun EnvironmentSelectorCardDevLightPreview() {
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                EnvironmentSelectorCard(
                    currentEnvironment = AppEnvironment.DEVELOPMENT,
                    onEnvironmentSelected = {}
                )
            }
        }
    }
}

@Preview(name = "2. Development - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EnvironmentSelectorCardDevDarkPreview() {
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                EnvironmentSelectorCard(
                    currentEnvironment = AppEnvironment.DEVELOPMENT,
                    onEnvironmentSelected = {}
                )
            }
        }
    }
}

@Preview(name = "3. Deployment - Light", showBackground = true)
@Composable
fun EnvironmentSelectorCardDeployLightPreview() {
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                EnvironmentSelectorCard(
                    currentEnvironment = AppEnvironment.DEPLOYMENT,
                    onEnvironmentSelected = {}
                )
            }
        }
    }
}

@Preview(name = "4. Deployment - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun EnvironmentSelectorCardDeployDarkPreview() {
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                EnvironmentSelectorCard(
                    currentEnvironment = AppEnvironment.DEPLOYMENT,
                    onEnvironmentSelected = {}
                )
            }
        }
    }
}
