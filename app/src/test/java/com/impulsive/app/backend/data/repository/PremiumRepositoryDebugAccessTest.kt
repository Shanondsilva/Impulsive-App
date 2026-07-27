package com.impulsive.app.backend.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumRepositoryDebugAccessTest {
    private val repositorySource = File(
        "src/main/java/com/impulsive/app/backend/data/repository/PremiumRepository.kt",
    ).readText()

    @Test
    fun `debug build resolves Plus access without a billing entitlement`() {
        assertTrue(
            resolvePlusAccess(
                isDebugBuild = true,
                realBillingEntitlement = false,
            ),
        )
    }

    @Test
    fun `release build depends on the real billing entitlement`() {
        assertFalse(
            resolvePlusAccess(
                isDebugBuild = false,
                realBillingEntitlement = false,
            ),
        )
        assertTrue(
            resolvePlusAccess(
                isDebugBuild = false,
                realBillingEntitlement = true,
            ),
        )
    }

    @Test
    fun `debug override is runtime only and does not persist a fake entitlement`() {
        val accessFlow = repositorySource.substring(
            repositorySource.indexOf("fun hasFeature("),
            repositorySource.indexOf("suspend fun setEntitlement("),
        )

        assertTrue(accessFlow.contains("resolvePlusAccess("))
        assertTrue(accessFlow.contains("isDebugBuild = BuildConfig.DEBUG"))
        assertFalse(accessFlow.contains("dataSource.setEntitlement"))
        assertFalse(accessFlow.contains("PremiumEntitlement("))
        assertFalse(accessFlow.contains("EntitlementSource.Debug"))
    }
}
