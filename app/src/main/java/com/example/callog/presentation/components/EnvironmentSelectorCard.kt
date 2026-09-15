package com.example.callog.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.callog.domain.model.AppEnvironment
import com.example.callog.presentation.theme.*

@Composable
fun EnvironmentSelectorCard(
    currentEnvironment: AppEnvironment,
    onEnvironmentSelected: (AppEnvironment) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassyCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Teal500.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Teal300,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Server Environment",
                            color = Slate50,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Switch all services (Supabase & Firebase)",
                            color = Slate400,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Active badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentEnvironment == AppEnvironment.DEVELOPMENT) Teal500.copy(alpha = 0.2f) else Amber500.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (currentEnvironment == AppEnvironment.DEVELOPMENT) Teal500.copy(alpha = 0.4f) else Amber500.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (currentEnvironment == AppEnvironment.DEVELOPMENT) Teal300 else Amber500)
                        )
                        Text(
                            text = currentEnvironment.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentEnvironment == AppEnvironment.DEVELOPMENT) Teal300 else Amber500
                        )
                    }
                }
            }

            // 2 Large Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EnvironmentOptionButton(
                    modifier = Modifier.weight(1f),
                    title = "1. Development",
                    icon = Icons.Default.Code,
                    isSelected = currentEnvironment == AppEnvironment.DEVELOPMENT,
                    activeColor = Teal300,
                    onClick = { onEnvironmentSelected(AppEnvironment.DEVELOPMENT) }
                )

                EnvironmentOptionButton(
                    modifier = Modifier.weight(1f),
                    title = "2. Deployment",
                    icon = Icons.Default.RocketLaunch,
                    isSelected = currentEnvironment == AppEnvironment.DEPLOYMENT,
                    activeColor = Amber500,
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
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) activeColor.copy(alpha = 0.15f) else Slate800.copy(alpha = 0.5f),
        label = "bgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Slate700,
        label = "borderColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) activeColor else Slate400,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Slate50 else Slate300
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = activeColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
