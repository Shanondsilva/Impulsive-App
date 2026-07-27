package com.impulsive.app.backend.service.protection

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.system.OsConstants
import com.impulsive.app.backend.domain.model.protection.DnsPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException

internal const val RecentForegroundWebsiteBrowserFreshnessMillis = 3_000L

internal data class DnsConnectionTuple(
    val protocol: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val destinationAddress: InetAddress,
    val destinationPort: Int,
)

internal sealed interface ExactWebsitePackageAttribution {
    data class SelectedPackage(
        val packageName: String,
    ) : ExactWebsitePackageAttribution

    data object Unavailable : ExactWebsitePackageAttribution
    data object Ambiguous : ExactWebsitePackageAttribution
    data object NonSelectedOwner : ExactWebsitePackageAttribution
}

internal data class RecentForegroundWebsiteBrowser(
    val packageName: String,
    val observedAtEpochMillis: Long,
) {
    fun isFresh(nowEpochMillis: Long): Boolean =
        nowEpochMillis >= observedAtEpochMillis &&
            nowEpochMillis - observedAtEpochMillis <=
            RecentForegroundWebsiteBrowserFreshnessMillis
}

internal object RecentForegroundWebsiteBrowserRegistry {
    @Volatile
    private var observation: RecentForegroundWebsiteBrowser? = null

    fun observe(
        packageName: String,
        observedAtEpochMillis: Long,
    ) {
        observation = RecentForegroundWebsiteBrowser(
            packageName = packageName,
            observedAtEpochMillis = observedAtEpochMillis,
        )
    }

    fun freshObservation(
        nowEpochMillis: Long,
    ): RecentForegroundWebsiteBrowser? =
        observation?.takeIf { recent -> recent.isFresh(nowEpochMillis) }

    fun clear() {
        observation = null
    }
}

internal enum class WebsiteIncidentAttributionReason {
    ExactOwnerMatchesCurrentForeground,
    ExactOwnerMatchesRecentForeground,
    CurrentForegroundUsedBecauseOwnerUnavailable,
    RecentForegroundUsedBecauseOwnerUnavailable,
    CurrentForegroundUsedBecauseOwnerAmbiguous,
    RecentForegroundUsedBecauseOwnerAmbiguous,
    RejectedExactDifferentBrowser,
    RejectedExactOwnerNotEligible,
    RejectedNoCurrentOrRecentBrowser,
}

internal data class WebsiteIncidentAttributionDecision(
    val packageName: String?,
    val reason: WebsiteIncidentAttributionReason,
)

internal fun decideWebsiteIncidentAttribution(
    exactAttribution: ExactWebsitePackageAttribution,
    currentForegroundPackage: String?,
    recentForegroundBrowser: RecentForegroundWebsiteBrowser?,
    websiteProtectedPackages: Set<String>,
    vpnAllowedPackages: Set<String>,
    nowEpochMillis: Long,
): WebsiteIncidentAttributionDecision {
    val protected = websiteProtectedPackages.normalizedPackageSet()
    val allowed = vpnAllowedPackages.normalizedPackageSet()
    val eligible = protected.intersect(allowed)
    val current = currentForegroundPackage
        ?.trim()
        ?.takeIf { packageName -> packageName in eligible }
    val recent = recentForegroundBrowser
        ?.takeIf { observation -> observation.isFresh(nowEpochMillis) }
        ?.packageName
        ?.trim()
        ?.takeIf { packageName -> packageName in eligible }

    return when (exactAttribution) {
        is ExactWebsitePackageAttribution.SelectedPackage -> {
            val exact = exactAttribution.packageName.trim()
            when {
                exact !in eligible ->
                    WebsiteIncidentAttributionDecision(
                        packageName = null,
                        reason = WebsiteIncidentAttributionReason.RejectedExactOwnerNotEligible,
                    )

                current != null && exact == current ->
                    WebsiteIncidentAttributionDecision(
                        packageName = exact,
                        reason = WebsiteIncidentAttributionReason.ExactOwnerMatchesCurrentForeground,
                    )

                current != null ->
                    WebsiteIncidentAttributionDecision(
                        packageName = null,
                        reason = WebsiteIncidentAttributionReason.RejectedExactDifferentBrowser,
                    )

                recent != null && exact == recent ->
                    WebsiteIncidentAttributionDecision(
                        packageName = exact,
                        reason = WebsiteIncidentAttributionReason.ExactOwnerMatchesRecentForeground,
                    )

                recent != null ->
                    WebsiteIncidentAttributionDecision(
                        packageName = null,
                        reason = WebsiteIncidentAttributionReason.RejectedExactDifferentBrowser,
                    )

                else ->
                    WebsiteIncidentAttributionDecision(
                        packageName = null,
                        reason = WebsiteIncidentAttributionReason.RejectedNoCurrentOrRecentBrowser,
                    )
            }
        }

        ExactWebsitePackageAttribution.NonSelectedOwner ->
            WebsiteIncidentAttributionDecision(
                packageName = null,
                reason = WebsiteIncidentAttributionReason.RejectedExactOwnerNotEligible,
            )

        ExactWebsitePackageAttribution.Unavailable ->
            foregroundFallbackDecision(
                current = current,
                recent = recent,
                currentReason =
                    WebsiteIncidentAttributionReason.CurrentForegroundUsedBecauseOwnerUnavailable,
                recentReason =
                    WebsiteIncidentAttributionReason.RecentForegroundUsedBecauseOwnerUnavailable,
            )

        ExactWebsitePackageAttribution.Ambiguous ->
            foregroundFallbackDecision(
                current = current,
                recent = recent,
                currentReason =
                    WebsiteIncidentAttributionReason.CurrentForegroundUsedBecauseOwnerAmbiguous,
                recentReason =
                    WebsiteIncidentAttributionReason.RecentForegroundUsedBecauseOwnerAmbiguous,
            )
    }
}

private fun foregroundFallbackDecision(
    current: String?,
    recent: String?,
    currentReason: WebsiteIncidentAttributionReason,
    recentReason: WebsiteIncidentAttributionReason,
): WebsiteIncidentAttributionDecision =
    when {
        current != null ->
            WebsiteIncidentAttributionDecision(current, currentReason)
        recent != null ->
            WebsiteIncidentAttributionDecision(recent, recentReason)
        else ->
            WebsiteIncidentAttributionDecision(
                packageName = null,
                reason = WebsiteIncidentAttributionReason.RejectedNoCurrentOrRecentBrowser,
            )
    }

internal fun dnsConnectionTupleFromPacket(
    packet: DnsPacket.Udp4,
): DnsConnectionTuple? =
    try {
        DnsConnectionTuple(
            protocol = OsConstants.IPPROTO_UDP,
            sourceAddress = InetAddress.getByAddress(packet.sourceIp),
            sourcePort = packet.sourcePort,
            destinationAddress = InetAddress.getByAddress(packet.destIp),
            destinationPort = packet.destPort,
        )
    } catch (_: UnknownHostException) {
        null
    }

internal fun selectAttributedWebsitePackage(
    ownerPackages: Set<String>,
    selectedPackages: Set<String>,
): String? {
    val selected = selectedPackages.normalizedPackageSet()
    val selectedOwnerPackages = ownerPackages
        .normalizedPackageSet()
        .intersect(selected)

    return selectedOwnerPackages.singleOrNull()
}


internal class WebsiteProtectionPackageAttributor(
    private val connectivityManager: ConnectivityManager?,
    private val packageManager: PackageManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    fun resolveExact(
        tuple: DnsConnectionTuple?,
        selectedPackages: Set<String>,
    ): ExactWebsitePackageAttribution {
        val selected = selectedPackages.normalizedPackageSet()
        if (selected.isEmpty() || tuple == null) {
            return ExactWebsitePackageAttribution.Unavailable
        }

        val ownerPackages = resolveOwnerPackages(tuple)
            ?: return ExactWebsitePackageAttribution.Unavailable
        val selectedOwnerPackages = ownerPackages
            .normalizedPackageSet()
            .intersect(selected)

        return when (selectedOwnerPackages.size) {
            1 -> ExactWebsitePackageAttribution.SelectedPackage(
                selectedOwnerPackages.single(),
            )

            0 -> {
                ProtectionLog.debugThrottled(
                    key = "website_attribution_uid_no_selected_package",
                    message = "Website Protection UID attribution found no selected package",
                )
                ExactWebsitePackageAttribution.NonSelectedOwner
            }

            else -> {
                ProtectionLog.warnThrottled(
                    key = "website_attribution_uid_ambiguous",
                    message = "Website Protection UID attribution matched multiple selected packages",
                )
                ExactWebsitePackageAttribution.Ambiguous
            }
        }
    }

    private fun resolveOwnerPackages(
        tuple: DnsConnectionTuple,
    ): Set<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || sdkInt < Build.VERSION_CODES.Q) {
            return null
        }

        val manager = connectivityManager ?: return null

        val ownerUid = try {
            manager.getConnectionOwnerUid(
                tuple.protocol,
                InetSocketAddress(tuple.sourceAddress, tuple.sourcePort),
                InetSocketAddress(tuple.destinationAddress, tuple.destinationPort),
            )
        } catch (exception: RuntimeException) {
            ProtectionLog.debugThrottled(
                key = "website_attribution_uid_lookup_failed",
                message = "Website Protection UID attribution failed: ${exception.javaClass.simpleName}",
            )
            return null
        }

        if (ownerUid == Process.INVALID_UID || ownerUid < 0) {
            return null
        }

        val packages = packageManager
            .getPackagesForUid(ownerUid)
            ?.toSet()
            .orEmpty()

        return packages.ifEmpty { null }
    }
}

private fun Set<String>.normalizedPackageSet(): Set<String> =
    asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()
