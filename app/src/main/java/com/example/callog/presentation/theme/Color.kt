package com.example.callog.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// AllSet Design System Base Colors
val AllSetBlue = Color(0xFF4A4FD8)       // #4A4FD8 (Primary Brand)
val AllSetLavender = Color(0xFFB8BCFF)   // #B8BCFF (Secondary Accent)
val AllSetAmber = Color(0xFFF4B544)      // #F4B544 (Warning/Alert)
val AllSetTeal = Color(0xFF45C79A)       // #45C79A (Success/Active)
val AllSetLightBg = Color(0xFFF6F7FB)    // #F6F7FB (Light Background)
val AllSetDarkBg = Color(0xFF1F1F1F)     // #1F1F1F (Dark Background)
val AllSetBorder = Color(0xFFD2D5E7)     // Soft light blue-gray for borders

// Dynamic Theme Mappings (support both light and dark themes)
val isDarkTheme: Boolean
    @Composable
    get() = MaterialTheme.colorScheme.background == AllSetDarkBg

val MidnightNavy: Color
    @Composable
    get() = if (isDarkTheme) AllSetDarkBg else AllSetLightBg

val Slate800: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val Slate700: Color
    @Composable
    get() = if (isDarkTheme) Color(0xFF3A3A3C) else AllSetBorder

val Teal500: Color
    @Composable
    get() = if (isDarkTheme) AllSetLavender else AllSetBlue

val Teal300: Color
    @Composable
    get() = if (isDarkTheme) AllSetBlue else AllSetLavender

val Amber500: Color
    @Composable
    get() = AllSetAmber

val Red500: Color
    @Composable
    get() = Color(0xFFE85C5C)

val Green500: Color
    @Composable
    get() = AllSetTeal

val GlassSurface: Color
    @Composable
    get() = if (isDarkTheme) Color(0x80282828) else Color(0x80FFFFFF)

// Dynamic Composable Mappings (resolves text contrast dynamically based on current theme)
val Slate50: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val Slate400: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

// --- Previously static (dark-only) colors — now theme-adaptive ---

/** Slate300: secondary body text. Light grey in dark mode, medium grey in light mode. */
val Slate300: Color
    @Composable
    get() = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF5E607E)

/** Slate900: heavy card/dialog backgrounds. Maps to MaterialTheme surface token. */
val Slate900: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

/** Slate950: deepest background layer. Maps to MaterialTheme background token. */
val Slate950: Color
    @Composable
    get() = MaterialTheme.colorScheme.background