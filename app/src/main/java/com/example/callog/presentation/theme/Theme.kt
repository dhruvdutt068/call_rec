package com.example.callog.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AllSetLavender,           // Soft lavender for dark theme readability
    secondary = AllSetBlue,
    tertiary = AllSetTeal,
    background = AllSetDarkBg,          // #1F1F1F page background
    surface = Color(0xFF2A2A2E),        // Card surface — slightly lighter than background
    onPrimary = AllSetDarkBg,
    onSecondary = Color.White,
    onTertiary = AllSetDarkBg,
    onBackground = AllSetLightBg,       // Near-white text on dark background
    onSurface = AllSetLightBg,
    surfaceVariant = Color(0xFF313135), // Elevated chip/field surface
    onSurfaceVariant = Color(0xFFBBBBCC), // Muted grey-lavender — readable on dark cards
    outline = Color(0xFF48484E),
    secondaryContainer = Color(0xFF3A3A4E), // Subtle tinted container for secondary buttons
    onSecondaryContainer = AllSetLavender
)

private val LightColorScheme = lightColorScheme(
    primary = AllSetBlue,
    secondary = AllSetLavender,
    tertiary = AllSetTeal,
    background = AllSetLightBg,         // #F6F7FB page background
    surface = Color(0xFFFFFFFF),        // White card surface — visible against background
    onPrimary = Color.White,
    onSecondary = AllSetDarkBg,
    onTertiary = Color.White,
    onBackground = AllSetDarkBg,        // Near-black text on light background
    onSurface = AllSetDarkBg,
    surfaceVariant = Color(0xFFE8EAF2), // Slightly tinted chip/field surface
    onSurfaceVariant = Color(0xFF5E607E), // Medium grey text on light variant
    outline = AllSetBorder,
    secondaryContainer = Color(0xFFE8EAFF), // Light blue-purple tinted container
    onSecondaryContainer = AllSetBlue
)

@Composable
fun CallogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to prioritize our custom premium color system
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}