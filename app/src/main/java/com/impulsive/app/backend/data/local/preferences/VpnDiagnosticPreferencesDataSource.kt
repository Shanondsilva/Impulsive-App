package com.impulsive.app.backend.data.local.preferences

import android.content.Context

class VpnDiagnosticPreferencesDataSource(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PreferencesName,
            Context.MODE_PRIVATE,
        )

    fun isLockdownModeActive(): Boolean =
        preferences.getBoolean(
            LockdownModeActiveKey,
            false,
        )

    fun setLockdownModeActive(
        active: Boolean,
    ) {
        preferences
            .edit()
            .putBoolean(
                LockdownModeActiveKey,
                active,
            )
            .apply()
    }

    private companion object {
        const val PreferencesName =
            "vpn_diagnostics"

        const val LockdownModeActiveKey =
            "lockdown_mode_active"
    }
}