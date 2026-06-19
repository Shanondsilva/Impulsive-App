package com.impulsive.app.backend.service.protection

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

/**
 * Starts and stops the DNS filter VPN. consentIntent returns the system consent Intent when the
 * user has not yet allowed a VPN for this app, or null when consent is already granted. The caller
 * launches that Intent and starts the service on approval.
 */
object ImpulsiveVpnController {
    fun consentIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, ImpulsiveVpnService::class.java)
            .setAction(ImpulsiveVpnService.ActionStart)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, ImpulsiveVpnService::class.java)
            .setAction(ImpulsiveVpnService.ActionStop)
        ContextCompat.startForegroundService(context, intent)
    }
}
