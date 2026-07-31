package com.example.callog.presentation.screens.splash

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.callog.R
import com.example.callog.presentation.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: (isPermissionGranted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Scale animation for logo
    val scaleAnim = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        scaleAnim.animateTo(
            targetValue = 1.1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        scaleAnim.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 150)
        )

        delay(1500) // Wait for splash display

        // Check permissions
        val hasCallLogPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val storagePermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            context,
            storagePermission
        ) == PackageManager.PERMISSION_GRANTED

        onNavigateNext(hasCallLogPermission && hasContactsPermission && hasPhoneStatePermission && hasStoragePermission)
    }

    // Theme-adaptive premium radial gradient
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val gradientColors = if (isDark) {
        listOf(
            Color(0xFF1E215C), // Deep midnight blue center glow
            Color(0xFF151735), // Dark twilight transition
            AllSetDarkBg       // Deep dark background
        )
    } else {
        listOf(
            AllSetLavender.copy(alpha = 0.4f), // Soft lavender glow
            AllSetLightBg.copy(alpha = 0.9f),  // Transition
            AllSetLightBg                      // Light background page color
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = gradientColors,
                    radius = 1400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_allset_logo),
            contentDescription = "AllSet Logo",
            modifier = Modifier
                .scale(scaleAnim.value)
                .fillMaxWidth(0.75f),
            contentScale = ContentScale.Fit
        )
    }
}

