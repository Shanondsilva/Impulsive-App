package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.model.adaptive.AdaptiveDecision
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.SafeExitAction
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.session.progress.SafeExitCandidateRecorder
import com.impulsive.app.backend.session.progress.SafeExitRecordingResult
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class MomentPlanSafeExitRequestStatus {
    Idle,
    Recording,
    Durable,
    Failed,
}

internal object MomentPlanSafeExitCandidateFactory {
    fun createOrNull(
        decision: AdaptiveDecision,
        zoneId: ZoneId,
    ): SafeExitCandidate? {
        val completedAtMillis =
            decision.completedAtMillis
                ?: return null

        if (
            decision.decisionId.isBlank() ||
            decision.startedAtMillis == null ||
            decision.dismissedAtMillis != null ||
            decision.assignment.actualIntervention !=
            InterventionFamily.MomentPlan ||
            decision.assignment.momentPlanId
                .isNullOrBlank()
        ) {
            return null
        }

        return SafeExitCandidate(
            source =
            SafeExitSource.MomentPlan,
            sourceId =
            decision.decisionId,
            action =
            SafeExitAction.WalkAway,
            completedAt =
            LocalDateTime.ofInstant(
                Instant.ofEpochMilli(
                    completedAtMillis,
                ),
                zoneId,
            ),
            validCompletion =
            true,
        )
    }
}

internal fun interface MomentPlanWalkAwayRecorder {
    suspend fun recordExplicitWalkAway(
        decision: AdaptiveDecision,
    ): SafeExitRecordingResult?
}

internal class MomentPlanSafeExitRecorder(
    private val recorder:
        SafeExitCandidateRecorder,
    private val zoneId:
        ZoneId = ZoneId.systemDefault(),
) : MomentPlanWalkAwayRecorder {
    override suspend fun recordExplicitWalkAway(
        decision: AdaptiveDecision,
    ): SafeExitRecordingResult? {
        val candidate =
            MomentPlanSafeExitCandidateFactory
                .createOrNull(
                    decision =
                    decision,
                    zoneId =
                    zoneId,
                )
                ?: return null

        val firstResult =
            recorder.record(
                candidate,
            )

        return if (
            firstResult ==
            SafeExitRecordingResult
                .RetryableFailure
        ) {
            recorder.record(
                candidate,
            )
        } else {
            firstResult
        }
    }
}

internal fun SafeExitRecordingResult?.isDurableMomentPlanSafeExit():
    Boolean {
    return this is
        SafeExitRecordingResult.Recorded ||
        this is
        SafeExitRecordingResult.Duplicate
}
