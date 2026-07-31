package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.pathshift.RoomPathShiftCycleRepository
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import com.impulsive.app.backend.session.pathshift.PathShiftRecoveryCoordinator
import com.impulsive.app.backend.session.pathshift.PathShiftReviewFinaliser
import com.impulsive.app.backend.session.pathshift.PathShiftWorkScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftRecoverySchedulingInstrumentedTest {
    @Test
    fun futureCycleIsRescheduledWithStableUniqueIdentity() = runBlocking {
        val database = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val dao = database.pathShiftCycleDao()
        dao.clearAll()
        val cycle = pathShiftCycleEntity(windowEnd = 20_000L)
        dao.insertOnce(cycle)
        val scheduled = mutableListOf<String>()
        val scheduler = object : PathShiftWorkScheduler {
            override fun schedule(cycleId: String, finaliseAtMillis: Long): Boolean {
                scheduled += cycleId
                return true
            }
            override fun cancel(cycleId: String) = true
            override fun cancelAll() = true
        }
        val clock = AdaptiveClock { 10_000L }
        val repo = RoomPathShiftCycleRepository(dao)
        val recovery = PathShiftRecoveryCoordinator(
            repo,
            PathShiftReviewFinaliser(
                repo,
                RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
                clock,
            ),
            scheduler,
            clock,
        )
        assertTrue(recovery.recover().rescheduled)
        assertEquals(listOf(cycle.cycleId), scheduled)
        dao.clearAll()
        Unit
    }
}
