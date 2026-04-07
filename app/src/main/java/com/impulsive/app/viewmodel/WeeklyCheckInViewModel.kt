package com.impulsive.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.data.db.TriggerLog
import com.impulsive.app.data.db.WeeklyTarget
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.engine.TaperingEngine
import com.impulsive.app.util.WeekUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CheckInUiState(
    val lastWeek: WeeklyTarget? = null,
    val lastWeekLogs: List<TriggerLog> = emptyList(),
    val interceptionsByDay: List<Int> = List(7) { 0 },
    val sessionsByDay: List<Int> = List(7) { 0 },
    val suggestedNextAllowed: Int = 1,
    val shouldTaper: Boolean = true,
    val isComplete: Boolean = false
)

class WeeklyCheckInViewModel(private val repo: ImpulsiveRepository) : ViewModel() {

    private val _state = MutableStateFlow(CheckInUiState())
    val state: StateFlow<CheckInUiState> = _state.asStateFlow()

    private val lastMonday = WeekUtil.currentWeekStart()
    private val lastSunday = lastMonday + 7L * 24 * 60 * 60 * 1000

    init {
        viewModelScope.launch {
            combine(
                repo.observeWeeklyTarget(lastMonday),
                repo.observeTriggersForRange(lastMonday, lastSunday)
            ) { week, logs ->
                val byDay = Array(7) { 0 }
                val sessByDay = Array(7) { 0 }
                logs.forEach { log ->
                    val dayIdx = ((log.timestamp - lastMonday) / (24 * 60 * 60 * 1000L))
                        .toInt()
                        .coerceIn(0, 6)
                    byDay[dayIdx]++
                    if (log.outcome == "Continue") sessByDay[dayIdx]++
                }
                val allowed = week?.allowedSessions ?: 7
                val used = week?.usedSessions ?: logs.count { it.outcome == "Continue" }
                CheckInUiState(
                    lastWeek = week,
                    lastWeekLogs = logs,
                    interceptionsByDay = byDay.toList(),
                    sessionsByDay = sessByDay.toList(),
                    suggestedNextAllowed = TaperingEngine.calculateNextTarget(allowed),
                    shouldTaper = TaperingEngine.shouldTaper(used, allowed)
                )
            }.collect { _state.value = it }
        }
    }

    fun acceptTaper() {
        viewModelScope.launch {
            val nextMonday = WeekUtil.nextWeekStart(lastMonday)
            val nextAllowed = _state.value.suggestedNextAllowed
                .let { if (_state.value.shouldTaper) it else _state.value.lastWeek?.allowedSessions ?: it }
            repo.insertOrReplaceWeeklyTarget(
                WeeklyTarget(
                    weekStartDate = nextMonday,
                    allowedSessions = nextAllowed,
                    usedSessions = 0
                )
            )
            repo.logEval(5, "check_in_taper_accepted", "next_allowed=$nextAllowed week=$nextMonday")
            _state.value = _state.value.copy(isComplete = true)
        }
    }

    fun resetComplete() {
        _state.value = _state.value.copy(isComplete = false)
    }

    fun keepCurrent(reason: String) {
        viewModelScope.launch {
            val nextMonday = WeekUtil.nextWeekStart(lastMonday)
            val currentAllowed = _state.value.lastWeek?.allowedSessions ?: 7
            repo.insertOrReplaceWeeklyTarget(
                WeeklyTarget(
                    weekStartDate = nextMonday,
                    allowedSessions = currentAllowed,
                    usedSessions = 0,
                    stallReason = reason
                )
            )
            repo.logEval(5, "check_in_stall", "reason=$reason kept=$currentAllowed week=$nextMonday")
            _state.value = _state.value.copy(isComplete = true)
        }
    }
}
