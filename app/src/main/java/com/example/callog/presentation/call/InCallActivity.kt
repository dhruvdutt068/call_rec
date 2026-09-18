package com.example.callog.presentation.call

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.callog.core.diagnostics.DeveloperLogger
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.callog.presentation.theme.CallogTheme
import com.example.callog.presentation.theme.Red500
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure window for lock-screen presentation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        enableEdgeToEdge()

        setContent {
            val prefs = remember { getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            val darkTheme = remember { prefs.getBoolean("dark_theme", false) }

            CallogTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CallSessionViewModel = hiltViewModel()
                    val state by viewModel.uiState.collectAsStateWithLifecycle()

                    // If there is genuinely no session, finish activity promptly
                    if (state is CallUiState.NoSession) {
                        LaunchedEffect(Unit) {
                            DeveloperLogger.info("INCALL_ACTIVITY", "No active call session. Finishing InCallActivity.")
                            finishAndRemoveTask()
                        }
                    }

                    AnimatedContent(
                        targetState = state,
                        contentKey = { targetState ->
                            when (targetState) {
                                is CallUiState.NoSession -> "no_session"
                                is CallUiState.Incoming -> "incoming"
                                is CallUiState.Connecting, is CallUiState.Active -> "active_call"
                                is CallUiState.Ended -> "ended"
                            }
                        },
                        label = "call_state_transition"
                    ) { targetState ->
                        when (targetState) {
                            is CallUiState.NoSession -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background)
                                )
                            }
                            is CallUiState.Incoming -> {
                                IncomingCallScreen(
                                    session = targetState.session,
                                    onAction = viewModel::onAction
                                )
                            }
                            is CallUiState.Connecting -> {
                                ActiveCallScreen(
                                    session = targetState.session,
                                    otherSessions = targetState.otherSessions,
                                    onAction = viewModel::onAction,
                                    onOpenPersonDetails = { personId ->
                                        openCanonicalPersonDetails(personId)
                                    }
                                )
                            }
                            is CallUiState.Active -> {
                                ActiveCallScreen(
                                    session = targetState.session,
                                    otherSessions = targetState.otherSessions,
                                    onAction = viewModel::onAction,
                                    onOpenPersonDetails = { personId ->
                                        openCanonicalPersonDetails(personId)
                                    }
                                )
                            }
                            is CallUiState.Ended -> {
                                PostCallSummaryScreen(
                                    session = targetState.session,
                                    onSaveWrapUp = { outcome, notes, isFavorite, scheduleDays ->
                                        viewModel.saveCallWrapUp(
                                            session = targetState.session,
                                            outcome = outcome,
                                            notes = notes,
                                            isFavorite = isFavorite,
                                            scheduleFollowUpDays = scheduleDays
                                        )
                                        finishAndRemoveTask()
                                    },
                                    onOpenPersonDetails = { personId ->
                                        openCanonicalPersonDetails(personId)
                                    },
                                    onDismiss = {
                                        finishAndRemoveTask()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If back is pressed during an in-call session, minimize to background rather than destroying the call
        moveTaskToBack(true)
    }

    private fun openCanonicalPersonDetails(personId: String) {
        try {
            val uri = Uri.parse("allset://contact/$personId")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("InCallActivity", "Failed to navigate to contact $personId", e)
        }
        finishAndRemoveTask()
    }
}

@Composable
fun CallEndedScreen(
    reason: String,
    onFinish: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1200)
        onFinish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Red500.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    tint = Red500,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
