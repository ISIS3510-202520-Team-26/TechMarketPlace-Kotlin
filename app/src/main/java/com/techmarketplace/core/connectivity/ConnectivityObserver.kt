package com.techmarketplace.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Emits true when the current default network has INTERNET + VALIDATED.
 *
 * Uses ConnectivityManager.registerDefaultNetworkCallback() on API 24+,
 * and a NetworkRequest callback on API 21–23.
 */
object ConnectivityObserver {

    fun observe(context: Context): Flow<Boolean> {
        val appCtx = context.applicationContext
        val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        return callbackFlow {
            // Send the immediate state first
            trySend(isOnlineNow(appCtx))

            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    // Prefer capabilitiesChanged for actual internet availability updates
                    trySend(caps.hasInternetAndValidated())
                }
                override fun onAvailable(network: Network) {
                    // Don't rely solely on this; may be unvalidated initially
                }
                override fun onLost(network: Network) {
                    trySend(false)
                }
                override fun onUnavailable() {
                    trySend(false)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Default network updates (system-chosen active network)
                cm.registerDefaultNetworkCallback(cb) // API 24+
            } else {
                // Fallback for API 21–23: listen to any INTERNET-capable network
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(req, cb)
            }

            awaitClose {
                try {
                    cm.unregisterNetworkCallback(cb)
                } catch (_: Exception) { /* already unregistered */ }
            }
        }.distinctUntilChanged()
    }

    /**
     * Snapshot check you can call anytime.
     */
    fun isOnlineNow(context: Context): Boolean {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasInternetAndValidated()
    }

    private fun NetworkCapabilities.hasInternetAndValidated(): Boolean {
        // INTERNET means the network *should* provide internet,
        // VALIDATED means the system verified actual connectivity.
        return hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
