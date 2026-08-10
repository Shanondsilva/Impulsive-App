package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.EngagementOutcome
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepEvidenceRecord
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepNoMatchReason
import com.impulsive.app.backend.domain.model.adaptive.FamiliarStepRouteIdentity
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FamiliarStepCoordinatorTest {
    @Test
    fun firstAttemptDoesNotReadEvidenceOrExposeAvailableState() = runBlocking {
        val fixture = fixture()
        fixture.decisions.stored += decision().copy(
            assignment = decision().assignment.copy(
                momentIntensity = MomentIntensity.FirstAttempt,
            ),
        )

        assertEquals(
            FamiliarStepSessionState.Unavailable(FamiliarStepNoMatchReason.FirstAttempt),
            fixture.coordinator.state(decision().decisionId),
        )
        assertEquals(0, fixture.decisions.familiarStepEvidenceReads)
    }

    @Test
    fun repeatedQualifiedStateHasExactCountsCommandsAndExistingRoute() = runBlocking {
        val fixture = fixture()
        fixture.decisions.stored += decision()
        fixture.decisions.familiarStepEvidence = evidence()

        val state = fixture.coordinator.state(decision().decisionId)
            as FamiliarStepSessionState.FamiliarStepAvailable

        assertEquals(4, state.comparableCount)
        assertEquals(3, state.favourableCount)
        assertEquals(state.comparableCount, state.explanation.comparableCount)
        assertEquals(state.favourableCount, state.explanation.favourableCount)
        assertEquals(state.routeIdentity, state.explanation.routeIdentity)
        assertEquals(FamiliarStepCommand.Start, state.startCommand)
        assertEquals(FamiliarStepCommand.AnotherSupport, state.anotherSupportCommand)
        assertEquals(FamiliarStepCommand.LeaveThisMoment, state.leaveThisMomentCommand)
        assertEquals(AdaptiveRouteKind.Game, state.routeRecommendation?.kind)
    }

    @Test
    fun startRevalidatesStalenessAndDuplicateStartIsIdempotent() = runBlocking {
        val fixture = fixture()
        fixture.decisions.stored += decision()
        fixture.decisions.familiarStepEvidence = evidence()
        val available = fixture.coordinator.state(decision().decisionId)
            as FamiliarStepSessionState.FamiliarStepAvailable

        assertTrue(
            fixture.coordinator.start(decision().decisionId, available.routeIdentity) is
                FamiliarStepStartResult.Ready,
        )
        assertTrue(
            fixture.coordinator.start(decision().decisionId, available.routeIdentity) is
                FamiliarStepStartResult.Ready,
        )

        fixture.decisions.familiarStepEvidence = evidence().map {
            it.copy(routeIdentity = it.routeIdentity.copy(protocolVersion = 99))
        }
        assertEquals(
            FamiliarStepStartResult.Unavailable(FamiliarStepNoMatchReason.StaleProtocol),
            fixture.coordinator.start(decision().decisionId, available.routeIdentity),
        )
    }

    @Test
    fun disablePersonalSuggestionsImmediatelyRemovesAvailabilityAndReenableRematches() = runBlocking {
        val fixture = fixture()
        fixture.decisions.stored += decision()
        fixture.decisions.familiarStepEvidence = evidence()
        assertTrue(
            fixture.coordinator.state(decision().decisionId) is
                FamiliarStepSessionState.FamiliarStepAvailable,
        )

        fixture.preferences.update(
            fixture.preferences.current.copy(personalSuggestionsEnabled = false),
            10_000L,
        )
        assertEquals(
            FamiliarStepSessionState.Unavailable(
                FamiliarStepNoMatchReason.PersonalSuggestionsDisabled,
            ),
            fixture.coordinator.state(decision().decisionId),
        )

        fixture.preferences.update(
            fixture.preferences.current.copy(personalSuggestionsEnabled = true),
            10_001L,
        )
        assertTrue(
            fixture.coordinator.state(decision().decisionId) is
                FamiliarStepSessionState.FamiliarStepAvailable,
        )
    }

    @Test
    fun clearHistoryImmediatelyRemovesAvailability() = runBlocking {
        val fixture = fixture()
        fixture.decisions.stored += decision()
        fixture.decisions.familiarStepEvidence = evidence()
        assertTrue(
            fixture.coordinator.state(decision().decisionId) is
                FamiliarStepSessionState.FamiliarStepAvailable,
        )
        fixture.decisions.familiarStepEvidence = emptyList()
        assertEquals(
            FamiliarStepSessionState.Unavailable(FamiliarStepNoMatchReason.InsufficientEvidence),
            fixture.coordinator.state(decision().decisionId),
        )
    }

    @Test
    fun familiarStepConflictRoutesToExistingCycleOwner() = runBlocking {
        val fixture = fixture(
            existingCycle = AdaptiveSupportCycle(
                cycleId = "old-cycle",
                decisionId = "old-decision",
                protectionIncidentToken = "old-incident",
                initialDurationMillis = 90_000L,
            ),
        )

        fixture.decisions.stored += decision()
        fixture.decisions.familiarStepEvidence = evidence()

        val available = fixture.coordinator.state(decision().decisionId) as
            FamiliarStepSessionState.FamiliarStepAvailable

        val result = fixture.coordinator.start(
            decision().decisionId,
            available.routeIdentity,
        ) as FamiliarStepStartResult.ResumeExistingCycle

        assertEquals(AdaptiveRouteKind.AdaptiveMoment, result.routeRequest.kind)

        assertEquals("old-decision", result.routeRequest.decisionId)

        assertFalse(result.routeRequest.decisionId == decision().decisionId)
    }

    @Test
    fun matcherPlacementIsLimitedToAdaptiveSessionCoordinator() {
        val production = File("src/main/java/com/impulsive/app")
        val directCallers = production.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("FamiliarStepMatcher.match(") }
            .map { it.name }
            .toList()
        assertEquals(listOf("FamiliarStepCoordinator.kt"), directCallers)

        val forbiddenAreas = listOf("overlay", "website", "home", "momentplan")
        assertFalse(directCallers.any { name -> forbiddenAreas.any { name.lowercase().contains(it) } })
    }

    private fun fixture(existingCycle: AdaptiveSupportCycle? = null): Fixture {
        val decisions = FakeDecisionRepository()
        val plans = FakeMomentPlanRepository()
        val preferences = FakePreferenceRepository()
        val clock = FakeClock(10_000L)
        val lifecycle = AdaptiveDecisionLifecycle(
            decisions,
            plans,
            FakeScheduler(),
            clock,
            AdaptiveSafeLogger { _, _ -> },
        )
        val cycleRepository = InMemoryCycleRepository().apply {
            existingCycle?.let(::seed)
        }
        return Fixture(
            decisions,
            preferences,
            FamiliarStepCoordinator(
                decisions,
                preferences,
                plans,
                lifecycle,
                AdaptiveSupportCycleCoordinator(
                    cycleRepository,
                    clock,
                    AdaptiveIdSource { "familiar-cycle" },
                ),
                clock,
            ),
        )
    }

    private fun evidence() = List(4) { index ->
        FamiliarStepEvidenceRecord(
            decisionId = "history-$index",
            routeIdentity = GameIdentity,
            momentCue = null,
            feedbackCode = if (index < 3) FeedbackCode.Helped else FeedbackCode.DidNotHelp,
            engagementOutcome = EngagementOutcome.Completed,
            repeatObservation = RepeatObservation.NoRepeatDetected,
            decisionAtMillis = index.toLong(),
            finalisedAtMillis = index + 100L,
        )
    }

    private data class Fixture(
        val decisions: FakeDecisionRepository,
        val preferences: FakePreferenceRepository,
        val coordinator: FamiliarStepCoordinator,
    )

    private class InMemoryCycleRepository : AdaptiveSupportCycleRepository {
        private var state: PersistedAdaptiveSupportCycle? = null

        fun seed(cycle: AdaptiveSupportCycle) {
            state = PersistedAdaptiveSupportCycle(
                cycle = cycle,
                createdAtEpochMillis = 1_000L,
                updatedAtEpochMillis = 1_000L,
                expiresAtEpochMillis = 100_000L,
                revision = 1L,
            )
        }

        override suspend fun create(
            cycle: AdaptiveSupportCycle,
            createdAtEpochMillis: Long,
            expiresAtEpochMillis: Long,
        ): AdaptiveSupportCycleCreateResult {
            state?.let { return AdaptiveSupportCycleCreateResult.ExistingActive(it) }
            return AdaptiveSupportCycleCreateResult.Created(
                PersistedAdaptiveSupportCycle(
                    cycle,
                    createdAtEpochMillis,
                    createdAtEpochMillis,
                    expiresAtEpochMillis,
                    1L,
                ).also { state = it },
            )
        }

        override suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult =
            state?.let(AdaptiveSupportCycleLoadResult::Active)
                ?: AdaptiveSupportCycleLoadResult.NotFound

        override suspend fun update(
            cycleId: String,
            expectedRevision: Long,
            cycle: AdaptiveSupportCycle,
            updatedAtEpochMillis: Long,
        ): AdaptiveSupportCycleMutationResult = AdaptiveSupportCycleMutationResult.NotFound

        override suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult {
            state = null
            return AdaptiveSupportCycleMutationResult.Cleared
        }

        override suspend fun clearAll(): AdaptiveSupportCycleClearAllResult {
            state = null
            return AdaptiveSupportCycleClearAllResult.Cleared
        }
    }

    private companion object {
        val GameIdentity = FamiliarStepRouteIdentity(
            InterventionFamily.PivotGame,
            "pivot_game",
            1,
        )
    }
}
