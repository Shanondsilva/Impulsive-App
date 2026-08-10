package com.impulsive.app.backend.service.protection

/**
 * What the non-Focus foreground notification may truthfully claim.
 *
 * APP-015: the monitoring notification used to be a single fixed string saying
 * protection was on, posted from configuration alone. Usage Access can be
 * revoked in system Settings at any time, which stops protected-app
 * interception working while leaving that claim on screen. These modes describe
 * what is *operational right now*, which is not the same question as what the
 * user has configured.
 */
internal enum class ProtectionMonitoringNotificationMode {
    /** Nothing is confirmed operational yet, or the service is about to stop. */
    Checking,
    AppProtection,
    WebsiteProtection,
    AppAndWebsiteProtection,
}

/**
 * Resolves the notification mode from live operational facts.
 *
 * [appProtectionOperational] must come from the live Usage Access grant, never
 * from persisted setup state -- the persisted flag records what was configured,
 * not whether Android still permits it.
 */
internal fun resolveProtectionMonitoringNotificationMode(
    setupLoaded: Boolean,
    appProtectionOperational: Boolean,
    websiteProtectionOperational: Boolean,
): ProtectionMonitoringNotificationMode = when {
    !setupLoaded -> ProtectionMonitoringNotificationMode.Checking

    appProtectionOperational && websiteProtectionOperational ->
        ProtectionMonitoringNotificationMode.AppAndWebsiteProtection

    appProtectionOperational -> ProtectionMonitoringNotificationMode.AppProtection

    websiteProtectionOperational -> ProtectionMonitoringNotificationMode.WebsiteProtection

    else -> ProtectionMonitoringNotificationMode.Checking
}
