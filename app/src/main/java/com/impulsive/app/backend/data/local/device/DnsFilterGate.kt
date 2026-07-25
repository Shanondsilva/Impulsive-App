package com.impulsive.app.backend.data.local.device

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.impulsive.app.backend.data.local.preferences.VpnDiagnosticPreferencesDataSource
import com.impulsive.app.backend.domain.model.protection.DnsFilterGateEvaluator
import com.impulsive.app.backend.service.protection.ImpulsiveVpnService

/**
 * Single entry point for the DNS filter pre-enable gate. Reads the live device state from
 * the two checkers and runs the pure evaluator, returning whether the filter can be enabled
 * now and which blockers remain. Call this right before enabling protection, since the
 * active-VPN read assumes our own VPN has not started yet.
 */
class DnsFilterGate(
    private val context: Context,
) {
    private val privateDnsChecker = PrivateDnsChecker(context)
    private val activeVpnChecker = ActiveVpnChecker(context)
    private val vpnDiagnostics = VpnDiagnosticPreferencesDataSource(context)

    fun evaluate(): DnsFilterGateEvaluator.GateResult =
        DnsFilterGateEvaluator.evaluate(
            privateDnsBypassesFilter = privateDnsChecker.bypassesLocalDnsFilter(),
            anotherVpnActive = activeVpnChecker.isAnotherVpnActive() && !ImpulsiveVpnService.isRunning,
            lockdownModeActive = vpnDiagnostics.isLockdownModeActive(),
        )

    fun isProtectionOn(): Boolean = ImpulsiveVpnService.isRunning

    fun privateDnsState(): PrivateDnsChecker.State = privateDnsChecker.read()

    fun privateDnsSettingsIntent() = privateDnsChecker.createPrivateDnsSettingsIntent()

    fun vpnSettingsIntent(): Intent {
        val vpnIntent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (vpnIntent.resolveActivity(context.packageManager) != null) {
            return vpnIntent
        }
        return Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
