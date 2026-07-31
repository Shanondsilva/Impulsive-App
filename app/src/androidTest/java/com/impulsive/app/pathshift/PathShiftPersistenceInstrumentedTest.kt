package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftPersistenceInstrumentedTest {
    @Test
    fun insertObserveAttachFinaliseAndCancelAreExactOnce() = runBlocking {
        val dao = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).pathShiftCycleDao()
        dao.clearAll()
        val active = pathShiftCycleEntity()
        assertTrue(dao.insertOnce(active) != -1L)
        assertEquals(active, dao.getById(active.cycleId))
        assertEquals(active.cycleId, dao.observeActive().filterNotNull().first().cycleId)
        assertEquals(
            1,
            dao.attachPreparedPlan(
                active.cycleId,
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333",
                160L,
            ),
        )
        assertEquals(0, dao.finaliseOnce(active.cycleId, 299L, 0, 0, 0, 0, 0, 0, 0))
        assertEquals(1, dao.finaliseOnce(active.cycleId, 300L, 0, 0, 0, 0, 0, 0, 0))
        assertEquals(0, dao.finaliseOnce(active.cycleId, 301L, 0, 0, 0, 0, 0, 0, 0))
        assertFalse(dao.getById(active.cycleId)?.status == "Active")
        assertEquals(0, dao.cancelOnce(active.cycleId, 302L))
        dao.clearAll()
        Unit
    }
}
