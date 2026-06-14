package com.impulsive.app.backend.data.local.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Reports whether a VPN is currently active on the device. Report-only. Android allows
 * one active VPN at a time, so before the DNS filter enables, the gate checks this and
 * tells the user to turn off any other VPN first. Run this before our own VPN starts, so
 * any VPN transport found here belongs to another app.
 */
class ActiveVpnChecker(
    private val context: Context,
) {
    fun isAnotherVpnActive(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            connectivityManager.allNetworks.any { network ->
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        }.getOrDefault(false)
    }
}
