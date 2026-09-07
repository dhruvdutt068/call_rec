package com.example.callog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.callog.core.telecom.TelecomRoleHelper
import com.example.callog.core.utils.ConnectionState
import com.example.callog.core.utils.ConnectivityService
import com.example.callog.domain.service.SyncManager
import com.example.callog.presentation.navigation.NavGraph
import com.example.callog.presentation.theme.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var connectivityService: ConnectivityService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Schedule periodic background sync to run every 15 minutes in the background
        syncManager.schedulePeriodicSync(15)

        // Trigger an immediate sync on app launch
        syncManager.startSync()

        // Automatically trigger sync when connectivity is restored
        lifecycleScope.launch {
            var previousState: ConnectionState? = null
            connectivityService.connectionStateFlow.collect { state ->
                if (previousState == ConnectionState.OFFLINE && state != ConnectionState.OFFLINE) {
                    android.util.Log.i("MainActivity", "Network connection restored. Running sync.")
                    syncManager.startSync()
                }
                previousState = state
            }
        }

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }

            // Permission requester for full telephony & call management
            val permissionsToRequest = remember {
                val perms = mutableListOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG,
                    Manifest.permission.READ_CONTACTS
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                } else {
                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
                    perms.add(Manifest.permission.MANAGE_OWN_CALLS)
                }
                perms.toTypedArray()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Permissions granted/denied callback */ }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(permissionsToRequest)
            }

            // Default Phone App / Dialer Role check
            var isDefaultDialer by remember { mutableStateOf(TelecomRoleHelper.isDefaultDialer(context)) }
            var bannerDismissed by remember { mutableStateOf(false) }

            val dialerRoleLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                isDefaultDialer = TelecomRoleHelper.isDefaultDialer(context)
            }

            CallogTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val callViewModel = hiltViewModel<com.example.callog.presentation.viewmodel.CallViewModel>()
                    val analyticsViewModel = hiltViewModel<com.example.callog.presentation.viewmodel.AnalyticsViewModel>()

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Prominent Default Dialer Banner if not yet default phone app
                        AnimatedVisibility(visible = !isDefaultDialer && !bannerDismissed) {
                            Surface(
                                color = AllSetBlue,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneInTalk,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Enable Full Call Management",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Set Callog as Default Phone App for in-call CRM cards & caller ID.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            TelecomRoleHelper.createRequestDialerRoleIntent(context)?.let { intent ->
                                                dialerRoleLauncher.launch(intent)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = AllSetBlue
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Set Default", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { bannerDismissed = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }

                        // Main Navigation Container
                        Box(modifier = Modifier.weight(1f)) {
                            NavGraph(
                                navController = navController,
                                callViewModel = callViewModel,
                                analyticsViewModel = analyticsViewModel,
                                darkTheme = darkTheme,
                                onDarkThemeChange = {
                                    darkTheme = it
                                    prefs.edit().putBoolean("dark_theme", it).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}