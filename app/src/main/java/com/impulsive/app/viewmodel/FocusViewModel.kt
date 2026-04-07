package com.impulsive.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.data.db.TriggerLog
import com.impulsive.app.data.db.WeeklyTarget
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.util.WeekUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FocusUiState(
    val currentWeek: WeeklyTarget? = null,
    val todayLogs: List<TriggerLog> = emptyList(),
    val weekLogs: List<TriggerLog> = emptyList(),
    /** 7 values Mon-Sun: total trigger log entries per day */
    val interceptionsByDay: List<Int> = List(7) { 0 },
    /** 7 values Mon-Sun: Continue-outcome entries per day */
    val sessionsByDay: List<Int> = List(7) { 0 },
    val hasUncheckedWeek: Boolean = false
)

class FocusViewModel(private val repo: ImpulsiveRepository) : ViewModel() {

    private val _state = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = _state.asStateFlow()

    init {
        val monday = WeekUtil.currentWeekStart()
        val sunday = monday + 7 * 24 * 60 * 60 * 1000L

        viewModelScope.launch {
            combine(
                repo.observeWeeklyTarget(monday),
                repo.observeTriggersForRange(monday, sunday)
            ) { week, logs ->
                val byDay = Array(7) { 0 }
                val sessByDay = Array(7) { 0 }
                logs.forEach { log ->
                    val dayIdx = ((log.timestamp - monday) / (24 * 60 * 60 * 1000L))
                        .toInt()
                        .coerceIn(0, 6)
                    byDay[dayIdx]++
                    if (log.outcome == "Continue") sessByDay[dayIdx]++
                }
                FocusUiState(
                    currentWeek = week,
                    weekLogs = logs,
                    interceptionsByDay = byDay.toList(),
                    sessionsByDay = sessByDay.toList()
                )
            }.collect { _state.value = it }
        }
    }
}
