package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.domain.model.safebrowse.SafeBrowsePolicySnapshot

/**
 * Repository boundary for loading the local Safe Browse navigation policy.
 *
 * This deliberately reuses the existing Website Protection blocked-domain
 * source so adult-domain policy cannot drift between Website Protection and
 * Safe Browse.
 */
class SafeBrowsePolicyRepository(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val blockedDomainRepository = BlockedDomainRepository(appContext)

    suspend fun loadSnapshot(): SafeBrowsePolicySnapshot {
        /*
         * Ensure the bundled mandatory defaults have been reconciled before
         * the Safe Browse policy reads them.
         */
        blockedDomainRepository.ensureSeeded()

        return SafeBrowsePolicySnapshot.from(
            blockedDomainRepository.loadBlockedDomains(),
        )
    }
}
