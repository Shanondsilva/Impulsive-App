package com.impulsive.app.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.data.db.TriggerLog
import com.impulsive.app.data.repository.ImpulsiveRepository
import com.impulsive.app.service.SessionTimerService
import com.impulsive.app.ui.timer.SessionTimerActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class InterceptScreen {
    HOLD, TRIGGER_ROUTING, INTERVENTION, GATEWAY, WALK_AWAY
}

data class InterceptState(
    val screen: InterceptScreen = InterceptScreen.HOLD,
    val holdProgress: Float = 0f,
    val holdSeconds: Float = 0f,
    val isHolding: Boolean = false,
    val holdComplete: Boolean = false,
    val selectedTrigger: String = "",
    val sessionsLeftThisWeek: Int = 0,
    val interceptedPackage: String = ""
)

class InterceptViewModel(
    private val repository: ImpulsiveRepository
) : ViewModel() {

    private val _state = MutableStateFlow(InterceptState())
    val state: StateFlow<InterceptState> = _state.asStateFlow()

    private var holdJob: Job? = null
    private var holdStartTime: Long = 0L
    private val holdDurationMs = 15_000L
    private val tickMs = 50L

    init {
        loadSessionCount()
    }

    private fun loadSessionCount() {
        viewModelScope.launch {
            val weekStart = getWeekStart()
            // Seed WeeklyTarget for this week if it doesn't exist yet
            val profile = repository.getProfile()
            val baseline = profile?.baselineSessionsPerWeek ?: 7
            repository.ensureWeeklyTarget(weekStart, baseline)

            repository.observeWeeklyTarget(weekStart).collect { target ->
                val remaining = (target?.allowedSessions ?: baseline) - (target?.usedSessions ?: 0)
                _state.update { it.copy(sessionsLeftThisWeek = remaining.coerceAtLeast(0)) }
            }
        }
    }

    fun onPressStart() {
        if (_state.value.holdComplete) return
        holdStartTime = System.currentTimeMillis()
        _state.update { it.copy(isHolding = true) }
        holdJob = viewModelScope.launch {
            while (true) {
                val elapsed = System.currentTimeMillis() - holdStartTime
                val progress = (elapsed.toFloat() / holdDurationMs).coerceIn(0f, 1f)
                val secondsLeft = ((holdDurationMs - elapsed) / 1000f).coerceAtLeast(0f)
                _state.update { it.copy(holdProgress = progress, holdSeconds = secondsLeft) }
                if (elapsed >= holdDurationMs) {
                    _state.update { it.copy(holdComplete = true, isHolding = false) }
                    delay(300)
                    _state.update { it.copy(screen = InterceptScreen.TRIGGER_ROUTING) }
                    break
                }
                delay(tickMs)
            }
        }
    }

    fun onPressRelease() {
        if (_state.value.holdComplete) return
        holdJob?.cancel()
        holdJob = null
        _state.update { it.copy(isHolding = false, holdProgress = 0f, holdSeconds = 0f) }
    }

    fun selectTrigger(trigger: String) {
        _state.update { it.copy(selectedTrigger = trigger, screen = InterceptScreen.INTERVENTION) }
        viewModelScope.launch {
            repository.logTrigger(
                TriggerLog(
                    timestamp = System.currentTimeMillis(),
                    triggerType = trigger,
                    outcome = "Intercepted",
                    holdDurationSeconds = 15f
                )
            )
            repository.logEval(phase = 2, name = "trigger_selected", value = trigger)
        }
    }

    fun completeIntervention() {
        _state.update { it.copy(screen = InterceptScreen.GATEWAY) }
    }

    fun walkAway() {
        viewModelScope.launch {
            repository.logTrigger(
                TriggerLog(
                    timestamp = System.currentTimeMillis(),
                    triggerType = _state.value.selectedTrigger,
                    outcome = "WalkAway",
                    holdDurationSeconds = 15f
                )
            )
            repository.logEval(phase = 2, name = "friction_outcome", value = "WalkAway")
        }
        _state.update { it.copy(screen = InterceptScreen.WALK_AWAY) }
    }

    fun setInterceptedPackage(pkg: String) {
        _state.update { it.copy(interceptedPackage = pkg) }
    }

    fun continueSession(context: Context) {
        viewModelScope.launch {
            val weekStart = getWeekStart()
            repository.incrementSessionsUsed(weekStart)
            repository.logEval(phase = 2, name = "friction_outcome", value = "Continue")
        }
        SessionTimerService.start(context, _state.value.interceptedPackage)
        context.startActivity(
            Intent(context, SessionTimerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }

    private fun getWeekStart(): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
