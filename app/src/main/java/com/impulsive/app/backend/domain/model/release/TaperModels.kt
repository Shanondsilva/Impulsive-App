package com.impulsive.app.backend.domain.model.release

import com.impulsive.app.backend.domain.model.score.UrgeEventRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

const val MinimumDailyUrgeCount = 1
const val TaperObservationDays = 14
const val TaperMinimumObservedDays = 10
const val TaperAcceptCooldownDays = 14L
const val TaperDeclineSuppressDays = 7L
const val TaperUrgePressureToleranceEvents = 7

data class TaperProposal(
    val fromCount: Int,
    val toCount: Int,
    val generatedOn: LocalDate,
)

data class TaperHistoryEntry(
    val date: LocalDate,
    val fromCount: Int,
    val toCount: Int,
)

data class TaperStoreState(
    val lastAcceptedAt: LocalDateTime? = null,
    val lastDeclinedAt: LocalDateTime? = null,
    val proposalsDisabled: Boolean = false,
    val history: List<TaperHistoryEntry> = emptyList(),
)

object TaperEvaluator {

    fun evaluate(
        now: LocalDateTime,
        currentDailyUrgeCount: Int,
        windowOutcomes: List<WindowOutcomeRecord>,
        urgeEvents: List<UrgeEventRecord>,
        lastAcceptedAt: LocalDateTime?,
        lastDeclinedAt: LocalDateTime?,
        proposalsDisabled: Boolean = false,
    ): TaperProposal? {
        if (proposalsDisabled) return null
        if (currentDailyUrgeCount <= MinimumDailyUrgeCount) return null
        val today = now.toLocalDate()

        if (lastAcceptedAt != null &&
            ChronoUnit.DAYS.between(lastAcceptedAt.toLocalDate(), today) < TaperAcceptCooldownDays
        ) {
            return null
        }
        if (lastDeclinedAt != null &&
            ChronoUnit.DAYS.between(lastDeclinedAt.toLocalDate(), today) < TaperDeclineSuppressDays
        ) {
            return null
        }

        val observationStart = today.minusDays((TaperObservationDays - 1).toLong())
        val observedOutcomes = windowOutcomes.filter {
            val day = it.windowStart.toLocalDate()
            !day.isBefore(observationStart) && !day.isAfter(today)
        }
        val outcomesByDay = observedOutcomes.groupBy { it.windowStart.toLocalDate() }
        if (outcomesByDay.size < TaperMinimumObservedDays) return null

        val averageUsedPerObservedDay = outcomesByDay.values
            .map { dayOutcomes -> dayOutcomes.count { it.status == WindowOutcomeStatus.Used } }
            .average()
        if (averageUsedPerObservedDay > (currentDailyUrgeCount - 1).toDouble()) return null

        val recentStart = today.minusDays(6)
        val priorStart = today.minusDays(13)
        val priorEnd = today.minusDays(7)
        val recentUrgeEvents = urgeEvents.count {
            !it.date.isBefore(recentStart) && !it.date.isAfter(today)
        }
        val priorUrgeEvents = urgeEvents.count {
            !it.date.isBefore(priorStart) && !it.date.isAfter(priorEnd)
        }
        if (recentUrgeEvents > priorUrgeEvents + TaperUrgePressureToleranceEvents) return null

        return TaperProposal(
            fromCount = currentDailyUrgeCount,
            toCount = currentDailyUrgeCount - 1,
            generatedOn = today,
        )
    }
}
