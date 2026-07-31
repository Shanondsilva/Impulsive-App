package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.pathshift.RoomPathShiftCycleRepository
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import com.impulsive.app.backend.session.pathshift.PathShiftFinalisationResult
import com.impulsive.app.backend.session.pathshift.PathShiftReviewFinaliser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftFinalisationInstrumentedTest {
    @Test
    fun dueCycleFinalisesOnceWithoutInventingOutcomes() = runBlocking {
        val database = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        database.pathShiftCycleDao().clearAll()
        val entity = pathShiftCycleEntity()
        database.pathShiftCycleDao().insertOnce(entity)
        val finaliser = PathShiftReviewFinaliser(
            RoomPathShiftCycleRepository(database.pathShiftCycleDao()),
            RoomAdaptiveDecisionRepository(database.adaptiveDecisionDao()),
            AdaptiveClock { entity.forecastWindowEndsAtMillis },
        )
        assertEquals(
            PathShiftFinalisationResult.Finalised,
            finaliser.finalise(entity.cycleId),
        )
        assertEquals(
            PathShiftFinalisationResult.AlreadyFinalised,
            finaliser.finalise(entity.cycleId),
        )
        val result = database.pathShiftCycleDao().getById(entity.cycleId)!!
        assertEquals(0, result.observedProtectedMomentCount)
        assertEquals(0, result.repeatDetectedCount)
        database.pathShiftCycleDao().clearAll()
        Unit
    }
}
