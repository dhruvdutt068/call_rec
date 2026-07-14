package com.example.callog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.example.callog.presentation.navigation.NavGraph
import com.example.callog.presentation.theme.CallogTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import com.example.callog.domain.service.SyncManager
import com.example.callog.core.utils.ConnectivityService
import com.example.callog.core.utils.ConnectionState
import kotlinx.coroutines.flow.collect
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
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
            var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }

            CallogTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val callViewModel = hiltViewModel<com.example.callog.presentation.viewmodel.CallViewModel>()
                    val analyticsViewModel = hiltViewModel<com.example.callog.presentation.viewmodel.AnalyticsViewModel>()

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