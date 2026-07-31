package com.impulsive.app.backend.session.adaptive

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.repository.adaptive.RoomAdaptiveDecisionRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRehearsalRepository
import com.impulsive.app.backend.data.repository.adaptive.RoomMomentPlanRepository
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksForMeBuilder
import com.impulsive.app.backend.domain.engine.adaptive.WhatWorksForMeReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class WhatWorksForMeUiState(
    val loading: Boolean = true,
    val report: WhatWorksForMeReport? = null,
    val message: String? = null,
)

class WhatWorksForMeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val decisions = RoomAdaptiveDecisionRepository(
        database.adaptiveDecisionDao(),
    )
    private val rehearsals = RoomMomentPlanRehearsalRepository(
        database.momentPlanRehearsalDao(),
    )
    private val plans = RoomMomentPlanRepository(database.momentPlanDao())
    private val _state = MutableStateFlow(WhatWorksForMeUiState())
    val state: StateFlow<WhatWorksForMeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                decisions.observeRecentDecisions(MaximumLocalRecords),
                rehearsals.observeRecentCompleted(MaximumLocalRecords),
                plans.observeAll(),
            ) { decisionRecords, rehearsalRecords, planRecords ->
                WhatWorksForMeBuilder.build(
                    decisions = decisionRecords,
                    rehearsals = rehearsalRecords,
                    plans = planRecords,
                    nowMillis = System.currentTimeMillis(),
                )
            }.catch { error ->
                if (error is CancellationException) throw error
                Log.w(
                    "WhatWorks",
                    "load failed (${error.javaClass.simpleName})",
                )
                _state.value = WhatWorksForMeUiState(
                    loading = false,
                    message = "Your personal patterns could not be loaded.",
                )
            }.collect { report ->
                _state.value = WhatWorksForMeUiState(
                    loading = false,
                    report = report,
                )
            }
        }
    }

    private companion object {
        const val MaximumLocalRecords = 2_000
    }
}
