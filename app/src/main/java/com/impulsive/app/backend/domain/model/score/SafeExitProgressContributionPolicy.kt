package com.impulsive.app.backend.domain.model.score

data class SafeExitProgressContribution(
    val safeExitCount:
        Int,
    val additionalControlPoints:
        Int,
)

object SafeExitProgressContributionPolicy {
    fun additionalControlPoints(
        source:
            SafeExitSource,
    ): Int {
        return when (
            source
        ) {
            SafeExitSource.PivotGame ->
                0

            SafeExitSource.ResetReading,
            SafeExitSource.MomentPlan,
            ->
                SAFE_EXIT_CONTROL_POINT_BONUS
        }
    }

    fun contribution(
        record:
            SafeExitRecord,
    ): SafeExitProgressContribution {
        return SafeExitProgressContribution(
            safeExitCount =
                1,
            additionalControlPoints =
                additionalControlPoints(
                    record.source,
                ),
        )
    }
}