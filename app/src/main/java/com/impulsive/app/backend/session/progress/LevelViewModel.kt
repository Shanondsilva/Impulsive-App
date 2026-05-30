package com.impulsive.app.backend.session.progress

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.LevelPreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LevelViewModel(application: Application) : AndroidViewModel(application) {
    private val dataSource = LevelPreferencesDataSource(application)

    val currentLevel: StateFlow<Int> = dataSource.currentLevel.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 1,
    )

    fun setLevel(level: Int) {
        viewModelScope.launch { dataSource.setLevel(level.coerceIn(1, 5)) }
    }

    /** Cycles 1 -> 2 -> 3 -> 4 -> 5 -> 1. For demo/testing. */
    fun advanceLevel() {
        val next = if (currentLevel.value >= 5) 1 else currentLevel.value + 1
        setLevel(next)
    }
}
