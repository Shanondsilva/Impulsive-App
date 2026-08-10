package com.impulsive.app.backend.session.protection

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.ProtectionSetupRepository
import com.impulsive.app.backend.data.local.device.DnsFilterGate
import com.impulsive.app.backend.data.local.device.InstalledAppScanner
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupItem
import com.impulsive.app.backend.domain.model.protection.ProtectionSetupState
import com.impulsive.app.backend.domain.model.protection.DnsFilterGateEvaluator
import com.impulsive.app.backend.domain.model.protection.ProtectedAppCategory
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
    private val dnsFilterGate = DnsFilterGate(application)
    private val installedAppScanner = InstalledAppScanner(application)
    private val websiteSetupStateProducer = WebsiteProtectionSetupStateProducer()

    val websiteSetupState: StateFlow<WebsiteProtectionSetupState> =
        websiteSetupStateProducer.state

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

            if (willBeEnabled && state.value.configurationDrivenAppProtectionConsented) {
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
            refreshWebsiteProtectionSetupState(packageNames)

            if (
                state.value
                    .websiteProtectionRuntimeEnabled
            ) {
                ImpulsiveVpnController.refreshAllowedApplications(getApplication())
            }
        }
    }

    fun refreshWebsiteProtectionSetupState(
        selectedBrowserPackageNames: Set<String> = state.value.websiteProtectedAppPackageNames,
    ) {
        val gateResult = dnsFilterGate.evaluate()
        val supportedInstalledBrowsers = installedAppScanner.getLaunchableAppCandidates()
            .asSequence()
            .filter { it.category == ProtectedAppCategory.BrowserSearch }
            .mapTo(mutableSetOf()) { it.packageName }
        websiteSetupStateProducer.refresh(
            WebsiteProtectionCapabilitySnapshot(
                capabilitiesLoaded = true,
                browserSelected = selectedBrowserPackageNames.isNotEmpty(),
                selectedBrowserSupported =
                    selectedBrowserPackageNames.isNotEmpty() &&
                        selectedBrowserPackageNames.all(supportedInstalledBrowsers::contains),
                vpnPermissionGranted =
                    ImpulsiveVpnController.consentIntent(getApplication()) == null,
                competingVpnActive =
                    gateResult.blockers.contains(DnsFilterGateEvaluator.Blocker.AnotherVpnActive) ||
                        gateResult.blockers.contains(
                            DnsFilterGateEvaluator.Blocker.LockdownModeActive,
                        ),
                privateDnsConflict = gateResult.blockers.contains(
                    DnsFilterGateEvaluator.Blocker.PrivateDnsActive,
                ),
                websiteProtectionEnableIntent =
                    state.value.websiteProtectionEnabled,
                websiteProtectionDisclosureAccepted =
                    state.value.websiteProtectionDisclosureAccepted,
            ),
        )
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

    fun setProtectionMonitorTransitionCompleted(completed: Boolean) {
        viewModelScope.launch {
            repository.setProtectionMonitorTransitionCompleted(completed)
            if (completed) {
                ProtectionServiceController.start(
                    context = getApplication(),
                    origin =
                        ProtectionServiceStartOrigin
                            .VisibleApp,
                )
            }
        }
    }

    suspend fun acceptCurrentWebsiteProtectionDisclosure():
        Boolean =
        runCatching {
            repository
                .acceptCurrentWebsiteProtectionDisclosure()

            true
        }.getOrDefault(
            false,
        )

    suspend fun enableWebsiteProtectionAfterDisclosure():
        Boolean {
        val enabled =
            runCatching {
                repository
                    .setWebsiteProtectionEnabled(
                        true,
                    )
            }.getOrDefault(
                false,
            )

        if (!enabled) {
            return false
        }

        ProtectionServiceController.start(
            context =
                getApplication(),
            origin =
                ProtectionServiceStartOrigin
                    .VisibleApp,
        )

        return true
    }

    fun setWebsiteProtectionEnabled(
        enabled:
            Boolean,
    ) {
        viewModelScope.launch {
            if (enabled) {
                enableWebsiteProtectionAfterDisclosure()
                return@launch
            }

            repository
                .setWebsiteProtectionEnabled(
                    false,
                )

            ProtectionNotificationHelper(
                getApplication(),
            )
                .cancelBlockedAttemptNotification()

            InterruptionNotificationLimiter
                .clearAppEncounters()

            ImpulsiveVpnController
                .stop(
                    getApplication(),
                )
        }
    }

    fun setWebsiteProtectionAlwaysOn(enabled: Boolean) {
        viewModelScope.launch {
            repository.setWebsiteProtectionAlwaysOn(enabled)

            if (
                state.value
                    .websiteProtectionRuntimeEnabled
            ) {
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
