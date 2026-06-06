package io.github.mobdev.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope

class NetworkStatus(context: Context, scope: CoroutineScope) {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService()

    private val onlineFlow: Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }

        fun emitCurrent() {
            val active = cm.activeNetwork
            val caps = active?.let { cm.getNetworkCapabilities(it) }
            // INTERNET capability is enough — VALIDATED can lag a few seconds after
            // airplane mode is turned off, which delays the queue flush.
            val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            trySend(online)
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitCurrent()
            override fun onLost(network: Network) = emitCurrent()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                emitCurrent()
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(request, callback)
        emitCurrent()

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }

    val online: StateFlow<Boolean> = onlineFlow
        .stateIn(scope, SharingStarted.Eagerly, initialValue = false)
}
