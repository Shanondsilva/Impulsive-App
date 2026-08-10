package com.impulsive.app.backend.session.adaptive

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the protected Support Cycle's dependency wiring.
 *
 * The superseded attempt ladder read decision history to pick a cycle duration.
 * A fixed duration needs no such query, so this proves the history dependency
 * did not survive as dead wiring that a later change could quietly revive.
 *
 * The assertions are scoped to this one factory: Room decision history remains
 * entirely legitimate elsewhere in the app.
 */
class AdaptiveSupportCycleDependenciesSourceTest {
    @Test
    fun coordinatorWiringDoesNotQueryDecisionHistoryForCycleBudgeting() {
        val wiring = source()

        assertFalse(
            "cycle wiring must not build a decision repository for budgeting",
            wiring.contains("RoomAdaptiveDecisionRepository"),
        )
        assertFalse(
            "the attempt-ladder resolver must be gone",
            wiring.contains("AdaptiveSupportCycleBudgetResolver"),
        )
        assertFalse(
            "no caller-supplied budget resolver may remain",
            wiring.contains("initialBudgetResolver"),
        )
        assertFalse(
            "no database handle is needed for a fixed duration",
            wiring.contains("AppDatabase"),
        )
    }

    @Test
    fun coordinatorWiringStillBuildsTheSupportCycleStoreNormally() {
        val wiring = source()

        assertTrue(wiring.contains("DataStoreAdaptiveSupportCycleRepository"))
        assertTrue(wiring.contains("fun coordinator("))
        assertTrue(wiring.contains("repository = repository(context)"))
    }

    private fun source(): String {
        val path = "app/src/main/java/com/impulsive/app/backend/session/adaptive/" +
            "AdaptiveSupportCycleDependencies.kt"
        val file = listOf(File(path), File("../$path")).firstOrNull(File::exists)
            ?: error("Source not found: $path")
        return file.readText()
    }
}
