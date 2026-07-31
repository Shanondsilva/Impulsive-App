package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.session.adaptive.RoomAdaptiveRetentionStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftRetentionInstrumentedTest {
    @Test
    fun expiredFinalisedCycleIsDeletedWhileActiveCycleIsPreserved() = runBlocking {
        val database = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val dao = database.pathShiftCycleDao()
        dao.clearAll()
        val finalised = pathShiftCycleEntity(
            id = "11111111-1111-4111-8111-111111111111",
            status = "Finalised",
            finalisedAt = 500L,
        )
        val active = pathShiftCycleEntity(
            id = "22222222-2222-4222-8222-222222222222",
            createdAt = 600L,
            windowStart = 700L,
            windowEnd = 1_500L,
        )
        dao.insertForRestore(finalised)
        dao.insertForRestore(active)
        val batch = RoomAdaptiveRetentionStore(database).prune(
            cutoffMillis = 1_000L,
            protectedDecisionIds = emptySet(),
            limit = 100,
        )
        assertEquals(listOf(finalised.cycleId), batch.pathShiftCycleIds)
        assertNull(dao.getById(finalised.cycleId))
        assertNotNull(dao.getById(active.cycleId))
        dao.clearAll()
        Unit
    }
}
