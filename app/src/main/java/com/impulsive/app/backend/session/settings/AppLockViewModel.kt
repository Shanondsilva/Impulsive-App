package com.impulsive.app.backend.session.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppLockViewModel(application: Application) : AndroidViewModel(application) {
    private val dataSource = AppLockPreferencesDataSource(application)

    val enabled: StateFlow<Boolean> = dataSource.enabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun disable() {
        viewModelScope.launch { dataSource.setEnabled(false) }
    }
}
