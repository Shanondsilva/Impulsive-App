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

internal data class DnsConnectionTuple(
    val protocol: Int,
    val sourceAddress: InetAddress,
    val sourcePort: Int,
    val destinationAddress: InetAddress,
    val destinationPort: Int,
)

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

internal fun selectSingleWebsitePackageFallback(
    selectedPackages: Set<String>,
    vpnAllowedPackages: Set<String>,
): String? {
    val selected = selectedPackages.normalizedPackageSet()
    val allowed = vpnAllowedPackages.normalizedPackageSet()

    return if (selected.size == 1 && allowed == selected) {
        selected.single()
    } else {
        null
    }
}

internal class WebsiteProtectionPackageAttributor(
    private val connectivityManager: ConnectivityManager?,
    private val packageManager: PackageManager,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {
    fun resolve(
        tuple: DnsConnectionTuple?,
        selectedPackages: Set<String>,
        vpnAllowedPackages: Set<String>,
    ): String? {
        val selected = selectedPackages.normalizedPackageSet()
        val allowed = vpnAllowedPackages.normalizedPackageSet()

        if (selected.isEmpty()) {
            return null
        }

        if (tuple != null) {
            val ownerPackages = resolveOwnerPackages(tuple)
            if (ownerPackages != null) {
                val selectedOwnerPackages = ownerPackages
                    .normalizedPackageSet()
                    .intersect(selected)

                return when (selectedOwnerPackages.size) {
                    1 -> selectedOwnerPackages.single()
                    0 -> {
                        ProtectionLog.debugThrottled(
                            key = "website_attribution_uid_no_selected_package",
                            message = "Website Protection UID attribution found no selected package",
                        )
                        null
                    }
                    else -> {
                        ProtectionLog.warnThrottled(
                            key = "website_attribution_uid_ambiguous",
                            message = "Website Protection UID attribution matched multiple selected packages",
                        )
                        null
                    }
                }
            }
        }

        val fallback = selectSingleWebsitePackageFallback(
            selectedPackages = selected,
            vpnAllowedPackages = allowed,
        )

        if (fallback == null && selected.size > 1) {
            ProtectionLog.debugThrottled(
                key = "website_attribution_unavailable_multiple_selected",
                message = "Website Protection attribution unavailable with multiple selected packages",
            )
        }

        return fallback
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
                InetSocketAddress(
                    tuple.sourceAddress,
                    tuple.sourcePort,
                ),
                InetSocketAddress(
                    tuple.destinationAddress,
                    tuple.destinationPort,
                ),
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
