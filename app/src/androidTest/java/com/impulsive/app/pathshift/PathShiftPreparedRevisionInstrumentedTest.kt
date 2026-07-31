package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftPreparedRevisionInstrumentedTest {
    @Test
    fun exactOpaquePlanAndRevisionAreStoredTogether() = runBlocking {
        val dao = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).pathShiftCycleDao()
        dao.clearAll()
        val cycle = pathShiftCycleEntity()
        dao.insertOnce(cycle)
        val plan = "22222222-2222-4222-8222-222222222222"
        val revision = "33333333-3333-4333-8333-333333333333"
        assertEquals(1, dao.attachPreparedPlan(cycle.cycleId, plan, revision, 150L))
        val stored = dao.getById(cycle.cycleId)!!
        assertEquals(plan, stored.preparedPlanId)
        assertEquals(revision, stored.preparedPlanContentRevisionId)
        dao.clearAll()
        Unit
    }
}
