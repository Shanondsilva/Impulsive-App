package com.impulsive.app.backend.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.domain.model.store.GameStoreCatalog
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Snake result can survive process death and be replayed into the store, so
 * one score session must never award points or consume a rented play twice.
 */
class SnakeGameStorePlayIdempotencyTest {

    private val points = GameStoreCatalog.TwoWinStreakControlPoints

    @Test
    fun snakeReplacedReflexInTheActiveCatalog() {
        val snake = GameStoreCatalog.byId("SNAKE")

        assertEquals("Snake", snake?.displayName)
        assertTrue(snake?.defaultOwned == true)
        assertNull(GameStoreCatalog.byId("REFLEX_OVERRIDE"))
    }

    @Test
    fun aSessionIsRecordedOnlyOnce() = runBlocking {
        withStore { source ->
            assertTrue(source.recordPlayOnce("SNAKE", 1001L, won = true, points))
            assertFalse(source.recordPlayOnce("SNAKE", 1001L, won = true, points))
        }
    }

    @Test
    fun twoUniqueWinsAwardTheStreakBonusExactlyOnce() = runBlocking {
        withStore { source ->
            source.recordPlayOnce("SNAKE", 1001L, won = true, points)

            // One win is only half a streak.
            assertEquals(0, source.spendablePoints.first())

            source.recordPlayOnce("SNAKE", 1002L, won = true, points)
            assertEquals(points, source.spendablePoints.first())

            // Replaying the same session must not award again.
            source.recordPlayOnce("SNAKE", 1002L, won = true, points)
            assertEquals(points, source.spendablePoints.first())
            assertEquals(points, source.lifetimePoints.first())
        }
    }

    @Test
    fun aLossResetsTheStreakWithoutAwarding() = runBlocking {
        withStore { source ->
            source.recordPlayOnce("SNAKE", 2001L, won = true, points)
            source.recordPlayOnce("SNAKE", 2002L, won = false, points)

            // The streak restarted, so the next single win awards nothing.
            source.recordPlayOnce("SNAKE", 2003L, won = true, points)
            assertEquals(0, source.spendablePoints.first())

            // A duplicate loss cannot reset anything a second time.
            assertFalse(source.recordPlayOnce("SNAKE", 2002L, won = false, points))

            source.recordPlayOnce("SNAKE", 2004L, won = true, points)
            assertEquals(points, source.spendablePoints.first())
        }
    }

    @Test
    fun anUnknownGameIsRejected() = runBlocking {
        withStore { source ->
            assertFalse(source.recordPlayOnce("REFLEX_OVERRIDE", 3001L, won = true, points))
        }
    }

    @Test
    fun receiptsSurviveDataStoreRecreation() = runBlocking {
        val directory = Files.createTempDirectory("snake-store-idempotency").toFile()
        val file = File(directory, "game_store_prefs.preferences_pb")

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = GameStorePreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file }),
            )

            assertTrue(first.recordPlayOnce("SNAKE", 4242L, won = true, points))
        } finally {
            firstScope.cancel()
            firstScope.coroutineContext[Job]?.join()
        }

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val recreated = GameStorePreferencesDataSource(
                PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }),
            )

            assertFalse(
                "the receipt must outlive the process",
                recreated.recordPlayOnce("SNAKE", 4242L, won = true, points),
            )
            assertTrue(
                "and must remain independently confirmable",
                recreated.isPlayRecorded("SNAKE", 4242L),
            )
        } finally {
            secondScope.cancel()
            secondScope.coroutineContext[Job]?.join()
        }
    }

    @Test
    fun receiptIsNotPresentBeforeRecording() = runBlocking {
        withStore { source ->
            assertFalse(source.isPlayRecorded("SNAKE", 5001L))
        }
    }

    @Test
    fun successfulRecordCanBeIndependentlyConfirmed() = runBlocking {
        withStore { source ->
            assertTrue(source.recordPlayOnce("SNAKE", 5002L, won = true, points))
            assertTrue(source.isPlayRecorded("SNAKE", 5002L))
        }
    }

    @Test
    fun duplicateRecordIsStillConfirmedDurable() = runBlocking {
        withStore { source ->
            source.recordPlayOnce("SNAKE", 5003L, won = true, points)

            // false means "already recorded" here, not "not recorded".
            assertFalse(source.recordPlayOnce("SNAKE", 5003L, won = true, points))
            assertTrue(source.isPlayRecorded("SNAKE", 5003L))
        }
    }

    @Test
    fun unknownGameCannotBeConfirmed() = runBlocking {
        withStore { source ->
            assertFalse(source.isPlayRecorded("REFLEX_OVERRIDE", 5004L))
        }
    }

    private suspend fun withStore(
        block: suspend (GameStorePreferencesDataSource) -> Unit,
    ) {
        val directory = Files.createTempDirectory("snake-store").toFile()
        val file = File(directory, "game_store_prefs.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            block(
                GameStorePreferencesDataSource(
                    PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
                ),
            )
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
    }
}
