package com.impulsive.app.backend.data.local.device

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Reads the device-wide Private DNS setting. Report-only: it does not change the
 * setting and cannot, since that requires a system-level permission a normal app
 * is not granted. A later gate uses this to decide whether to ask the user to turn
 * Private DNS off before the on-device DNS filter can work.
 */
class PrivateDnsChecker(
    private val context: Context,
) {
    sealed interface State {
        /** Private DNS is off. DNS is plaintext and the local filter can read it. */
        data object Off : State

        /** Automatic mode. DNS may be encrypted to the network resolver, which can bypass the local filter. */
        data object Opportunistic : State

        /** A specific Private DNS host is set. DNS is encrypted to that host and bypasses the local filter. */
        data class Strict(val hostname: String?) : State

        /** Android version is below 9, which has no Private DNS feature, so nothing to bypass. */
        data object Unsupported : State

        /** The setting could not be read. */
        data object Unknown : State
    }

    fun read(): State {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return State.Unsupported
        }
        return runCatching {
            val resolver = context.contentResolver
            val mode = Settings.Global.getString(resolver, MODE_KEY)
            when (mode) {
                MODE_OFF -> State.Off
                MODE_OPPORTUNISTIC -> State.Opportunistic
                MODE_HOSTNAME -> State.Strict(
                    Settings.Global.getString(resolver, SPECIFIER_KEY)?.trim()?.ifBlank { null },
                )
                else -> State.Unknown
            }
        }.getOrDefault(State.Unknown)
    }

    /**
     * Strict mode always bypasses the local filter. Opportunistic may bypass when the
     * network resolver supports encryption, so it is reported as a possible bypass too.
     */
    fun bypassesLocalDnsFilter(): Boolean = when (read()) {
        is State.Strict -> true
        State.Opportunistic -> true
        State.Off, State.Unsupported, State.Unknown -> false
    }

    /**
     * Opens network settings so the user can reach the Private DNS control. There is no
     * public Intent action that targets the Private DNS page directly, so this opens the
     * broader network settings and the gate copy tells the user where to go from there.
     * Falls back to the top-level Settings screen if network settings cannot be opened.
     */
    fun createPrivateDnsSettingsIntent(): Intent {
        val networkIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (networkIntent.resolveActivity(context.packageManager) != null) {
            return networkIntent
        }
        return Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val MODE_KEY = "private_dns_mode"
        const val SPECIFIER_KEY = "private_dns_specifier"
        const val MODE_OFF = "off"
        const val MODE_OPPORTUNISTIC = "opportunistic"
        const val MODE_HOSTNAME = "hostname"
    }
}
