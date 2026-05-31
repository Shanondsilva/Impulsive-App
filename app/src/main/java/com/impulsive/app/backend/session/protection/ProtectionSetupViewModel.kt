package com.impulsive.app.backend.session.protection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProtectionSetupViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = ProtectionSetupRepository(application)

    val state: StateFlow<ProtectionSetupState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProtectionSetupState(),
    )

    fun setUsageAccessEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setUsageAccessEnabled(enabled) }
    }

    fun setSelectedBlockedAppPackageNames(packageNames: Set<String>) {
        viewModelScope.launch { repository.setSelectedBlockedAppPackageNames(packageNames) }
    }

    fun setWebsiteProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setWebsiteProtectionEnabled(enabled) }
    }

    fun setInterruptionPermissionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setInterruptionPermissionEnabled(enabled) }
    }

    fun setUninstallProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setUninstallProtectionEnabled(enabled) }
    }

    fun setNotificationPermissionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setNotificationPermissionEnabled(enabled) }
    }

    fun markSkipped(item: ProtectionSetupItem) {
        viewModelScope.launch { repository.markSkipped(item) }
    }

    fun clearSkipped(item: ProtectionSetupItem) {
        viewModelScope.launch { repository.clearSkipped(item) }
    }

    fun clearProtectionSetup() {
        viewModelScope.launch { repository.clear() }
    }
}
