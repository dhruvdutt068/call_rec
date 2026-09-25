package com.example.callog.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.callog.presentation.navigation3.CrmBottomTab
import com.example.callog.presentation.navigation3.allset.AllSetNavKey
import com.example.callog.presentation.theme.AllSetBlue
import com.example.callog.presentation.theme.AllSetLavender
import com.example.callog.presentation.theme.AllSetTeal
import com.example.callog.presentation.theme.CallogMotion

/**
 * Custom Material 3 Expressive Floating Bottom Navigation Dock with Center-Docked Action FAB.
 *
 * Contains:
 * 1. Dialer (Left destination -> DialerScreen with Recent Call Logs & Keypad)
 * 2. Contacts (CRM & Phonebook directory)
 * 3. Center Floating Action Button (Quick Actions Menu)
 * 4. Recordings (Audio vault)
 * 5. Settings (Preferences & tools)
 *
 * Stateless component adhering to Clean Architecture & M3 Expressive design tokens.
 */
@Composable
fun CallogBottomNavigation(
    selectedTab: CrmBottomTab,
    onTabSelected: (CrmBottomTab) -> Unit,
    onAddContact: () -> Unit,
    onCreateTask: () -> Unit,
    onScheduleMeeting: () -> Unit,
    onLogCall: () -> Unit,
    modifier: Modifier = Modifier
) = CallogBottomNavigation(
    isContactsSelected = selectedTab == CrmBottomTab.CONTACTS,
    isDialerSelected = selectedTab == CrmBottomTab.DIALER,
    isRecordingsSelected = selectedTab == CrmBottomTab.RECORDINGS,
    isSettingsSelected = selectedTab == CrmBottomTab.SETTINGS,
    onContactsClick = { onTabSelected(CrmBottomTab.CONTACTS) },
    onDialerClick = { onTabSelected(CrmBottomTab.DIALER) },
    onRecordingsClick = { onTabSelected(CrmBottomTab.RECORDINGS) },
    onSettingsClick = { onTabSelected(CrmBottomTab.SETTINGS) },
    onAddContact = onAddContact,
    onCreateTask = onCreateTask,
    onScheduleMeeting = onScheduleMeeting,
    onLogCall = onLogCall,
    modifier = modifier
)

@Composable
fun CallogBottomNavigation(
    currentDestination: AllSetNavKey?,
    onContactsClick: () -> Unit,
    onRecordingsClick: () -> Unit,
    onAddContact: () -> Unit,
    onCreateTask: () -> Unit,
    onScheduleMeeting: () -> Unit,
    onLogCall: () -> Unit,
    modifier: Modifier = Modifier
) = CallogBottomNavigation(
    isContactsSelected = currentDestination is AllSetNavKey.Contacts || currentDestination == AllSetNavKey.ContactsTab,
    isDialerSelected = false,
    isRecordingsSelected = currentDestination == AllSetNavKey.CallLogs,
    isSettingsSelected = false,
    onContactsClick = onContactsClick,
    onDialerClick = {},
    onRecordingsClick = onRecordingsClick,
    onSettingsClick = {},
    onAddContact = onAddContact,
    onCreateTask = onCreateTask,
    onScheduleMeeting = onScheduleMeeting,
    onLogCall = onLogCall,
    modifier = modifier
)

@Composable
fun CallogBottomNavigation(
    isContactsSelected: Boolean,
    isDialerSelected: Boolean,
    isRecordingsSelected: Boolean,
    isSettingsSelected: Boolean,
    onContactsClick: () -> Unit,
    onDialerClick: () -> Unit,
    onRecordingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddContact: () -> Unit,
    onCreateTask: () -> Unit,
    onScheduleMeeting: () -> Unit,
    onLogCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isQuickActionsOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Fullscreen backdrop dismissal when quick actions menu is active
        if (isQuickActionsOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isQuickActionsOpen = false
                    }
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        // Quick Actions Popup Menu (Positioned directly above the center FAB)
        AnimatedVisibility(
            visible = isQuickActionsOpen,
            enter = fadeIn(animationSpec = CallogMotion.snappySpring()) +
                    slideInVertically(
                        initialOffsetY = { it / 3 },
                        animationSpec = CallogMotion.gentleSpring()
                    ) +
                    scaleIn(
                        initialScale = 0.85f,
                        animationSpec = CallogMotion.bouncySpring()
                    ),
            exit = fadeOut(animationSpec = CallogMotion.snappySpring()) +
                    slideOutVertically(
                        targetOffsetY = { it / 3 },
                        animationSpec = CallogMotion.snappySpring()
                    ) +
                    scaleOut(
                        targetScale = 0.85f,
                        animationSpec = CallogMotion.snappySpring()
                    ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 84.dp
                )
        ) {
            QuickActionsCard(
                onAddContact = {
                    isQuickActionsOpen = false
                    onAddContact()
                },
                onCreateTask = {
                    isQuickActionsOpen = false
                    onCreateTask()
                },
                onScheduleMeeting = {
                    isQuickActionsOpen = false
                    onScheduleMeeting()
                },
                onLogCall = {
                    isQuickActionsOpen = false
                    onLogCall()
                }
            )
        }

        // Floating Dock Container + Overlapping Center FAB
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 12.dp
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Rounded Pill Dock Surface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 4.dp,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Dialer + Contacts
                    CallogDockItem(
                        selected = isDialerSelected,
                        onClick = onDialerClick,
                        selectedIcon = Icons.Filled.Dialpad,
                        unselectedIcon = Icons.Filled.Dialpad,
                        label = "Dialer",
                        contentDescription = "Dialer",
                        modifier = Modifier.weight(1f)
                    )
                    CallogDockItem(
                        selected = isContactsSelected,
                        onClick = onContactsClick,
                        selectedIcon = Icons.Filled.Contacts,
                        unselectedIcon = Icons.Outlined.Contacts,
                        label = "Contacts",
                        contentDescription = "Contacts",
                        modifier = Modifier.weight(1f)
                    )

                    // Reserved center gap for FAB clearance
                    Spacer(modifier = Modifier.width(68.dp))

                    // Right side: Recordings + Settings
                    CallogDockItem(
                        selected = isRecordingsSelected,
                        onClick = onRecordingsClick,
                        selectedIcon = Icons.Filled.GraphicEq,
                        unselectedIcon = Icons.Outlined.GraphicEq,
                        label = "Recordings",
                        contentDescription = "Recordings",
                        modifier = Modifier.weight(1f)
                    )
                    CallogDockItem(
                        selected = isSettingsSelected,
                        onClick = onSettingsClick,
                        selectedIcon = Icons.Filled.Settings,
                        unselectedIcon = Icons.Filled.Settings,
                        label = "Settings",
                        contentDescription = "Settings",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Center Floating Action Button (Overlapping Dock Surface)
            CallogCenterFab(
                isOpen = isQuickActionsOpen,
                onClick = { isQuickActionsOpen = !isQuickActionsOpen },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-14).dp)
            )
        }
    }
}

/**
 * Center Action FAB that visually overlaps the floating navigation dock.
 * Animates smoothly between '+' (closed) and '×' (open).
 */
@Composable
fun CallogCenterFab(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = CallogMotion.snappySpring(),
        label = "fab_press_scale"
    )

    val rotationAngle by animateFloatAsState(
        targetValue = if (isOpen) 135f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fab_rotation"
    )

    val elevation by animateDpAsState(
        targetValue = if (isOpen) 12.dp else if (isPressed) 4.dp else 8.dp,
        animationSpec = CallogMotion.snappySpring(),
        label = "fab_elevation"
    )

    FloatingActionButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(58.dp)
            .scale(scale)
            .semantics {
                role = Role.Button
                contentDescription = "Quick actions"
            },
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = elevation,
            pressedElevation = 4.dp,
            hoveredElevation = 10.dp,
            focusedElevation = 10.dp
        )
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .rotate(rotationAngle)
        )
    }
}

/**
 * Individual Navigation Item inside the floating dock with M3 selection container.
 */
@Composable
private fun CallogDockItem(
    selected: Boolean,
    onClick: () -> Unit,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        },
        animationSpec = CallogMotion.ColorSpring,
        label = "dock_item_icon_color"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = CallogMotion.ColorSpring,
        label = "dock_item_text_color"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = CallogMotion.ColorSpring,
        label = "dock_item_container_color"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 40.dp),
                onClick = onClick
            )
            .semantics {
                this.selected = selected
                this.role = Role.Tab
                this.contentDescription = contentDescription
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            // M3 Expressive Pill Indicator
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) selectedIcon else unselectedIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor
            )
        }
    }
}

/**
 * Compact Quick Actions Popup Menu displayed when tapping the center Action FAB.
 */
@Composable
fun QuickActionsCard(
    onAddContact: () -> Unit,
    onCreateTask: () -> Unit,
    onScheduleMeeting: () -> Unit,
    onLogCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = 220.dp, max = 280.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.2f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 8.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            QuickActionMenuItem(
                icon = Icons.Outlined.PersonAdd,
                iconTint = AllSetTeal,
                iconContainerColor = AllSetTeal.copy(alpha = 0.15f),
                label = "Add Contact",
                description = "New CRM lead / contact",
                onClick = onAddContact
            )

            QuickActionMenuItem(
                icon = Icons.Outlined.TaskAlt,
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                label = "Create Task",
                description = "Add follow-up reminder",
                onClick = onCreateTask
            )

            QuickActionMenuItem(
                icon = Icons.Outlined.Event,
                iconTint = AllSetLavender,
                iconContainerColor = AllSetLavender.copy(alpha = 0.2f),
                label = "Schedule Meeting",
                description = "Calendar appointment",
                onClick = onScheduleMeeting
            )

            QuickActionMenuItem(
                icon = Icons.Outlined.Phone,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                label = "Log Call",
                description = "Open keypad & dialer",
                onClick = onLogCall
            )
        }
    }
}

/**
 * Interactive Item inside the Quick Actions Popup Menu.
 */
@Composable
private fun QuickActionMenuItem(
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
