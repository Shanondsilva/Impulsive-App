package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.ProtectionSetupPreferencesDataSource
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import kotlinx.coroutines.flow.Flow

class ProtectionSetupRepository(
    context: Context,
) {
    private val dataSource = ProtectionSetupPreferencesDataSource(context)

    val state: Flow<ProtectionSetupState> = dataSource.state

    suspend fun setUsageAccessEnabled(enabled: Boolean) {
        dataSource.setUsageAccessEnabled(enabled)
    }

    suspend fun setSelectedBlockedAppPackageNames(packageNames: Set<String>) {
        dataSource.setSelectedBlockedAppPackageNames(packageNames)
    }

    suspend fun setWebsiteProtectionEnabled(enabled: Boolean) {
        dataSource.setWebsiteProtectionEnabled(enabled)
    }

    suspend fun setWebsiteProtectionAlwaysOn(enabled: Boolean) {
        dataSource.setWebsiteProtectionAlwaysOn(enabled)
    }

    suspend fun setInterruptionPermissionEnabled(enabled: Boolean) {
        dataSource.setInterruptionPermissionEnabled(enabled)
    }

    suspend fun setBackgroundActivityEnabled(enabled: Boolean) {
        dataSource.setBackgroundActivityEnabled(enabled)
    }

    suspend fun setUninstallProtectionEnabled(enabled: Boolean) {
        dataSource.setUninstallProtectionEnabled(enabled)
    }

    suspend fun setNotificationPermissionEnabled(enabled: Boolean) {
        dataSource.setNotificationPermissionEnabled(enabled)
    }

    suspend fun markSkipped(item: ProtectionSetupItem) {
        dataSource.markSkipped(item)
    }

    suspend fun clearSkipped(item: ProtectionSetupItem) {
        dataSource.clearSkipped(item)
    }

    suspend fun clear() {
        dataSource.clear()
    }
}
