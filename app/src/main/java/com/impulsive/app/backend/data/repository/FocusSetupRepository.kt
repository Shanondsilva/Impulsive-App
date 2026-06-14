package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.FocusSetupDataSource
import kotlinx.coroutines.flow.Flow

class FocusSetupRepository(context: Context) {
    private val dataSource = FocusSetupDataSource(context)

    /** Null = never configured, fall back to the urge-protection list. */
    val configuredBlockedPackages: Flow<Set<String>?> = dataSource.configuredBlockedPackages

    suspend fun setBlockedPackages(packageNames: Set<String>) {
        dataSource.setBlockedPackages(packageNames)
    }
}
