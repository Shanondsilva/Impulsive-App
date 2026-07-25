package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.data.local.preferences.BlockedDomainVersionDataSource
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.domain.model.protection.ExistingBlockedDomainSnapshot
import com.impulsive.app.backend.domain.model.protection.normalizeDomainOrNull
import com.impulsive.app.backend.domain.model.protection.planDefaultBlocklistUpgrade

/**
 * Reconciles the versioned bundled mandatory defaults and manages custom domains.
 * Reconciliation is additive: shipped defaults and custom user entries are never
 * deleted, while a custom entry that becomes a shipped default is promoted in place.
 */
class BlockedDomainRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).blockedDomainDao()
    private val assetLoader = DefaultBlocklistAssetLoader(appContext)
    private val versionDataSource = BlockedDomainVersionDataSource(appContext)

    suspend fun ensureSeeded() {
        val asset = assetLoader.load()
        val storedVersion = versionDataSource.readAppliedVersion()

        if (asset.version <= storedVersion) {
            return
        }

        val existing = dao.getAll().map { entity ->
            ExistingBlockedDomainSnapshot(
                domain = entity.domain,
                isDefault = entity.isDefault,
                addedByUser = entity.addedByUser,
            )
        }
        val plan = planDefaultBlocklistUpgrade(
            storedVersion = storedVersion,
            asset = asset,
            existing = existing,
        )
        val nowMillis = System.currentTimeMillis()

        plan.entriesToInsert.forEach { entry ->
            dao.insert(
                BlockedDomainEntity(
                    domain = entry.domain,
                    category = entry.category,
                    isDefault = true,
                    addedByUser = false,
                    createdAtMillis = nowMillis,
                ),
            )
        }

        plan.entriesToPromote.forEach { entry ->
            dao.promoteToDefault(
                domain = entry.domain,
                category = entry.category,
            )
        }

        plan.versionToPersist?.let { version ->
            versionDataSource.writeAppliedVersion(version)
        }
    }

    suspend fun loadBlockedDomains(): Set<String> =
        dao.getAllDomains().mapNotNull(::normalizeDomainOrNull).toSet()

    suspend fun addUserDomain(domain: String) {
        val normalized = normalizeDomainOrNull(domain) ?: return

        dao.insert(
            BlockedDomainEntity(
                domain = normalized,
                category = "custom",
                isDefault = false,
                addedByUser = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        RestoreSnapshotRefreshScheduler.request(appContext)
    }
}
