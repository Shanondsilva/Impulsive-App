package com.impulsive.app.frontend.pathshift

import com.impulsive.app.core.util.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathShiftCharacterPresentationTest {
    @Test
    fun `character level and LP derive unchanged from existing reward state`() {
        val presentation = create(
            level = 4,
            points = 73,
            experience = PathShiftExperienceState.Active,
        )
        assertEquals(4, presentation.level)
        assertEquals(73, presentation.currentLevelPoints)
    }

    @Test
    fun `experience maps to calm participation states`() {
        assertEquals(
            PathShiftCharacterState.NotEnoughHistory,
            create(experience = PathShiftExperienceState.InsufficientHistory).state,
        )
        assertEquals(
            PathShiftCharacterState.WalkingCurrentPath,
            create(experience = PathShiftExperienceState.Active).state,
        )
        assertEquals(
            PathShiftCharacterState.PathPrepared,
            create(
                experience = PathShiftExperienceState.Active,
                prepared = true,
            ).state,
        )
        assertEquals(
            PathShiftCharacterState.ReviewingPath,
            create(experience = PathShiftExperienceState.FinalisedReview).state,
        )
    }

    @Test
    fun `reduced motion is presentation only and remains usable`() {
        val animated = create(reducedMotion = false)
        val reduced = create(reducedMotion = true)
        assertFalse(animated.reducedMotion)
        assertTrue(reduced.reducedMotion)
        assertEquals(animated.level, reduced.level)
        assertEquals(animated.currentLevelPoints, reduced.currentLevelPoints)
    }

    @Test
    fun `TalkBack description does not interpret health or forecast severity`() {
        val description = create().contentDescription.lowercase()
        assertTrue(description.contains("participation"))
        assertTrue(description.contains("not health status"))
        assertTrue(description.contains("not") && description.contains("forecast severity"))
        assertFalse(description.contains("addiction level"))
        assertFalse(description.contains("recovery score"))
    }

    private fun create(
        level: Int = 2,
        points: Int = 15,
        experience: PathShiftExperienceState = PathShiftExperienceState.ForecastReady,
        prepared: Boolean = false,
        reducedMotion: Boolean = false,
    ): PathShiftCharacterPresentation = PathShiftCharacterPresentation.create(
        currentLevel = level,
        currentLevelPoints = points,
        experienceState = experience,
        hasPreparedPlan = prepared,
        timeOfDay = TimeOfDay.Morning,
        reducedMotion = reducedMotion,
    )
}
