package com.impulsive.app.backend.session.protection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WebsiteProtectionBlockingCondition {
    BrowserNotSelected,
    DisclosureReviewRequired,
    VpnPermissionRequired,
    CompetingVpnActive,
    PrivateDnsConflict,
    UnsupportedBrowser,
    ProtectionUnavailable,
    Ready,
}

enum class WebsiteProtectionNextAction {
    SelectBrowser,
    ReviewDisclosure,
    RequestVpnPermission,
    OpenVpnSettings,
    OpenPrivateDnsSettings,
    ChooseSupportedBrowser,
    RetryCapabilityCheck,
    None,
}

data class WebsiteProtectionCapabilitySnapshot(
    val capabilitiesLoaded: Boolean,
    val browserSelected: Boolean,
    val selectedBrowserSupported: Boolean,
    val vpnPermissionGranted: Boolean,
    val competingVpnActive: Boolean,
    val privateDnsConflict: Boolean,
    /**
     * The persisted enable intent, not runtime authority. Named explicitly so it
     * is never mistaken for "Website Protection is currently operational".
     */
    val websiteProtectionEnableIntent: Boolean,
    val websiteProtectionDisclosureAccepted: Boolean,
)

data class WebsiteProtectionSetupState(
    val condition: WebsiteProtectionBlockingCondition,
    val nextAction: WebsiteProtectionNextAction,
)

object WebsiteProtectionSetupStatePolicy {
    fun evaluate(snapshot: WebsiteProtectionCapabilitySnapshot): WebsiteProtectionSetupState = when {
        !snapshot.capabilitiesLoaded -> state(
            WebsiteProtectionBlockingCondition.ProtectionUnavailable,
            WebsiteProtectionNextAction.RetryCapabilityCheck,
        )
        !snapshot.browserSelected -> state(
            WebsiteProtectionBlockingCondition.BrowserNotSelected,
            WebsiteProtectionNextAction.SelectBrowser,
        )
        !snapshot.selectedBrowserSupported -> state(
            WebsiteProtectionBlockingCondition.UnsupportedBrowser,
            WebsiteProtectionNextAction.ChooseSupportedBrowser,
        )
        /*
         * A legacy user who already had Website Protection configured on must
         * review the current disclosure before the separate Android VPN
         * permission step. A brand-new user (no enable intent) is not forced
         * here: their Turn On flow already routes through DnsFilterGateScreen.
         */
        snapshot.websiteProtectionEnableIntent &&
            !snapshot.websiteProtectionDisclosureAccepted -> state(
            WebsiteProtectionBlockingCondition.DisclosureReviewRequired,
            WebsiteProtectionNextAction.ReviewDisclosure,
        )
        !snapshot.vpnPermissionGranted -> state(
            WebsiteProtectionBlockingCondition.VpnPermissionRequired,
            WebsiteProtectionNextAction.RequestVpnPermission,
        )
        snapshot.competingVpnActive -> state(
            WebsiteProtectionBlockingCondition.CompetingVpnActive,
            WebsiteProtectionNextAction.OpenVpnSettings,
        )
        snapshot.privateDnsConflict -> state(
            WebsiteProtectionBlockingCondition.PrivateDnsConflict,
            WebsiteProtectionNextAction.OpenPrivateDnsSettings,
        )
        else -> state(
            WebsiteProtectionBlockingCondition.Ready,
            WebsiteProtectionNextAction.None,
        )
    }

    private fun state(
        condition: WebsiteProtectionBlockingCondition,
        action: WebsiteProtectionNextAction,
    ) = WebsiteProtectionSetupState(condition, action)
}

/** Mutable only at the backend capability boundary; UI receives the immutable StateFlow. */
class WebsiteProtectionSetupStateProducer {
    private val _state = MutableStateFlow(
        WebsiteProtectionSetupStatePolicy.evaluate(
            WebsiteProtectionCapabilitySnapshot(
                capabilitiesLoaded = false,
                browserSelected = false,
                selectedBrowserSupported = false,
                vpnPermissionGranted = false,
                competingVpnActive = false,
                privateDnsConflict = false,
                websiteProtectionEnableIntent = false,
                websiteProtectionDisclosureAccepted = false,
            ),
        ),
    )
    val state: StateFlow<WebsiteProtectionSetupState> = _state.asStateFlow()

    fun refresh(snapshot: WebsiteProtectionCapabilitySnapshot) {
        _state.value = WebsiteProtectionSetupStatePolicy.evaluate(snapshot)
    }
}

enum class BlockedSitePrimaryAction { OpenCoordinatorRecommendation }
enum class BlockedSiteQuietFallback { DismissInterruption }

data class BlockedSiteInterruptionState(
    val decisionId: String,
    val primaryAction: BlockedSitePrimaryAction =
        BlockedSitePrimaryAction.OpenCoordinatorRecommendation,
    val quietFallback: BlockedSiteQuietFallback =
        BlockedSiteQuietFallback.DismissInterruption,
) {
    init {
        require(decisionId.isNotBlank())
    }
}
