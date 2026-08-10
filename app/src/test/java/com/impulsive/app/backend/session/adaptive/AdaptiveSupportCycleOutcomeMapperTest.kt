package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleStepResolution
import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionPolicy
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveAssignment
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveReasonCode
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSourceKind
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AssignmentMode
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.adaptive.MomentIntensity
import com.impulsive.app.backend.domain.model.adaptive.RepeatObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSupportCycleOutcomeMapperTest {
    @Test
    fun terminalCycleStatesRemainDistinct() {
        val active = AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L)
        val started = (AdaptiveSupportCycleTransitionPolicy.startStep(
            active,
            InterventionFamily.ShortPause,
            requestedDurationMillis = 30_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionResult.Applied).cycle

        val completed = resolve(started, AdaptiveSupportCycleStepResolution.CompletedAndEndCycle)
        val failed = resolve(started, AdaptiveSupportCycleStepResolution.FailedAndEndCycle)
        val abandoned = resolve(started, AdaptiveSupportCycleStepResolution.AbandonedAndEndCycle)
        val cancelled = resolve(started, AdaptiveSupportCycleStepResolution.CancelledAndEndCycle)

        assertEquals(AdaptiveSupportCycleOutcomeKind.Completed, AdaptiveSupportCycleOutcomeMapper.fromCycle(completed))
        assertEquals(AdaptiveSupportCycleOutcomeKind.Failed, AdaptiveSupportCycleOutcomeMapper.fromCycle(failed))
        assertEquals(AdaptiveSupportCycleOutcomeKind.Abandoned, AdaptiveSupportCycleOutcomeMapper.fromCycle(abandoned))
        assertEquals(AdaptiveSupportCycleOutcomeKind.Cancelled, AdaptiveSupportCycleOutcomeMapper.fromCycle(cancelled))
    }

    /**
     * The mapper prioritises UserRequestedAlternative ahead of status, so the
     * two explicit rejections must remain distinguishable: the first leaves a
     * continuing cycle reporting AlternativeRequested, while the terminal second
     * reports InterventionAbandoned and therefore maps to Abandoned.
     */
    @Test
    fun firstAndSecondAlternativeRequestsMapToDistinctOutcomes() {
        val active = AdaptiveSupportCycle("cycle", "decision", "incident", 90_000L)
        val started = (AdaptiveSupportCycleTransitionPolicy.startStep(
            active,
            InterventionFamily.ShortPause,
            requestedDurationMillis = 30_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionResult.Applied).cycle

        val afterFirst = resolve(
            started,
            AdaptiveSupportCycleStepResolution.AlternativeRequested,
        )
        assertEquals(1, afterFirst.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportCycleOutcomeKind.AlternativeRequested,
            AdaptiveSupportCycleOutcomeMapper.fromCycle(afterFirst),
        )

        val nextStep = (AdaptiveSupportCycleTransitionPolicy.startStep(
            afterFirst,
            InterventionFamily.PivotReading,
            requestedDurationMillis = 30_000L,
            minimumUsefulDurationMillis = 10_000L,
        ) as com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionResult.Applied).cycle

        val afterSecond = resolve(
            nextStep,
            AdaptiveSupportCycleStepResolution.AlternativeRequested,
        )
        assertEquals(2, afterSecond.alternativeRequestCount)
        assertEquals(
            AdaptiveSupportCycleOutcomeKind.Abandoned,
            AdaptiveSupportCycleOutcomeMapper.fromCycle(afterSecond),
        )
    }

    @Test
    fun existingDecisionEvidenceMapsFeedbackRepeatAndEngagementSeparately() {
        val outcomes = AdaptiveSupportCycleOutcomeMapper.fromDecision(
            decision().copy(
                startedAtMillis = 2L,
                dismissedAtMillis = 3L,
                feedbackCode = FeedbackCode.WrongTiming,
                repeatObservation = RepeatObservation.RepeatDetected,
            ),
        )
        assertTrue(outcomes.contains(AdaptiveSupportCycleOutcomeKind.Dismissed))
        assertTrue(outcomes.contains(AdaptiveSupportCycleOutcomeKind.WrongTiming))
        assertTrue(outcomes.contains(AdaptiveSupportCycleOutcomeKind.RepeatDetected))
    }

    private fun resolve(
        cycle: AdaptiveSupportCycle,
        resolution: AdaptiveSupportCycleStepResolution,
    ) = (AdaptiveSupportCycleTransitionPolicy.resolveCurrentStep(cycle, resolution)
        as com.impulsive.app.backend.domain.engine.adaptive.AdaptiveSupportCycleTransitionResult.Applied).cycle

    private fun decision() = AdaptiveDecision(
        decisionId = "decision",
        protectionIncidentToken = "incident",
        sourceKind = AdaptiveSourceKind.App,
        createdAtMillis = 1L,
        momentWindowStartedAtMillis = 1L,
        momentCue = null,
        baselineUrgeRating = null,
        assignment = AdaptiveAssignment(
            MomentIntensity.FirstAttempt,
            AssignmentMode.MinimumFriction,
            setOf(InterventionFamily.ShortPause),
            InterventionFamily.ShortPause,
            1.0,
            AdaptiveReasonCode.MinimumEffectiveFriction,
        ),
        observationDeadlineAtMillis = 100L,
    )
}
