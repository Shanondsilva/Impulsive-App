package com.impulsive.app.backend.domain.pathshift

enum class PathShiftCycleStatus {
    Active,
    Finalised,
    Cancelled,
}

data class PathShiftCycle(
    val cycleId: String,
    val createdAtMillis: Long,
    val lookbackStartedAtMillis: Long,
    val lookbackEndedAtMillis: Long,
    val forecastWindowStartedAtMillis: Long,
    val forecastWindowEndsAtMillis: Long,
    val forecastPolicyVersion: Int,
    val evidenceStrength: PathShiftEvidenceStrength,
    val inputProtectedMomentCount: Int,
    val inputDistinctDayCount: Int,
    val estimatedLowerCount: Int,
    val estimatedUpperCount: Int,
    val commonWindowStartMinute: Int?,
    val commonWindowEndMinute: Int?,
    val preparedPlanId: String? = null,
    val preparedPlanContentRevisionId: String? = null,
    val preparedAtMillis: Long? = null,
    val reviewFinalisedAtMillis: Long? = null,
    val observedProtectedMomentCount: Int = 0,
    val preparedPlanSelectedCount: Int = 0,
    val preparedPlanStartedCount: Int = 0,
    val preparedPlanCompletedCount: Int = 0,
    val preparedPlanDismissedCount: Int = 0,
    val wrongTimingCount: Int = 0,
    val repeatDetectedCount: Int = 0,
    val status: PathShiftCycleStatus = PathShiftCycleStatus.Active,
    val cancelledAtMillis: Long? = null,
) {
    init {
        require(cycleId.isNotBlank())
        require(createdAtMillis >= 0L)
        require(lookbackEndedAtMillis > lookbackStartedAtMillis)
        require(forecastWindowEndsAtMillis > forecastWindowStartedAtMillis)
        require(estimatedLowerCount >= 0)
        require(estimatedUpperCount >= estimatedLowerCount)
        require(inputProtectedMomentCount >= 0)
        require(inputDistinctDayCount >= 0)
        require(
            listOf(
                observedProtectedMomentCount,
                preparedPlanSelectedCount,
                preparedPlanStartedCount,
                preparedPlanCompletedCount,
                preparedPlanDismissedCount,
                wrongTimingCount,
                repeatDetectedCount,
            ).all { it >= 0 },
        )
        require((preparedPlanId == null) == (preparedPlanContentRevisionId == null))
        require(preparedAtMillis == null || preparedPlanId != null)
        require(
            commonWindowStartMinute == null ||
                commonWindowStartMinute in 0 until PathShiftTimeWindow.MinutesPerDay,
        )
        require(
            commonWindowEndMinute == null ||
                commonWindowEndMinute in 1..PathShiftTimeWindow.MinutesPerDay,
        )
        require((commonWindowStartMinute == null) == (commonWindowEndMinute == null))
        when (status) {
            PathShiftCycleStatus.Active -> {
                require(reviewFinalisedAtMillis == null)
                require(cancelledAtMillis == null)
            }
            PathShiftCycleStatus.Finalised -> {
                require(reviewFinalisedAtMillis != null)
                require(reviewFinalisedAtMillis >= forecastWindowEndsAtMillis)
                require(cancelledAtMillis == null)
            }
            PathShiftCycleStatus.Cancelled -> {
                require(cancelledAtMillis != null)
                require(reviewFinalisedAtMillis == null)
            }
        }
    }
}

data class PathShiftReviewCounts(
    val observedProtectedMomentCount: Int,
    val preparedPlanSelectedCount: Int,
    val preparedPlanStartedCount: Int,
    val preparedPlanCompletedCount: Int,
    val preparedPlanDismissedCount: Int,
    val wrongTimingCount: Int,
    val repeatDetectedCount: Int,
) {
    init {
        require(
            listOf(
                observedProtectedMomentCount,
                preparedPlanSelectedCount,
                preparedPlanStartedCount,
                preparedPlanCompletedCount,
                preparedPlanDismissedCount,
                wrongTimingCount,
                repeatDetectedCount,
            ).all { it >= 0 },
        )
        require(preparedPlanSelectedCount <= observedProtectedMomentCount)
        require(preparedPlanStartedCount <= preparedPlanSelectedCount)
        require(preparedPlanCompletedCount <= preparedPlanStartedCount)
        require(preparedPlanDismissedCount <= preparedPlanStartedCount)
        require(wrongTimingCount <= observedProtectedMomentCount)
        require(repeatDetectedCount <= observedProtectedMomentCount)
    }
}
