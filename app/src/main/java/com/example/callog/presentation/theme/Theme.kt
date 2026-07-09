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
    primary = Color(0xFFF0A878),        // Light warm sienna for readability
    secondary = Color(0xFFCEC6BE),      // Warm grey secondary
    tertiary = Color(0xFFE8A0A0),       // Soft dusty rose
    background = Color(0xFF1E1714),     // Warm dark background
    surface = Color(0xFF2A201A),        // Warm dark card surface
    onPrimary = Color(0xFF1E1714),
    onSecondary = Color.White,
    onBackground = Color(0xFFFAF5EE),
    onSurface = Color(0xFFFAF5EE),
    surfaceVariant = Color(0xFF3A2E26),
    onSurfaceVariant = Color(0xFFCEC6BE),
    outline = Color(0xFF504840)
)

private val LightColorScheme = lightColorScheme(
    primary = SaharaBurntSienna,
    secondary = SaharaWarmGray,
    tertiary = SaharaDustyRose,
    background = SaharaWarmLinen,
    surface = SaharaWarmLinen,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = SaharaWarmGrayDark,
    onSurface = SaharaWarmGrayDark,
    surfaceVariant = SaharaWarmGrayMedium,
    onSurfaceVariant = SaharaWarmGray,
    outline = SaharaBorder
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