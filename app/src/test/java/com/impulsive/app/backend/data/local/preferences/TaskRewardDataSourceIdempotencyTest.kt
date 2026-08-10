package com.impulsive.app.backend.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.impulsive.app.backend.domain.model.release.calculateReleasePlan
import com.impulsive.app.backend.domain.model.tasks.PsychologyTaskType
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRewardDataSourceIdempotencyTest {
    @Test
    fun duplicateCompletionTokenSurvivesDataStoreRecreationWithoutSecondReward() =
        runBlocking {
            val directory = Files.createTempDirectory("task-reward-idempotency").toFile()
            val file = File(directory, "task_rewards.preferences_pb")

            val now = LocalDateTime.now().withNano(0)

            val releasePlan = calculateReleasePlan(
                selectedDailyUrgeCount = 3,
                now = now,
            )

            val completionToken = "REFLEX_OVERRIDE:123456"

            val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            try {
                val firstSource = TaskRewardDataSource(
                    PreferenceDataStoreFactory.create(
                        scope = firstScope,
                        produceFile = { file },
                    ),
                )

                val firstResult = firstSource.completeTask(
                    taskType = PsychologyTaskType.ReflexOverride,
                    releasePlan = releasePlan,
                    now = now,
                    gameType = "REFLEX_OVERRIDE",
                    score = 500,
                    durationSec = 60,
                    validCompletion = true,
                    completionToken = completionToken,
                )

                firstScope.cancel()
                firstScope.coroutineContext[Job]?.join()

                val recreatedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

                try {
                    val recreatedSource = TaskRewardDataSource(
                        PreferenceDataStoreFactory.create(
                            scope = recreatedScope,
                            produceFile = { file },
                        ),
                    )

                    val duplicateResult = recreatedSource.completeTask(
                        taskType = PsychologyTaskType.ReflexOverride,
                        releasePlan = releasePlan,
                        now = now.plusSeconds(1),
                        gameType = "REFLEX_OVERRIDE",
                        score = 500,
                        durationSec = 60,
                        validCompletion = true,
                        completionToken = completionToken,
                    )

                    val state = recreatedSource.storeState.first()

                    assertEquals(firstResult, duplicateResult)

                    assertEquals(
                        1,
                        state.records.getValue(PsychologyTaskType.ReflexOverride).completedTodayCount,
                    )

                    assertEquals(firstResult.currentLevel, state.currentLevel)

                    assertEquals(firstResult.currentLevelPoints, state.currentLevelPoints)
                } finally {
                    recreatedScope.cancel()
                    recreatedScope.coroutineContext[Job]?.join()
                }
            } finally {
                firstScope.cancel()
                directory.deleteRecursively()
            }
        }

    @Test
    fun differentCompletionTokenRemainsALegitimateNewCompletion() =
        runBlocking {
            val directory = Files.createTempDirectory("task-reward-distinct").toFile()

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            try {
                val source = TaskRewardDataSource(
                    PreferenceDataStoreFactory.create(
                        scope = scope,
                        produceFile = { File(directory, "task_rewards.preferences_pb") },
                    ),
                )

                val now = LocalDateTime.now().withNano(0)

                val releasePlan = calculateReleasePlan(
                    selectedDailyUrgeCount = 3,
                    now = now,
                )

                source.completeTask(
                    taskType = PsychologyTaskType.RhythmTiles,
                    releasePlan = releasePlan,
                    now = now,
                    gameType = "RHYTHM_TILES",
                    score = 600,
                    durationSec = 60,
                    validCompletion = true,
                    completionToken = "RHYTHM_TILES:100",
                )

                source.completeTask(
                    taskType = PsychologyTaskType.RhythmTiles,
                    releasePlan = releasePlan,
                    now = now.plusSeconds(1),
                    gameType = "RHYTHM_TILES",
                    score = 600,
                    durationSec = 60,
                    validCompletion = true,
                    completionToken = "RHYTHM_TILES:101",
                )

                val state = source.storeState.first()

                assertEquals(
                    2,
                    state.records.getValue(PsychologyTaskType.RhythmTiles).completedTodayCount,
                )

                assertTrue(state.currentLevelPoints > 0)
            } finally {
                scope.cancel()
                scope.coroutineContext[Job]?.join()
                directory.deleteRecursively()
            }
        }

    @Test
    fun snakeHasIndependentFirstTimeRewardAfterLegacyReflexCompletion() = runBlocking {
        val directory = Files.createTempDirectory("snake-option-a").toFile()
        val file = File(directory, "task_rewards.preferences_pb")

        val dayOne = LocalDateTime.now().withNano(0)
        val dayTwo = dayOne.plusDays(1)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            val source = TaskRewardDataSource(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
            )

            // Day 1: the retired Reflex task is completed for the first time.
            source.completeTask(
                taskType = PsychologyTaskType.ReflexOverride,
                releasePlan = calculateReleasePlan(selectedDailyUrgeCount = 3, now = dayOne),
                now = dayOne,
                gameType = "REFLEX_OVERRIDE",
                score = 500,
                durationSec = 60,
                validCompletion = true,
                completionToken = "REFLEX_OVERRIDE:900001",
            )

            assertTrue(source.storeState.first().records[PsychologyTaskType.ReflexOverride]!!.completedEver)

            // Snake must still look brand new: Option A copies nothing across.
            assertFalse(source.storeState.first().records[PsychologyTaskType.Snake]!!.completedEver)

            val snakeResult = source.completeTask(
                taskType = PsychologyTaskType.Snake,
                releasePlan = calculateReleasePlan(selectedDailyUrgeCount = 3, now = dayTwo),
                now = dayTwo,
                gameType = "SNAKE",
                score = 120,
                durationSec = 62,
                validCompletion = true,
                completionToken = "SNAKE:900002",
            )

            // Snake earns its own first-time Level Points.
            assertEquals(10, snakeResult?.levelPointsAwarded)

            val after = source.storeState.first().records
            assertTrue(after[PsychologyTaskType.Snake]!!.completedEver)
            assertTrue(after[PsychologyTaskType.ReflexOverride]!!.completedEver)

            val levelPointsAfterFirst = source.storeState.first().currentLevelPoints

            // Replaying Snake's token replays the same result without re-awarding.
            val duplicate = source.completeTask(
                taskType = PsychologyTaskType.Snake,
                releasePlan = calculateReleasePlan(selectedDailyUrgeCount = 3, now = dayTwo),
                now = dayTwo.plusSeconds(1),
                gameType = "SNAKE",
                score = 120,
                durationSec = 62,
                validCompletion = true,
                completionToken = "SNAKE:900002",
            )

            assertEquals(snakeResult, duplicate)
            assertEquals(levelPointsAfterFirst, source.storeState.first().currentLevelPoints)
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
    }
}
