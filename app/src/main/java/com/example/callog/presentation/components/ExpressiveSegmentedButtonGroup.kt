package com.example.callog.presentation.components

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.callog.presentation.theme.*

data class SegmentedOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val count: Int? = null
)

data class ExpressiveSegmentedButtonItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val count: Int? = null
)


@Composable
fun ExpressiveSegmentedButtonGroup(
    items: List<ExpressiveSegmentedButtonItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    ExpressiveSegmentedButtonGroup(
        options = items.map { SegmentedOption(it.key, it.label, it.icon, it.count) },
        selectedValue = selectedKey,
        onValueSelected = onItemSelected,
        modifier = modifier,
        activeColor = activeColor
    )
}

/**
 * Material 3 Expressive Segmented Button Group.
 * Reusable horizontal control supporting spring-based transitions, icons, and 48dp touch targets.
 */
@Composable
fun <T> ExpressiveSegmentedButtonGroup(
    options: List<SegmentedOption<T>>,
    selectedValue: T,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CallogShapes.interactive),
        shape = CallogShapes.interactive,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue

                val itemBgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        activeColor.copy(alpha = 0.14f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = CallogMotion.snappySpring(),
                    label = "segBgColor"
                )

                val itemBorderColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        activeColor.copy(alpha = 0.5f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = CallogMotion.snappySpring(),
                    label = "segBorderColor"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        activeColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = CallogMotion.snappySpring(),
                    label = "segContentColor"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CallogShapes.subtle)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onValueSelected(option.value) }
                        )
                        .defaultMinSize(minHeight = 44.dp),
                    shape = CallogShapes.subtle,
                    color = itemBgColor,
                    border = if (isSelected) BorderStroke(1.dp, itemBorderColor) else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (option.icon != null) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1
                        )

                        if (option.count != null && option.count > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = CallogShapes.pill,
                                color = if (isSelected) activeColor else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = option.count.toString(),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Segmented Group - Light", showBackground = true)
@Composable
fun ExpressiveSegmentedButtonGroupLightPreview() {
    var selected by remember { mutableStateOf("ALL") }
    CallogTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ExpressiveSegmentedButtonGroup(
                    options = listOf(
                        SegmentedOption("ALL", "All", Icons.Outlined.List, 42),
                        SegmentedOption("PENDING", "Pending", Icons.Outlined.Schedule, 5),
                        SegmentedOption("COMPLETED", "Done", Icons.Outlined.CheckCircle, 37)
                    ),
                    selectedValue = selected,
                    onValueSelected = { selected = it }
                )
            }
        }
    }
}

@Preview(name = "Segmented Group - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ExpressiveSegmentedButtonGroupDarkPreview() {
    var selected by remember { mutableStateOf("PENDING") }
    CallogTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                ExpressiveSegmentedButtonGroup(
                    options = listOf(
                        SegmentedOption("ALL", "All", Icons.Outlined.List),
                        SegmentedOption("PENDING", "Pending", Icons.Outlined.Schedule),
                        SegmentedOption("COMPLETED", "Done", Icons.Outlined.CheckCircle)
                    ),
                    selectedValue = selected,
                    onValueSelected = { selected = it }
                )
            }
        }
    }
}
