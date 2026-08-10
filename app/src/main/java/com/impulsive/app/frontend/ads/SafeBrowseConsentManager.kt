package com.impulsive.app.frontend.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.impulsive.app.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether a rewarded ad may currently be requested, and why not when it cannot be. */
sealed interface SafeBrowseConsentState {
    data object Unknown : SafeBrowseConsentState

    data class Resolved(
        val canRequestAds: Boolean,
        val privacyOptionsRequired: Boolean,
    ) : SafeBrowseConsentState

    data class Failed(
        val message: String,
    ) : SafeBrowseConsentState
}

/**
 * The single app-wide [SafeBrowseConsentManager] instance, provided once at the navigation
 * root. Every consumer reads through this instead of constructing its own instance, so the
 * UMP SDK's [ConsentInformation] is never backed by two independent managers racing each
 * other. `null` only where the provider genuinely has not been installed (previews, demo
 * navigation) -- consumers fall back to a screen-local instance in that case.
 */
val LocalSafeBrowseConsentManager = staticCompositionLocalOf<SafeBrowseConsentManager?> { null }

/**
 * Provides the single process-wide [SafeBrowseConsentManager]. Every call site -- the
 * navigation root, Safe Browse, Settings -- must go through [get] instead of constructing
 * the manager directly, so the UMP SDK's [ConsentInformation] is never backed by two
 * independent, racing manager instances.
 */
object SafeBrowseConsentManagerProvider {
    @Volatile
    private var instance: SafeBrowseConsentManager? = null

    fun get(context: Context): SafeBrowseConsentManager =
        instance ?: synchronized(this) {
            instance ?: SafeBrowseConsentManager(context.applicationContext).also { created ->
                instance = created
            }
        }
}

/**
 * Owns Google's User Messaging Platform consent flow for Safe Browse's optional rewarded
 * ad. Consent is prepared while the user is on the Safe Browse unlock screen — never
 * during the secured browser session, and never during a trigger-interruption screen.
 *
 * Mobile Ads must never be initialised before [SafeBrowseConsentState.Resolved] confirms
 * [SafeBrowseConsentState.Resolved.canRequestAds].
 *
 * The info-update request and the consent-form presentation are deliberately separate
 * calls: [requestConsentInfoUpdate] is safe to call from anywhere (including a
 * trigger-interruption route) because it never shows UI, while [showRequiredFormIfAppropriate]
 * must only be called from a calm route such as the Safe Browse unlock screen, since it can
 * present a full-screen consent form.
 *
 * Only ever constructed by [SafeBrowseConsentManagerProvider] -- never directly by a screen
 * or ViewModel.
 */
class SafeBrowseConsentManager internal constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(appContext)

    private val _state = MutableStateFlow<SafeBrowseConsentState>(SafeBrowseConsentState.Unknown)
    val state: StateFlow<SafeBrowseConsentState> = _state.asStateFlow()

    /** True while a [requestConsentInfoUpdate] network call is in flight. */
    private val infoRequestInFlight = AtomicBoolean(false)

    /** True once a [requestConsentInfoUpdate] call has completed (successfully or not). */
    private val infoUpdateCompleted = AtomicBoolean(false)

    /** True only if the most recently completed info update succeeded. */
    private val infoUpdateSucceeded = AtomicBoolean(false)

    /** True once [showRequiredFormIfAppropriate] has attempted a form for the current info update. */
    private val requiredFormAttempted = AtomicBoolean(false)

    /** True while the required consent form is currently on screen. */
    private val requiredFormShowing = AtomicBoolean(false)

    private companion object {
        const val ConsentUpdateErrorMessage = "Ad privacy choices could not be updated."
        const val ConsentFormErrorMessage = "Ad privacy choices could not be opened."
    }

    /**
     * Requests the current consent status. Shows no UI. Only performs the actual network
     * request once per process launch -- subsequent calls are a no-op so this is safe to
     * call from every route that might need a resolved [state] without repeatedly hitting
     * the network or re-triggering SDK callbacks.
     */
    fun requestConsentInfoUpdate(activity: Activity) {
        if (infoUpdateCompleted.get()) {
            return
        }

        if (!infoRequestInFlight.compareAndSet(false, true)) {
            return
        }

        val params = ConsentRequestParameters.Builder()
            .apply { applyDebugSettingsIfConfigured(activity) }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                infoRequestInFlight.set(false)
                infoUpdateSucceeded.set(true)
                infoUpdateCompleted.set(true)
                requiredFormAttempted.set(false)
                publishResolvedState()
            },
            {
                infoRequestInFlight.set(false)
                infoUpdateSucceeded.set(false)
                infoUpdateCompleted.set(true)
                requiredFormAttempted.set(false)
                publishStateAfterFailure(ConsentUpdateErrorMessage)
            },
        )
    }

    /** Re-requests consent info after a prior failure. A no-op while a request is in flight. */
    fun retryConsentInfoUpdate(activity: Activity) {
        if (infoRequestInFlight.get()) {
            return
        }

        infoUpdateCompleted.set(false)
        infoUpdateSucceeded.set(false)
        requiredFormAttempted.set(false)

        requestConsentInfoUpdate(activity)
    }

    /**
     * Shows Google's official consent form only if the UMP SDK determines one is required.
     * Call this only from a calm route (the Safe Browse unlock screen) -- never from a
     * trigger-interruption or recovery-task screen, and never while the secured browser is
     * active.
     */
    fun showRequiredFormIfAppropriate(activity: Activity) {
        if (!infoUpdateCompleted.get() || !infoUpdateSucceeded.get()) {
            return
        }

        if (!requiredFormAttempted.compareAndSet(false, true)) {
            return
        }

        if (!requiredFormShowing.compareAndSet(false, true)) {
            return
        }

        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            requiredFormShowing.set(false)

            if (formError == null) {
                publishResolvedState()
            } else {
                publishStateAfterFailure(ConsentFormErrorMessage)
            }
        }
    }

    fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Shows Google's official privacy-options form. Never a custom-built consent form. */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (String?) -> Unit = {},
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError == null) {
                onDismissed(null)
                publishResolvedState()
            } else {
                onDismissed(ConsentFormErrorMessage)
                publishStateAfterFailure(ConsentFormErrorMessage)
            }
        }
    }

    private fun publishResolvedState() {
        _state.value = SafeBrowseConsentState.Resolved(
            canRequestAds = consentInformation.canRequestAds(),
            privacyOptionsRequired = isPrivacyOptionsRequired(),
        )
    }

    /**
     * A failure never simply blanks out a previously resolved, still-valid consent state --
     * it only surfaces as [SafeBrowseConsentState.Failed] when the SDK itself confirms ads
     * genuinely cannot be requested. Never exposes the raw UMP [formError] message.
     */
    private fun publishStateAfterFailure(stableMessage: String) {
        if (consentInformation.canRequestAds()) {
            publishResolvedState()
        } else {
            _state.value = SafeBrowseConsentState.Failed(stableMessage)
        }
    }

    /**
     * Debug consent settings are never a side effect of `isDebuggable` alone. All three of
     * a debuggable build, [BuildConfig.IMPULSIVE_UMP_DEBUG_EEA] being explicitly true, and a
     * non-blank [BuildConfig.IMPULSIVE_UMP_TEST_DEVICE_HASH] are required before EEA debug
     * geography is ever forced.
     */
    private fun ConsentRequestParameters.Builder.applyDebugSettingsIfConfigured(activity: Activity) {
        val isDebuggable =
            (activity.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val testDeviceHash = BuildConfig.IMPULSIVE_UMP_TEST_DEVICE_HASH.trim()

        if (!isDebuggable || !BuildConfig.IMPULSIVE_UMP_DEBUG_EEA || testDeviceHash.isEmpty()) {
            return
        }

        val debugSettings = ConsentDebugSettings.Builder(activity)
            .addTestDeviceHashedId(testDeviceHash)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .build()

        setConsentDebugSettings(debugSettings)
    }
}
