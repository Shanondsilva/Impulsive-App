package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftResetDeletionInstrumentedTest {
    @Test
    fun resetClearsCyclesButPreservesPreferencesAndFullDeletionClearsBoth() = runBlocking {
        val database = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val cycles = database.pathShiftCycleDao()
        val preferences = database.adaptivePreferenceDao()
        cycles.clearAll()
        preferences.clearAll()
        preferences.insertForRestore(
            com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity(
                pathShiftEnabled = true,
            ),
        )
        cycles.insertOnce(pathShiftCycleEntity())
        val data = RoomAdaptiveDataRepository(database)
        data.clearPersonalLearning()
        assertNull(cycles.getActive())
        assertTrue(preferences.get()!!.pathShiftEnabled)

        cycles.insertOnce(pathShiftCycleEntity())
        data.clearAllAdaptiveData()
        assertNull(cycles.getActive())
        assertEquals(null, preferences.get())
        preferences.insertDefaults(0L)
        Unit
    }
}
