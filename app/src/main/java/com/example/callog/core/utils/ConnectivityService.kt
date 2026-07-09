package com.example.callog.core.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState {
    WIFI, MOBILE, OFFLINE
}

@Singleton
class ConnectivityService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val connectionStateFlow: Flow<ConnectionState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                
                if (isWifi) {
                    trySend(ConnectionState.WIFI)
                } else if (isMobile) {
                    trySend(ConnectionState.MOBILE)
                } else {
                    trySend(ConnectionState.WIFI) // Treat other connections like ethernet as WIFI
                }
            }

            override fun onLost(network: Network) {
                trySend(ConnectionState.OFFLINE)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                if (isWifi) {
                    trySend(ConnectionState.WIFI)
                } else if (isMobile) {
                    trySend(ConnectionState.MOBILE)
                } else {
                    trySend(ConnectionState.WIFI)
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (capabilities == null) {
            trySend(ConnectionState.OFFLINE)
        } else {
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            if (isWifi) {
                trySend(ConnectionState.WIFI)
            } else if (isMobile) {
                trySend(ConnectionState.MOBILE)
            } else {
                trySend(ConnectionState.WIFI)
            }
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    fun isConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifi(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
