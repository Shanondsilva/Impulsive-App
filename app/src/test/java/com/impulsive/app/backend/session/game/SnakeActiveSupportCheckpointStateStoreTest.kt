package com.impulsive.app.backend.session.game

import androidx.lifecycle.SavedStateHandle
import com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeActiveSupportCheckpointStateStoreTest {

    @Test
    fun `a matching Snake checkpoint saves and restores`() {
        val handle = SavedStateHandle()
        val store = SnakeActiveSupportCheckpointStateStore(handle)

        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        assertEquals(35_000L, store.restore(snakeLaunch()))
    }

    @Test
    fun `a checkpoint survives recreation of the saved state handle`() {
        val handle = SavedStateHandle()
        SnakeActiveSupportCheckpointStateStore(handle)
            .save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        // Simulate process recreation: rebuild the handle from its contents.
        val recreated = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })

        assertEquals(
            35_000L,
            SnakeActiveSupportCheckpointStateStore(recreated).restore(snakeLaunch()),
        )
    }

    @Test
    fun `standalone launches never carry a checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())
        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        assertNull(store.restore(RecoveryGameLaunchContext.Standalone))
    }

    @Test
    fun `a different cycle does not inherit the checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())
        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        assertNull(store.restore(snakeLaunch(cycleId = "other-cycle")))
    }

    @Test
    fun `a different decision does not inherit the checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())
        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        assertNull(store.restore(snakeLaunch(decisionId = "other-decision")))
    }

    @Test
    fun `a non-Snake launch cannot read the checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())
        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        assertNull(store.restore(snakeLaunch(gameType = ScoreGameType.ReflexOverride)))
    }

    @Test
    fun `a non-Snake launch cannot write a checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())

        assertThrows(IllegalArgumentException::class.java) {
            store.save(
                launch = snakeLaunch(gameType = ScoreGameType.RhythmTiles),
                consumedSupportMillis = 1_000L,
            )
        }
    }

    @Test
    fun `a negative consumed duration is rejected`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())

        assertThrows(IllegalArgumentException::class.java) {
            store.save(launch = snakeLaunch(), consumedSupportMillis = -1L)
        }
    }

    @Test
    fun `clear removes the checkpoint`() {
        val store = SnakeActiveSupportCheckpointStateStore(SavedStateHandle())
        store.save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        store.clear()

        assertNull(store.restore(snakeLaunch()))
    }

    @Test
    fun `a version mismatch is rejected`() {
        val handle = SavedStateHandle()
        handle[SnakeActiveSupportCheckpointStateStore.CheckpointKey] =
            SnakeActiveSupportCheckpoint(
                version = SnakeActiveSupportCheckpointStateStore.CurrentVersion + 1,
                cycleId = "cycle-1",
                decisionId = "decision-1",
                gameTypeId = ScoreGameType.Snake.id,
                consumedSupportMillis = 35_000L,
            )

        assertNull(SnakeActiveSupportCheckpointStateStore(handle).restore(snakeLaunch()))
    }

    @Test
    fun `the stored checkpoint holds no gameplay state`() {
        val handle = SavedStateHandle()
        SnakeActiveSupportCheckpointStateStore(handle)
            .save(launch = snakeLaunch(), consumedSupportMillis = 35_000L)

        val stored = handle.get<SnakeActiveSupportCheckpoint>(
            SnakeActiveSupportCheckpointStateStore.CheckpointKey,
        )!!

        // The Compose compiler adds a synthetic $stable field; ignore synthetics.
        val declaredFields = SnakeActiveSupportCheckpoint::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
        val fieldNames = declaredFields.map { it.name }
        val fieldTypes = declaredFields.map { it.type.simpleName }

        assertEquals(
            listOf(
                "version",
                "cycleId",
                "decisionId",
                "gameTypeId",
                "consumedSupportMillis",
            ),
            fieldNames,
        )
        assertTrue(
            "only scalars belong in the checkpoint",
            fieldTypes.all { it in setOf("int", "long", "String") },
        )
        assertTrue(
            fieldNames.none { name ->
                listOf("snake", "cell", "food", "direction", "score", "fruit").any {
                    name.contains(it, ignoreCase = true)
                }
            },
        )
        assertEquals(35_000L, stored.consumedSupportMillis)
    }

    private fun snakeLaunch(
        cycleId: String = "cycle-1",
        decisionId: String = "decision-1",
        gameType: ScoreGameType = ScoreGameType.Snake,
    ) = RecoveryGameLaunchContext.SupportCycle(
        cycleId = cycleId,
        decisionId = decisionId,
        gameType = gameType,
        maxDurationMillis = 60_000L,
    )
}
