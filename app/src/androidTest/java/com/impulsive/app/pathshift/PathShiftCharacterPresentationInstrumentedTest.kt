package com.impulsive.app.pathshift

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.core.util.TimeOfDay
import com.impulsive.app.frontend.pathshift.PathShiftCharacterPresentation
import com.impulsive.app.frontend.pathshift.PathShiftCharacterState
import com.impulsive.app.frontend.pathshift.PathShiftExperienceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShiftCharacterPresentationInstrumentedTest {
    @Test
    fun existingLevelDrivesCalmReducedMotionPresentation() {
        val presentation = PathShiftCharacterPresentation.create(
            currentLevel = 5,
            currentLevelPoints = 42,
            experienceState = PathShiftExperienceState.Active,
            hasPreparedPlan = true,
            timeOfDay = TimeOfDay.Night,
            reducedMotion = true,
        )
        assertEquals(5, presentation.level)
        assertEquals(42, presentation.currentLevelPoints)
        assertEquals(PathShiftCharacterState.PathPrepared, presentation.state)
        assertTrue(presentation.reducedMotion)
        assertFalse(presentation.contentDescription.lowercase().contains("addiction"))
    }
}
