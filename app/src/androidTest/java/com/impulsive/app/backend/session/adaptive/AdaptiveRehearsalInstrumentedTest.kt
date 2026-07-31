package com.impulsive.app.backend.session.adaptive

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.database.SqlCipherDatabaseMigrator
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRehearsalRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanSaveResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveRehearsalInstrumentedTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun lifecycleIsIdempotentAndCreatesNoAdaptiveDecision() = runBlocking {
        val plan = plan()
        val plans = RoomMomentPlanRepository(database.momentPlanDao())
        val rehearsals = RoomMomentPlanRehearsalRepository(
            database.momentPlanRehearsalDao(),
        )
        assertEquals(MomentPlanSaveResult.Applied, plans.create(plan))
        val coordinator = MomentPlanRehearsalCoordinator(
            rehearsals = rehearsals,
            plans = plans,
            clock = AdaptiveClock { 5_000L },
            ids = RehearsalIdSource { UUID.randomUUID().toString() },
        )

        val first = coordinator.startGuided(plan.planId)
        val second = coordinator.startGuided(plan.planId)
        val rehearsalId = checkNotNull(first.session).rehearsal.rehearsalId

        assertTrue(first.created)
        assertEquals(rehearsalId, second.session?.rehearsal?.rehearsalId)
        assertEquals(RehearsalTerminalResult.Applied, coordinator.complete(rehearsalId))
        assertEquals(
            RehearsalTerminalResult.AlreadyCompleted,
            coordinator.complete(rehearsalId),
        )
        assertEquals(0, database.adaptiveDecisionDao().count())
        assertEquals(5_000L, plans.getById(plan.planId)?.rehearsedAtMillis)
    }

    @Test
    fun dismissalCannotBecomeCompletion() = runBlocking {
        val plan = plan()
        val plans = RoomMomentPlanRepository(database.momentPlanDao())
        val rehearsals = RoomMomentPlanRehearsalRepository(
            database.momentPlanRehearsalDao(),
        )
        plans.create(plan)
        val coordinator = MomentPlanRehearsalCoordinator(
            rehearsals,
            plans,
            AdaptiveClock { 5_000L },
        )
        val id = checkNotNull(coordinator.startQuick(plan.planId).session)
            .rehearsal.rehearsalId

        assertEquals(RehearsalTerminalResult.Applied, coordinator.dismiss(id))
        assertEquals(RehearsalTerminalResult.AlreadyDismissed, coordinator.complete(id))
        assertNull(rehearsals.getById(id)?.completedAtMillis)
        assertNotNull(rehearsals.getById(id)?.dismissedAtMillis)
    }

    @Test
    fun deletedPlanDoesNotDeleteHistoricalPractice() = runBlocking {
        val plan = plan()
        val plans = RoomMomentPlanRepository(database.momentPlanDao())
        val rehearsals = RoomMomentPlanRehearsalRepository(
            database.momentPlanRehearsalDao(),
        )
        plans.create(plan)
        val coordinator = MomentPlanRehearsalCoordinator(
            rehearsals,
            plans,
            AdaptiveClock { 5_000L },
        )
        val id = checkNotNull(coordinator.startGuided(plan.planId).session)
            .rehearsal.rehearsalId
        coordinator.complete(id)

        plans.delete(plan.planId)

        assertNotNull(rehearsals.getById(id))
    }

    @Test
    fun currentSchemaOpensWithSqlCipher() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "sqlcipher-schema-ten-${UUID.randomUUID()}.db"
        SqlCipherDatabaseMigrator.ensureSqlCipherLoaded()
        val encrypted = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 7 }))
            .build()
        try {
            encrypted.openHelper.writableDatabase
            assertEquals(11, encrypted.openHelper.writableDatabase.version)
        } finally {
            encrypted.close()
            context.deleteDatabase(name)
        }
    }

    private fun plan() = MomentPlan(
        planId = UUID.randomUUID().toString(),
        title = "Clear morning",
        momentCue = MomentCue.Stress,
        actionText = "Take a short walk",
        futureCueText = "Feel clear tomorrow",
        actionType = MomentPlanActionType.TextOnly,
        actionTarget = null,
        enabled = true,
        preferredForCue = false,
        createdAtMillis = 1_000L,
        updatedAtMillis = 1_000L,
    )
}
