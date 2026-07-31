package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.preferences.TaskRewardDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftLpNonInterferenceInstrumentedTest {
    @Test
    fun cycleLifecycleDoesNotTouchExistingLPStore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rewards = TaskRewardDataSource(context)
        val before = rewards.storeState.first()
        val dao = AppDatabase.getInstance(context).pathShiftCycleDao()
        dao.clearAll()
        val cycle = pathShiftCycleEntity()
        dao.insertOnce(cycle)
        dao.cancelOnce(cycle.cycleId, 250L)
        val after = rewards.storeState.first()
        assertEquals(before.currentLevel, after.currentLevel)
        assertEquals(before.currentLevelPoints, after.currentLevelPoints)
        dao.clearAll()
        Unit
    }
}
