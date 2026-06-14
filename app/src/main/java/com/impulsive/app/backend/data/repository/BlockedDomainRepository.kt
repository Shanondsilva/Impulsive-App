package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.domain.model.protection.DefaultBlockedDomains

/**
 * Reads and seeds the on-device blocklist. Seeding runs only when the table is empty, so it is
 * safe to call on every enable. The unique index on domain means duplicate inserts are ignored.
 */
class BlockedDomainRepository(
    context: Context,
) {
    private val dao = AppDatabase.getInstance(context).blockedDomainDao()

    suspend fun ensureSeeded() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        val entities = DefaultBlockedDomains.starter.map { entry ->
            BlockedDomainEntity(
                domain = entry.domain,
                category = entry.category,
                isDefault = true,
                addedByUser = false,
                createdAtMillis = now,
            )
        }
        dao.insertAll(entities)
    }

    suspend fun loadBlockedDomains(): Set<String> =
        dao.getAllDomains().toSet()

    suspend fun addUserDomain(domain: String) {
        val normalized = domain.trim().lowercase().removeSuffix(".")
        if (normalized.isEmpty()) return
        dao.insert(
            BlockedDomainEntity(
                domain = normalized,
                category = "custom",
                isDefault = false,
                addedByUser = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
    }
}
