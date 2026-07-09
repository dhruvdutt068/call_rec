package com.example.callog.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sahara Design System Base Colors
val SaharaBurntSienna = Color(0xFFC2652A)     // Primary sienna
val SaharaWarmLinen = Color(0xFFFAF5EE)       // Warm background linen
val SaharaDustyRose = Color(0xFF8C3C3C)       // Tertiary sienna/rose
val SaharaWarmGrayDark = Color(0xFF3A302A)    // Obsidian warm dark
val SaharaWarmGray = Color(0xFF78706A)        // Slate gray
val SaharaWarmGrayMedium = Color(0xFFECE6DC)  // Surface container high / secondary backgrounds
val SaharaWarmGrayLight = Color(0xFFF2ECE4)   // Surface container low / card backgrounds
val SaharaBorder = Color(0xFFD8D0C8)          // Borders & dividers

// Static Mappings (safe for non-composable contexts like Canvas DrawScope)
val MidnightNavy = SaharaWarmLinen
val Slate800 = SaharaWarmGrayLight
val Slate700 = SaharaBorder
val Teal500 = SaharaBurntSienna
val Teal300 = SaharaBurntSienna
val Amber500 = SaharaDustyRose
val Red500 = Color(0xFFC0392B)
val Green500 = Color(0xFF2E7D32)
val GlassSurface = Color(0x99F2ECE4)

// Dynamic Composable Mappings (resolves text contrast dynamically based on current theme)
val Slate50: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val Slate400: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant