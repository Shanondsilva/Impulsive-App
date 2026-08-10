package com.impulsive.app.backend.domain.model.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Snake is a brand-new task identity (Option A): it inherits nothing from
 * Reflex Override, which stays decodable only for pre-cutover data.
 */
class SnakeTaskCutoverTest {

    @Test
    fun `Snake has its own stable identity`() {
        assertEquals("snake", PsychologyTaskType.Snake.id)
        assertEquals("Snake", PsychologyTaskType.Snake.taskTitle)
    }

    @Test
    fun `Snake and Reflex remain separate identities`() {
        assertNotEquals(PsychologyTaskType.Snake.id, PsychologyTaskType.ReflexOverride.id)
        assertEquals("reflex_override", PsychologyTaskType.ReflexOverride.id)

        val ids = PsychologyTaskType.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `Snake has its own reward definition on the easier game tier`() {
        val snake = PsychologyTaskRewardDefinitions.first {
            it.taskType == PsychologyTaskType.Snake
        }

        assertEquals(10, snake.firstTimeLevelPoints)
        assertEquals(2, snake.repeatLevelPoints)
    }

    @Test
    fun `the legacy Reflex definition survives for restored tasks`() {
        assertNotNull(
            PsychologyTaskRewardDefinitions.firstOrNull {
                it.taskType == PsychologyTaskType.ReflexOverride
            },
        )
    }

    @Test
    fun `Snake counts as a game task`() {
        assertTrue(PsychologyTaskType.Snake.isGameTask())
        // Legacy Reflex stays a game task so restored data still resolves.
        assertTrue(PsychologyTaskType.ReflexOverride.isGameTask())
    }

    @Test
    fun `no active recommendation candidate list offers Reflex`() {
        val source = java.io.File(
            "src/main/java/com/impulsive/app/backend/domain/model/tasks/TaskRewardModels.kt",
        ).readText()

        val recommendation = source
            .substringAfter("fun recommendPsychologyTask(")
            .substringBefore("private fun chooseRecommendedTask(")

        assertTrue(recommendation.contains("PsychologyTaskType.Snake"))
        assertFalse(recommendation.contains("PsychologyTaskType.ReflexOverride"))
    }

    @Test
    fun `the recommendation fallback is Snake`() {
        val source = java.io.File(
            "src/main/java/com/impulsive/app/backend/domain/model/tasks/TaskRewardModels.kt",
        ).readText()

        assertTrue(
            source.contains("candidates.ifEmpty { listOf(PsychologyTaskType.Snake) }"),
        )
    }

    @Test
    fun `a default recommendation can select Snake`() {
        val recommendation = recommendPsychologyTask(
            taskStatuses = emptyList(),
            recentRecommendedTaskTypes = emptyList(),
            currentUrgeIntensity = null,
            currentTriggerType = null,
            currentTriggerSource = null,
            userEnergyState = null,
        )

        assertEquals(PsychologyTaskType.Snake, recommendation.taskType)
        assertTrue(recommendation.reason.isNotBlank())
    }
}
