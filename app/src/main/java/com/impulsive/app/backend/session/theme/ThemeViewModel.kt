package com.impulsive.app.backend.session.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.ThemePreferencesDataSource
import com.impulsive.app.core.util.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val dataSource = ThemePreferencesDataSource(application)

    val themeMode: StateFlow<ThemeMode> = dataSource.themeMode.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.AsPerTime,
    )

    fun setThemeMode(mode: ThemeMode) {
        val modeToStore = if (mode == ThemeMode.System) ThemeMode.AsPerTime else mode
        viewModelScope.launch { dataSource.setThemeMode(modeToStore) }
    }
}
