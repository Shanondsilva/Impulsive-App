package com.impulsive.app.backend.session.protection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.service.protection.ImpulsiveVpnController
import com.impulsive.app.backend.service.protection.InterruptionNotificationLimiter
import com.impulsive.app.backend.service.protection.ProtectionInterruptionOverlay
import com.impulsive.app.backend.service.protection.ProtectionNotificationHelper
import com.impulsive.app.backend.service.protection.ProtectionServiceController
import com.impulsive.app.backend.service.protection.ProtectionServiceOperationalState
import com.impulsive.app.backend.service.protection.ProtectionServiceOperationalStateStore
import com.impulsive.app.backend.service.protection.ProtectionServiceStartOrigin
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

    val serviceOperationalState:
        StateFlow<
            ProtectionServiceOperationalState
        > =
        ProtectionServiceOperationalStateStore
            .state

    fun setUsageAccessEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setUsageAccessEnabled(enabled) }
    }

    fun setSelectedBlockedAppPackageNames(packageNames: Set<String>) {
        viewModelScope.launch {
            val wasEnabled = state.value.selectedBlockedAppPackageNames.isNotEmpty()
            val willBeEnabled = packageNames.isNotEmpty()

            repository.setSelectedBlockedAppPackageNames(packageNames)

            if (willBeEnabled && state.value.appProtectionMonitorEnabled) {
                ProtectionServiceController.start(
                    context = getApplication(),
                    origin =
                        ProtectionServiceStartOrigin
                            .VisibleApp,
                    showTemporaryNotification = !wasEnabled,
                )
            } else {
                ProtectionServiceController.start(
                    context = getApplication(),
                    origin =
                        ProtectionServiceStartOrigin
                            .VisibleApp,
                )
            }
        }
    }

    fun setWebsiteProtectedAppPackageNames(packageNames: Set<String>) {
        viewModelScope.launch {
            repository.setWebsiteProtectedAppPackageNames(packageNames)

            if (state.value.websiteProtectionEnabled) {
                ImpulsiveVpnController.refreshAllowedApplications(getApplication())
            }
        }
    }

    fun setAppProtectionMonitorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAppProtectionMonitorEnabled(enabled)
            if (!enabled) {
                ProtectionInterruptionOverlay.dismissOwned(ProtectionInterruptionOverlay.Owner.AppMonitor)
                ProtectionNotificationHelper(getApplication()).cancelBlockedAttemptNotification()
                InterruptionNotificationLimiter.clearAppEncounters()
            }
            ProtectionServiceController.start(
                context = getApplication(),
                origin =
                    ProtectionServiceStartOrigin
                        .VisibleApp,
            )
        }
    }

    fun setWebsiteProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWebsiteProtectionEnabled(enabled)

            if (enabled) {
                ProtectionServiceController.start(
                    context = getApplication(),
                    origin =
                        ProtectionServiceStartOrigin
                            .VisibleApp,
                )
            } else {
                ImpulsiveVpnController.stop(getApplication())
            }
        }
    }

    fun setWebsiteProtectionAlwaysOn(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWebsiteProtectionAlwaysOn(enabled)

            if (state.value.websiteProtectionEnabled) {
                ProtectionServiceController.start(
                    context = getApplication(),
                    origin =
                        ProtectionServiceStartOrigin
                            .VisibleApp,
                )
            }
        }
    }

    fun setInterruptionPermissionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setInterruptionPermissionEnabled(enabled) }
    }

    fun setBackgroundActivityEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setBackgroundActivityEnabled(enabled) }
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
