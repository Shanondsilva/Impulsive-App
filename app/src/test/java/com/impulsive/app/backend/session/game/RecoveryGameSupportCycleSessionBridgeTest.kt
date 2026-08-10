package com.impulsive.app.backend.session.game

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import com.impulsive.app.backend.session.adaptive.AdaptiveClock
import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCommandResult
import com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryGameSupportCycleSessionBridgeTest {
    @Test
    fun standaloneNeverTouchesCoordinatorState() = runBlocking {
        val repository = FakeRepository()
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator(repository))

        assertEquals(
            SupportCycleGameBindResult.Standalone,
            bridge.bind(com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext.Standalone),
        )
        assertEquals(
            SupportCycleGameReportResult.IgnoredStandalone,
            bridge.report(SupportCycleGameTerminalOutcome.Completed, 40_000L, true),
        )
        assertEquals(0, repository.updateCount)
    }

    @Test
    fun authoritativeRemainingBudgetBoundsRecreatedLaunch() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        val cycle = AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L)
        repository.seed(cycle)
        coordinator.startGame("cycle", ScoreGameType.ReflexOverride, 90_000L, 10_000L)
        coordinator.recordElapsed("cycle", 40_000L)

        val launch = com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext.SupportCycle(
            "cycle",
            "decision",
            ScoreGameType.ReflexOverride,
            90_000L,
        )
        val rebound = RecoveryGameSupportCycleSessionBridge(coordinator).bind(launch)
            as SupportCycleGameBindResult.Bound

        assertEquals(50_000L, rebound.launch.maxDurationMillis)
    }

    @Test
    fun validCompletionReportsExactlyOnce() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        val first = bridge.report(SupportCycleGameTerminalOutcome.Completed, 40_000L, true)
        val duplicate = bridge.report(SupportCycleGameTerminalOutcome.Completed, 40_000L, true)

        assertTrue(first is SupportCycleGameReportResult.Reported)
        assertEquals(SupportCycleGameReportResult.Duplicate, duplicate)
        assertEquals(3, repository.updateCount) // start, recovery mark, atomic elapsed-plus-terminal clear
    }

    @Test
    fun gameResultDoesNotTerminaliseCycleBeforeUserChoice() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        var terminalCount = 0
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator) { _, _ -> terminalCount += 1 }
        bridge.bind(started.launch)

        val result = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            40_000L,
        )

        assertTrue(result.allowsContinuation)
        assertFalse(checkNotNull(repository.state).cycle.isTerminal)
        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome.Completed,
            repository.state?.cycle?.currentStep?.outcome,
        )
        assertEquals(0, terminalCount)
    }

    @Test
    fun completedGamePlusPlayAnotherPreservesCycleDecisionAndRemainingBudget() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)
        bridge.resolveForContinuation(SupportCycleGameTerminalOutcome.Completed, 40_000L)

        val handOff = RecoveryGameSupportCycleHandOff(coordinator).prepareNext(
            started.launch,
            ScoreGameType.RhythmTiles,
        ) as RecoveryGameHandOffResult.Ready

        assertEquals("cycle", handOff.launch.cycleId)
        assertEquals("decision", handOff.launch.decisionId)
        assertEquals(ScoreGameType.RhythmTiles, handOff.launch.gameType)
        assertEquals(50_000L, handOff.launch.maxDurationMillis)
        assertTrue(handOff.launch.maxDurationMillis < 90_000L)
    }

    @Test
    fun duplicatePlayAnotherStartsOnlyOneNextStep() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)
        bridge.resolveForContinuation(SupportCycleGameTerminalOutcome.Completed, 10_000L)
        val handOff = RecoveryGameSupportCycleHandOff(coordinator)

        assertTrue(
            handOff.prepareNext(started.launch, ScoreGameType.RhythmTiles) is
                RecoveryGameHandOffResult.Ready,
        )
        assertEquals(
            RecoveryGameHandOffResult.Unavailable,
            handOff.prepareNext(started.launch, ScoreGameType.BlockCascade),
        )
        assertEquals(2, repository.state?.cycle?.currentStep?.sequence)
    }

    @Test
    fun completedWalkAwayTerminalisesAndNotifiesExactlyOnce() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        var terminalCount = 0
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator) { _, _ -> terminalCount += 1 }
        bridge.bind(started.launch)
        bridge.resolveForContinuation(SupportCycleGameTerminalOutcome.Completed, 20_000L)

        assertTrue(
            bridge.resolveAndEnd(SupportCycleGameTerminalOutcome.Completed, 20_000L).allowsExit,
        )
        assertEquals(
            SupportCycleGameReportResult.Duplicate,
            bridge.resolveAndEnd(SupportCycleGameTerminalOutcome.Completed, 20_000L),
        )
        assertEquals(1, terminalCount)
        assertEquals(null, repository.state)
    }

    @Test
    fun backTerminalisesAsAbandonedExactlyOnce() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.SkylineReset,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        var terminalCount = 0
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator) { _, outcome ->
            assertEquals(SupportCycleGameTerminalOutcome.Abandoned, outcome)
            terminalCount += 1
        }
        bridge.bind(started.launch)

        assertTrue(
            bridge.resolveAndEnd(SupportCycleGameTerminalOutcome.Abandoned, 5_000L).allowsExit,
        )
        assertEquals(
            SupportCycleGameReportResult.Duplicate,
            bridge.resolveAndEnd(SupportCycleGameTerminalOutcome.Abandoned, 5_000L),
        )
        assertEquals(1, terminalCount)
    }

    @Test
    fun forcedReplayUsesRemainingBudgetAndCannotResetToNinetySeconds() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.BlockCascade,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)
        bridge.resolveForContinuation(SupportCycleGameTerminalOutcome.Failed, 40_000L)

        val replay = RecoveryGameSupportCycleHandOff(coordinator).prepareNext(
            started.launch,
            ScoreGameType.BlockCascade,
        ) as RecoveryGameHandOffResult.Ready
        assertEquals(50_000L, replay.launch.maxDurationMillis)
        assertEquals(ScoreGameType.BlockCascade, replay.launch.gameType)
    }

    @Test
    fun totalBudgetTimeoutPreventsFurtherLaunch() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)
        bridge.resolveForContinuation(SupportCycleGameTerminalOutcome.TimedOut, 90_000L)

        assertEquals(
            RecoveryGameHandOffResult.Unavailable,
            RecoveryGameSupportCycleHandOff(coordinator).prepareNext(
                started.launch,
                ScoreGameType.ReflexOverride,
            ),
        )
        assertEquals(null, repository.state)
    }

    @Test
    fun oneTerminalGameResultEmitsOneDecisionOutcome() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive.AdaptiveSupportCycleGameLaunchResult.Ready
        var terminalCount = 0
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator) { decisionId, outcome ->
            assertEquals("decision", decisionId)
            assertEquals(SupportCycleGameTerminalOutcome.Completed, outcome)
            terminalCount += 1
        }
        bridge.bind(started.launch)

        bridge.report(SupportCycleGameTerminalOutcome.Completed, 20_000L, true)
        bridge.report(SupportCycleGameTerminalOutcome.Completed, 20_000L, true)

        assertEquals(1, terminalCount)
    }

    @Test
    fun completedResultCanBindAfterBridgeRecreation() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        RecoveryGameSupportCycleSessionBridge(coordinator).apply {
            bind(started.launch)
            resolveForContinuation(
                SupportCycleGameTerminalOutcome.Completed,
                40_000L,
            )
        }

        val rebound = RecoveryGameSupportCycleSessionBridge(coordinator)
            .bind(started.launch) as SupportCycleGameBindResult.Bound

        assertEquals(50_000L, rebound.launch.maxDurationMillis)
        assertEquals(
            SupportCycleResolvedStep(
                outcome = SupportCycleGameTerminalOutcome.Completed,
                elapsedDurationMillis = 40_000L,
            ),
            rebound.resolvedStep,
        )
        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportCycleStatus.Active,
            repository.state?.cycle?.status,
        )
    }

    @Test
    fun restoredCompletedResultCanFinishCycle() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        RecoveryGameSupportCycleSessionBridge(coordinator).apply {
            bind(started.launch)
            resolveForContinuation(
                SupportCycleGameTerminalOutcome.Completed,
                40_000L,
            )
        }

        var terminalCount = 0
        val restored = RecoveryGameSupportCycleSessionBridge(
            coordinator,
        ) { decisionId, outcome ->
            assertEquals("decision", decisionId)
            assertEquals(SupportCycleGameTerminalOutcome.Completed, outcome)
            terminalCount += 1
        }

        restored.bind(started.launch)

        assertTrue(
            restored.resolveAndEnd(
                SupportCycleGameTerminalOutcome.Completed,
                40_000L,
            ).allowsExit,
        )
        assertEquals(null, repository.state)
        assertEquals(1, terminalCount)
    }

    @Test
    fun cancelledTerminalStepCannotBind() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L))
        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.BlockCascade,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val persisted = checkNotNull(repository.state)
        val step = checkNotNull(persisted.cycle.currentStep)
        repository.state = persisted.copy(
            cycle = persisted.cycle.copy(
                currentStep = step.copy(
                    outcome = com.impulsive.app.backend.domain.model.adaptive
                        .AdaptiveSupportStepOutcome.Cancelled,
                ),
            ),
        )

        assertEquals(
            SupportCycleGameBindResult.UnavailableSupportCycle,
            RecoveryGameSupportCycleSessionBridge(coordinator).bind(started.launch),
        )
    }

    @Test
    fun missingCycleNeverFallsBackToStandalone() = runBlocking {
        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator(FakeRepository()))
        val launch = com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext.SupportCycle(
            "missing",
            "decision",
            ScoreGameType.BlockCascade,
            90_000L,
        )

        assertEquals(SupportCycleGameBindResult.UnavailableSupportCycle, bridge.bind(launch))
    }

    @Test
    fun mismatchedDecisionNeverFallsBackToStandalone() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)
        repository.seed(AdaptiveSupportCycle("cycle", "authoritative", "incident", 90_000L))
        coordinator.startGame("cycle", ScoreGameType.BlockCascade, 90_000L, 10_000L)
        val mismatched = com.impulsive.app.backend.domain.game.RecoveryGameLaunchContext.SupportCycle(
            "cycle",
            "requested",
            ScoreGameType.BlockCascade,
            90_000L,
        )

        assertEquals(
            SupportCycleGameBindResult.UnavailableSupportCycle,
            RecoveryGameSupportCycleSessionBridge(coordinator).bind(mismatched),
        )
    }

    @Test
    fun runtimeBindingExposesRestoredCompletedStep() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val originalRuntime =
            RecoveryGameSupportCycleRuntime(coordinator)

        val originalBinding = checkNotNull(
            originalRuntime.bindWithRecovery(
                requested = started.launch,
                standaloneDurationMillis = 90_000L,
            ),
        )

        assertEquals(90_000L, originalBinding.durationMillis)
        assertEquals(null, originalBinding.resolvedStep)

        originalRuntime.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            40_000L,
        )

        val restoredRuntime =
            RecoveryGameSupportCycleRuntime(coordinator)

        val restoredBinding = checkNotNull(
            restoredRuntime.bindWithRecovery(
                requested = started.launch,
                standaloneDurationMillis = 90_000L,
            ),
        )

        assertEquals(50_000L, restoredBinding.durationMillis)
        assertEquals(
            SupportCycleResolvedStep(
                outcome = SupportCycleGameTerminalOutcome.Completed,
                elapsedDurationMillis = 40_000L,
            ),
            restoredBinding.resolvedStep,
        )

        assertTrue(restoredRuntime.isSupportCycle())
    }

    @Test
    fun runtimeBindingPreservesInProgressRemainingDuration() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        coordinator.startGame(
            "cycle",
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        )

        coordinator.recordElapsed(
            "cycle",
            25_000L,
        )

        val launch =
            com.impulsive.app.backend.domain.game
                .RecoveryGameLaunchContext.SupportCycle(
                    cycleId = "cycle",
                    decisionId = "decision",
                    gameType = ScoreGameType.RhythmTiles,
                    maxDurationMillis = 90_000L,
                )

        val binding = checkNotNull(
            RecoveryGameSupportCycleRuntime(coordinator)
                .bindWithRecovery(
                    requested = launch,
                    standaloneDurationMillis = 90_000L,
                ),
        )

        assertEquals(65_000L, binding.durationMillis)
        assertEquals(null, binding.resolvedStep)
    }

    @Test
    fun runtimeStandaloneBindingHasNoResolvedStep() = runBlocking {
        val runtime =
            RecoveryGameSupportCycleRuntime(
                coordinator(FakeRepository()),
            )

        val binding = checkNotNull(
            runtime.bindWithRecovery(
                requested =
                    com.impulsive.app.backend.domain.game
                        .RecoveryGameLaunchContext.Standalone,
                standaloneDurationMillis = 60_000L,
            ),
        )

        assertEquals(60_000L, binding.durationMillis)
        assertEquals(null, binding.resolvedStep)
        assertFalse(runtime.isSupportCycle())
    }

    @Test
    fun legacyRuntimeBindFailsClosedForRestoredTerminalStep() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.BlockCascade,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        RecoveryGameSupportCycleRuntime(coordinator).apply {
            bindWithRecovery(
                requested = started.launch,
                standaloneDurationMillis = 90_000L,
            )

            resolveForContinuation(
                SupportCycleGameTerminalOutcome.Failed,
                30_000L,
            )
        }

        val restoredRuntime =
            RecoveryGameSupportCycleRuntime(coordinator)

        assertEquals(
            null,
            restoredRuntime.bind(
                requested = started.launch,
                standaloneDurationMillis = 90_000L,
            ),
        )

        assertTrue(restoredRuntime.isSupportCycle())

        val authoritativeBinding = checkNotNull(
            RecoveryGameSupportCycleRuntime(coordinator)
                .bindWithRecovery(
                    requested = started.launch,
                    standaloneDurationMillis = 90_000L,
                ),
        )

        assertEquals(
            SupportCycleGameTerminalOutcome.Failed,
            authoritativeBinding.resolvedStep?.outcome,
        )

        assertEquals(
            30_000L,
            authoritativeBinding.resolvedStep
                ?.elapsedDurationMillis,
        )
    }

    @Test
    fun conflictingResolvedOutcomeFailsClosed() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)

        bridge.bind(started.launch)

        bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        val conflict = bridge.resolveAndEnd(
            SupportCycleGameTerminalOutcome.Failed,
            20_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.OutcomeConflict,
            conflict,
        )

        assertFalse(conflict.allowsExit)

        assertFalse(conflict.allowsContinuation)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.Completed,
            repository.state?.cycle?.currentStep?.outcome,
        )

        assertFalse(
            checkNotNull(repository.state).cycle.isTerminal,
        )
    }

    @Test
    fun unifiedResultActionCoordinatorClearsStateAfterSuccessfulFinish() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val runtime = RecoveryGameSupportCycleRuntime(coordinator)

        runtime.bindWithRecovery(
            requested = started.launch,
            standaloneDurationMillis = 90_000L,
        )

        runtime.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        var clearCount = 0

        val actions = RecoveryGameResultActionCoordinator(
            runtime = runtime,
            clearResultState = { clearCount += 1 },
        )

        assertTrue(
            actions.finish(
                outcome = SupportCycleGameTerminalOutcome.Completed,
                elapsedDurationMillis = 20_000L,
            ),
        )

        assertEquals(1, clearCount)

        assertEquals(null, repository.state)
    }

    @Test
    fun unifiedResultActionCoordinatorDoesNotClearOnOutcomeConflict() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val runtime = RecoveryGameSupportCycleRuntime(coordinator)

        runtime.bindWithRecovery(
            requested = started.launch,
            standaloneDurationMillis = 90_000L,
        )

        runtime.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        var clearCount = 0

        val actions = RecoveryGameResultActionCoordinator(
            runtime = runtime,
            clearResultState = { clearCount += 1 },
        )

        assertFalse(
            actions.finish(
                outcome = SupportCycleGameTerminalOutcome.Failed,
                elapsedDurationMillis = 20_000L,
            ),
        )

        assertEquals(0, clearCount)

        assertTrue(repository.state != null)
    }

    @Test
    fun persistenceFailureDoesNotBecomeSuccessfulDuplicateAndCanRetry() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)

        bridge.bind(started.launch)

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.PersistenceFailure

        val failed = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.Reported(
                AdaptiveSupportCycleCommandResult.PersistenceFailure,
            ),
            failed,
        )

        assertFalse(failed.allowsContinuation)

        assertFalse(failed.allowsExit)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.InProgress,
            repository.state?.cycle?.currentStep?.outcome,
        )

        assertEquals(
            0L,
            repository.state?.cycle?.currentStep?.consumedDurationMillis,
        )

        val retry = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertTrue(retry.allowsContinuation)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.Completed,
            repository.state?.cycle?.currentStep?.outcome,
        )

        assertEquals(
            20_000L,
            repository.state?.cycle?.currentStep?.consumedDurationMillis,
        )

        assertEquals(
            SupportCycleGameReportResult.Duplicate,
            bridge.resolveForContinuation(
                SupportCycleGameTerminalOutcome.Completed,
                20_000L,
            ),
        )
    }

    @Test
    fun revisionConflictDoesNotBecomeSuccessfulDuplicateAndCanRetry() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)

        bridge.bind(started.launch)

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.RevisionConflict(
            currentRevision = 99L,
        )

        val conflicted = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Failed,
            15_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.Reported(
                AdaptiveSupportCycleCommandResult.RevisionConflict,
            ),
            conflicted,
        )

        assertFalse(conflicted.allowsContinuation)

        assertFalse(conflicted.allowsExit)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.InProgress,
            repository.state?.cycle?.currentStep?.outcome,
        )

        assertEquals(
            0L,
            repository.state?.cycle?.currentStep?.consumedDurationMillis,
        )

        val retry = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Failed,
            15_000L,
        )

        assertTrue(retry.allowsContinuation)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.Failed,
            repository.state?.cycle?.currentStep?.outcome,
        )

        assertEquals(
            15_000L,
            repository.state?.cycle?.currentStep?.consumedDurationMillis,
        )
    }

    @Test
    fun differentOutcomeCannotReplaceRetryablePendingReport() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.BlockCascade,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)

        bridge.bind(started.launch)

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.PersistenceFailure

        val failed = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            10_000L,
        )

        assertFalse(failed.allowsContinuation)

        val updatesAfterFailure = repository.updateCount

        val conflictingRetry = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Failed,
            10_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.OutcomeConflict,
            conflictingRetry,
        )

        assertFalse(conflictingRetry.allowsContinuation)

        assertFalse(conflictingRetry.allowsExit)

        assertEquals(
            updatesAfterFailure,
            repository.updateCount,
        )

        val correctRetry = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            10_000L,
        )

        assertTrue(correctRetry.allowsContinuation)

        assertEquals(
            com.impulsive.app.backend.domain.model.adaptive
                .AdaptiveSupportStepOutcome.Completed,
            repository.state?.cycle?.currentStep?.outcome,
        )
    }

    @Test
    fun failedCycleFinishRemainsRetryableAndNeverBecomesDuplicate() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.SkylineReset,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)

        bridge.bind(started.launch)

        assertTrue(
            bridge.resolveForContinuation(
                SupportCycleGameTerminalOutcome.Completed,
                20_000L,
            ).allowsContinuation,
        )

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.PersistenceFailure

        val failedFinish = bridge.resolveAndEnd(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.Reported(
                AdaptiveSupportCycleCommandResult.PersistenceFailure,
            ),
            failedFinish,
        )

        assertFalse(failedFinish.allowsExit)

        assertTrue(repository.state != null)

        val retry = bridge.resolveAndEnd(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertTrue(retry.allowsExit)

        assertEquals(null, repository.state)
    }

    @Test
    fun keepsRestoredResultVisibleIsTrueForActiveContinuation() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        val result = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertTrue(result.allowsContinuation)
        assertTrue(result.keepsRestoredResultVisible)
        assertTrue(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(result),
        )
    }

    @Test
    fun keepsRestoredResultVisibleIsTrueForSuccessfulTerminalExit() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        val result = bridge.resolveAndEnd(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertFalse(result.allowsContinuation)
        assertTrue(result.allowsExit)
        assertTrue(result.keepsRestoredResultVisible)
        assertTrue(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(result),
        )
    }

    @Test
    fun keepsRestoredResultVisibleIsTrueForRetryablePersistenceFailure() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.PersistenceFailure

        val failed = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        assertFalse(failed.allowsContinuation)
        assertFalse(failed.allowsExit)
        assertTrue(failed.keepsRestoredResultVisible)
        // Visible Result, but no accepted mutation: the checkpoint must survive.
        assertFalse(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(failed),
        )
    }

    @Test
    fun keepsRestoredResultVisibleIsTrueForRetryableRevisionConflict() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.RhythmTiles,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        repository.nextUpdateResult = AdaptiveSupportCycleMutationResult.RevisionConflict(
            currentRevision = 99L,
        )

        val conflicted = bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Failed,
            15_000L,
        )

        assertFalse(conflicted.allowsContinuation)
        assertFalse(conflicted.allowsExit)
        assertTrue(conflicted.keepsRestoredResultVisible)
        // Visible Result, but no accepted mutation: the checkpoint must survive.
        assertFalse(
            SnakeSupportCheckpointResolutionPolicy
                .shouldClearAfterExhaustedCheckpoint(conflicted),
        )
    }

    @Test
    fun keepsRestoredResultVisibleIsFalseForOutcomeConflict() = runBlocking {
        val repository = FakeRepository()
        val coordinator = coordinator(repository)

        repository.seed(
            AdaptiveSupportCycle(
                "cycle",
                "decision",
                "incident",
                90_000L,
            ),
        )

        val started = coordinator.startGame(
            "cycle",
            ScoreGameType.ReflexOverride,
            90_000L,
            10_000L,
        ) as com.impulsive.app.backend.session.adaptive
            .AdaptiveSupportCycleGameLaunchResult.Ready

        val bridge = RecoveryGameSupportCycleSessionBridge(coordinator)
        bridge.bind(started.launch)

        bridge.resolveForContinuation(
            SupportCycleGameTerminalOutcome.Completed,
            20_000L,
        )

        val conflict = bridge.resolveAndEnd(
            SupportCycleGameTerminalOutcome.Failed,
            20_000L,
        )

        assertEquals(
            SupportCycleGameReportResult.OutcomeConflict,
            conflict,
        )
        assertFalse(conflict.keepsRestoredResultVisible)
    }

    private fun coordinator(repository: AdaptiveSupportCycleRepository) =
        AdaptiveSupportCycleCoordinator(repository, AdaptiveClock { 100L })

    private class FakeRepository : AdaptiveSupportCycleRepository {
        var state: PersistedAdaptiveSupportCycle? = null
        var updateCount = 0
        var nextUpdateResult: AdaptiveSupportCycleMutationResult? = null

        fun seed(cycle: AdaptiveSupportCycle) {
            state = PersistedAdaptiveSupportCycle(cycle, 1L, 1L, 1_000_000L, 1L)
        }

        override suspend fun create(
            cycle: AdaptiveSupportCycle,
            createdAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ) = AdaptiveSupportCycleCreateResult.ExistingActive(checkNotNull(state))

        override suspend fun load(nowEpochMillis: Long) = state
            ?.let(AdaptiveSupportCycleLoadResult::Active)
            ?: AdaptiveSupportCycleLoadResult.NotFound

        override suspend fun update(
            cycleId: String,
            expectedRevision: Long,
            cycle: AdaptiveSupportCycle,
            updatedAtEpochMillis: Long,
        ): AdaptiveSupportCycleMutationResult {
            val current = state ?: return AdaptiveSupportCycleMutationResult.NotFound
            updateCount += 1

            val forcedResult = nextUpdateResult

            if (forcedResult != null) {
                nextUpdateResult = null
                return forcedResult
            }

            if (cycle.isTerminal) {
                state = null
                return AdaptiveSupportCycleMutationResult.Cleared
            }
            val updated = current.copy(cycle = cycle, revision = current.revision + 1L)
            state = updated
            return AdaptiveSupportCycleMutationResult.Updated(updated)
        }

        override suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult {
            state = null
            return AdaptiveSupportCycleMutationResult.Cleared
        }

        override suspend fun clearAll(): AdaptiveSupportCycleClearAllResult {
            state = null
            return AdaptiveSupportCycleClearAllResult.Cleared
        }
    }
}
