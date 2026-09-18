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
    onPrimary = AllSetDarkBg,
    primaryContainer = Color(0xFF35397A),
    onPrimaryContainer = AllSetLavender,
    secondary = AllSetBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A3A4E), // Subtle tinted container for secondary buttons
    onSecondaryContainer = AllSetLavender,
    tertiary = AllSetTeal,
    onTertiary = AllSetDarkBg,
    tertiaryContainer = Color(0xFF1E4D3E),
    onTertiaryContainer = AllSetTeal,
    background = AllSetDarkBg,          // #1F1F1F page background
    onBackground = AllSetLightBg,       // Near-white text on dark background
    surface = Color(0xFF2A2A2E),        // Card surface — slightly lighter than background
    onSurface = AllSetLightBg,
    surfaceVariant = Color(0xFF313135), // Elevated chip/field surface
    onSurfaceVariant = Color(0xFFBBBBCC), // Muted grey-lavender — readable on dark cards
    surfaceContainerLowest = Color(0xFF19191C),
    surfaceContainerLow = Color(0xFF232326),
    surfaceContainer = Color(0xFF2A2A2E),
    surfaceContainerHigh = Color(0xFF313135),
    surfaceContainerHighest = Color(0xFF38383D),
    outline = Color(0xFF48484E),
    outlineVariant = Color(0xFF3A3A40)
)

private val LightColorScheme = lightColorScheme(
    primary = AllSetBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAFF),
    onPrimaryContainer = AllSetBlue,
    secondary = AllSetLavender,
    onSecondary = AllSetDarkBg,
    secondaryContainer = Color(0xFFE8EAFF), // Light blue-purple tinted container
    onSecondaryContainer = AllSetBlue,
    tertiary = AllSetTeal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F7EF),
    onTertiaryContainer = Color(0xFF1E6B52),
    background = AllSetLightBg,         // #F6F7FB page background
    onBackground = AllSetDarkBg,        // Near-black text on light background
    surface = Color(0xFFFFFFFF),        // White card surface — visible against background
    onSurface = AllSetDarkBg,
    surfaceVariant = Color(0xFFE8EAF2), // Slightly tinted chip/field surface
    onSurfaceVariant = Color(0xFF5E607E), // Medium grey text on light variant
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F4F9),
    surfaceContainer = Color(0xFFECEEF5),
    surfaceContainerHigh = Color(0xFFE4E6F0),
    surfaceContainerHighest = Color(0xFFDCDEEA),
    outline = AllSetBorder,
    outlineVariant = Color(0xFFE0E2EE)
)

@Composable
fun CallogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Prioritize custom AllSet M3 Expressive color system
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