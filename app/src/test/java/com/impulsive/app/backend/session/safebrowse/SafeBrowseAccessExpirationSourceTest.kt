package com.impulsive.app.backend.session.safebrowse

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the authoritative Safe Browse expiration contract:
 *
 * - persistence succeeds before exhaustion is published;
 * - persistence failure returns before authoritative success state;
 * - exhaustion requires zero balance and no active lease;
 * - checkpoint exhaustion does not perform a second endUsage deduction;
 * - AccessExpired remains de-duplicated.
 */
class SafeBrowseAccessExpirationSourceTest {

    private val source =
        File(
            "src/main/java/com/impulsive/app/backend/session/safebrowse/SafeBrowseAccessViewModel.kt",
        ).readText()

    private fun blockBetween(
        startMarker: String,
        endMarker: String,
    ): String {
        val start =
            source.indexOf(
                startMarker,
            )

        assertTrue(
            "Missing start marker: $startMarker",
            start >= 0,
        )

        val end =
            source.indexOf(
                endMarker,
                start +
                    startMarker.length,
            )

        assertTrue(
            "Missing end marker: $endMarker",
            end > start,
        )

        return source.substring(
            start,
            end,
        )
    }

    private val expireUsageLockedBlock:
        String by lazy {
        blockBetween(
            startMarker =
                "private suspend fun expireUsageLocked()",
            endMarker =
                "private suspend fun applyPassEntitlementLocked(",
        )
    }

    private val checkpointBlock:
        String by lazy {
        blockBetween(
            startMarker =
                "val snapshot = runCatching { repository.checkpointUsage() }",
            endMarker =
                "if (shouldStop) {",
        )
    }

    private val emitExpiredOnceBlock:
        String by lazy {
        blockBetween(
            startMarker =
                "private fun emitAccessExpiredOnceLocked()",
            endMarker =
                "private fun applyAuthoritativeLedgerStateLocked()",
        )
    }

    @Test
    fun endUsagePersistencePrecedesUsageActiveClearingAndAccessExpiredEmission() {
        val endUsageIndex =
            expireUsageLockedBlock.indexOf(
                "val snapshot = runCatching { repository.endUsage() }",
            )

        val authoritativeSnapshotIndex =
            expireUsageLockedBlock.indexOf(
                "authoritativeLedgerSnapshot = snapshot",
                endUsageIndex,
            )

        val usageActiveFalseIndex =
            expireUsageLockedBlock.indexOf(
                "usageActive = false",
                authoritativeSnapshotIndex,
            )

        val stopTickerIndex =
            expireUsageLockedBlock.indexOf(
                "stopTickerLocked()",
                usageActiveFalseIndex,
            )

        val expiredIndex =
            expireUsageLockedBlock.indexOf(
                "emitAccessExpiredOnceLocked()",
                stopTickerIndex,
            )

        assertTrue(
            "repository.endUsage() result is not captured",
            endUsageIndex >= 0,
        )

        assertTrue(
            authoritativeSnapshotIndex >
                endUsageIndex,
        )

        assertTrue(
            usageActiveFalseIndex >
                authoritativeSnapshotIndex,
        )

        assertTrue(
            stopTickerIndex >
                usageActiveFalseIndex,
        )

        assertTrue(
            expiredIndex >
                stopTickerIndex,
        )
    }

    @Test
    fun failurePathReturnsBeforeAnyAuthoritativeSuccessStateIsPublished() {
        val endUsageIndex =
            expireUsageLockedBlock.indexOf(
                "val snapshot = runCatching { repository.endUsage() }",
            )

        val failureHandlerIndex =
            expireUsageLockedBlock.indexOf(
                "handlePersistenceFailureLocked(error)",
                endUsageIndex,
            )

        val returnAfterFailureIndex =
            expireUsageLockedBlock.indexOf(
                "return true",
                failureHandlerIndex,
            )

        val authoritativeSnapshotIndex =
            expireUsageLockedBlock.indexOf(
                "authoritativeLedgerSnapshot = snapshot",
                endUsageIndex,
            )

        assertTrue(
            endUsageIndex >= 0,
        )

        assertTrue(
            failureHandlerIndex >
                endUsageIndex,
        )

        assertTrue(
            returnAfterFailureIndex in
                failureHandlerIndex until
                authoritativeSnapshotIndex,
        )
    }

    @Test
    fun accessExpiredIsGatedOnAGenuinelyExhaustedSnapshotAndEmittedOnce() {
        val exhaustionGuardIndex =
            expireUsageLockedBlock.indexOf(
                "if (snapshot.remainingMillis <= 0L && !snapshot.leaseActive)",
            )

        val lockedIndex =
            expireUsageLockedBlock.indexOf(
                "_accessState.value = SafeBrowseAccessState.Locked",
                exhaustionGuardIndex,
            )

        val expiredIndex =
            expireUsageLockedBlock.indexOf(
                "emitAccessExpiredOnceLocked()",
                lockedIndex,
            )

        assertTrue(
            exhaustionGuardIndex >= 0,
        )

        assertTrue(
            lockedIndex >
                exhaustionGuardIndex,
        )

        assertTrue(
            expiredIndex >
                lockedIndex,
        )

        assertTrue(
            emitExpiredOnceBlock.contains(
                "if (expirationEffectEmitted)",
            ),
        )

        assertTrue(
            emitExpiredOnceBlock.contains(
                "expirationEffectEmitted = true",
            ),
        )

        assertTrue(
            emitExpiredOnceBlock.contains(
                "_effects.tryEmit(SafeBrowseAccessEffect.AccessExpired)",
            ),
        )
    }

    @Test
    fun endUsageResultIsCapturedNeverIgnored() {
        assertTrue(
            expireUsageLockedBlock.contains(
                "val snapshot = runCatching { repository.endUsage() }",
            ),
        )

        assertTrue(
            expireUsageLockedBlock.contains(
                "authoritativeLedgerSnapshot = snapshot",
            ),
        )
    }

    @Test
    fun checkpointExhaustedBranchNeverCallsExpireUsageLockedAgain() {
        val codeOnly =
            checkpointBlock
                .lineSequence()
                .filterNot { line ->
                    line
                        .trim()
                        .startsWith("//")
                }
                .joinToString("\n")

        assertFalse(
            codeOnly.contains(
                "expireUsageLocked()",
            ),
        )

        assertTrue(
            checkpointBlock.contains(
                "authoritativeLedgerSnapshot = snapshot",
            ),
        )

        assertTrue(
            checkpointBlock.contains(
                "applyAuthoritativeLedgerStateLocked()",
            ),
        )

        assertTrue(
            checkpointBlock.contains(
                "if (snapshot.remainingMillis <= 0L)",
            ),
        )

        assertTrue(
            checkpointBlock.contains(
                "emitAccessExpiredOnceLocked()",
            ),
        )
    }
}
