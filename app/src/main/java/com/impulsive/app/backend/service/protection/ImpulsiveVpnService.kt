package com.impulsive.app.backend.service.protection

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.core.app.ServiceCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.impulsive.app.MainActivity
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.repository.BlockedDomainRepository
import com.impulsive.app.backend.domain.model.protection.BlockedDomainMatcher
import com.impulsive.app.backend.domain.model.protection.DnsMessage
import com.impulsive.app.backend.domain.model.protection.DnsPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * On-device DNS filter tunnel. Only the captive DNS address is routed into the tunnel, so all other
 * traffic uses the normal network. Each DNS query is forwarded to an upstream resolver through a
 * protected socket and the reply is written back. This step forwards everything unchanged; blocking
 * is added in a later step.
 */
class ImpulsiveVpnService : VpnService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationHelper by lazy { ProtectionNotificationHelper(applicationContext) }

    @Volatile
    private var tunnel: ParcelFileDescriptor? = null
    private var loopJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var blockedDomains: Set<String> = emptySet()

    private val appSettingsDataSource by lazy { AppSettingsPreferencesDataSource(applicationContext) }
    private val hideSensitiveNotifications by lazy {
        appSettingsDataSource.hideSensitiveNotifications
            .stateIn(serviceScope, SharingStarted.Eagerly, false)
    }

    // Web-block launch state. Keyed on the matched blocked entry (for example
    // "example.com") so the burst of sub-domain lookups in one page load shares a
    // single cooldown. webBlockLaunchPending is a single-launch latch so two
    // different blocked entries seen close together cannot stack timers.
    private val webBlockCooldownByEntry = HashMap<String, Long>()
    @Volatile
    private var webBlockLaunchPending = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> {
                stopTunnelAndSelf()
                return START_NOT_STICKY
            }
            ActionStart, null -> startTunnelIfNeeded()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)

    override fun onRevoke() {
        stopTunnelAndSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        teardownTunnel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startTunnelIfNeeded() {
        if (tunnel != null) return
        notificationHelper.ensureChannels()
        startAsForegroundService()
        val established = runCatching { establishTunnel() }
            .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
            .getOrNull()
        if (established == null) {
            stopTunnelAndSelf()
            return
        }
        tunnel = established
        isRunning = true
        loopJob = serviceScope.launch {
            runCatching { loadBlocklist() }
                .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
            runCatching { runDnsLoop(established) }
                .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
        }
    }

    private fun establishTunnel(): ParcelFileDescriptor? =
        Builder()
            .setSession(VpnSessionName)
            .setMtu(MtuBytes)
            .addAddress(TunnelAddress, 32)
            .addDnsServer(CaptiveDnsAddress)
            .addRoute(CaptiveDnsAddress, 32)
            .setBlocking(true)
            .establish()

    private suspend fun loadBlocklist() {
        val repository = BlockedDomainRepository(applicationContext)
        repository.ensureSeeded()
        blockedDomains = repository.loadBlockedDomains()
    }

    private fun runDnsLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val upstream = DatagramSocket()
        protect(upstream)
        upstream.soTimeout = UpstreamTimeoutMillis
        val upstreamAddresses = listOf(
            InetSocketAddress(InetAddress.getByName(UpstreamResolver), DnsPort),
            InetSocketAddress(InetAddress.getByName(UpstreamResolverFallback), DnsPort),
        )
        val buffer = ByteArray(MtuBytes)
        try {
            while (serviceScope.isActive) {
                val read = input.read(buffer)
                if (read <= 0) continue
                val parsed = DnsPacket.parseIpv4Udp(buffer, read) ?: continue
                if (parsed.destPort != DnsPort) continue
                val domain = DnsMessage.readQuestionName(parsed.payload)
                val blockedEntry = if (domain != null) {
                    BlockedDomainMatcher.matchedBlockedEntry(domain, blockedDomains)
                } else {
                    null
                }
                if (blockedEntry != null) {
                    maybeScheduleWebBlockScreen(blockedEntry)
                    val nxResponse = DnsMessage.buildNxDomainResponse(parsed.payload)
                    if (nxResponse != null) {
                        output.write(
                            DnsPacket.buildIpv4Udp(
                                sourceIp = parsed.destIp,
                                destIp = parsed.sourceIp,
                                sourcePort = parsed.destPort,
                                destPort = parsed.sourcePort,
                                payload = nxResponse,
                            ),
                        )
                    }
                    continue
                }
                val response = forwardUpstream(upstream, upstreamAddresses, parsed.payload) ?: continue
                val reply = DnsPacket.buildIpv4Udp(
                    sourceIp = parsed.destIp,
                    destIp = parsed.sourceIp,
                    sourcePort = parsed.destPort,
                    destPort = parsed.sourcePort,
                    payload = response,
                )
                output.write(reply)
            }
        } finally {
            runCatching { upstream.close() }
            runCatching { input.close() }
            runCatching { output.close() }
        }
    }

    private fun forwardUpstream(
        socket: DatagramSocket,
        addresses: List<InetSocketAddress>,
        query: ByteArray,
    ): ByteArray? {
        for (address in addresses) {
            val result = runCatching {
                socket.send(DatagramPacket(query, query.size, address))
                val responseBuffer = ByteArray(MtuBytes)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                responseBuffer.copyOfRange(0, responsePacket.length)
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun maybeScheduleWebBlockScreen(blockedEntry: String) {
        if (webBlockLaunchPending) return
        val nowMillis = System.currentTimeMillis()
        val lastAt = webBlockCooldownByEntry[blockedEntry]
        if (lastAt != null && nowMillis - lastAt < WebBlockCooldownMillis) return
        webBlockCooldownByEntry[blockedEntry] = nowMillis
        webBlockLaunchPending = true
        serviceScope.launch {
            delay(WebBlockTimerMillis)
            runCatching { launchWebBlockScreen(blockedEntry) }
                .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
            webBlockLaunchPending = false
        }
    }

    /**
     * Launches the shared block screen for a blocked web domain using the same
     * path AppMonitorService uses for blocked apps: a direct activity launch
     * when the Display over other apps permission is granted, with the full
     * screen intent notification as the fallback. The matched entry is passed
     * as both the source label and source package so the existing block screen
     * shows the domain.
     */
    private fun launchWebBlockScreen(blockedEntry: String) {
        if (Settings.canDrawOverlays(applicationContext)) {
            val blockIntent = MainActivity.createBlockIntent(
                context = applicationContext,
                sourcePackageName = blockedEntry,
                sourceLabel = blockedEntry,
            )
            val launched = runCatching { applicationContext.startActivity(blockIntent) }.isSuccess
            if (launched) return
        }
        notificationHelper.showBlockFullScreen(
            sourcePackageName = blockedEntry,
            sourceLabel = blockedEntry,
            hideSensitive = hideSensitiveNotifications.value,
        )
    }

    private fun startAsForegroundService() {
        val notification = notificationHelper.createVpnNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                ProtectionNotificationHelper.VpnNotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            ServiceCompat.startForeground(
                this,
                ProtectionNotificationHelper.VpnNotificationId,
                notification,
                0,
            )
        }
    }

    private fun teardownTunnel() {
        loopJob?.cancel()
        loopJob = null
        runCatching { tunnel?.close() }
        tunnel = null
        isRunning = false
    }

    private fun stopTunnelAndSelf() {
        teardownTunnel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        @Volatile
        var isRunning: Boolean = false

        const val ActionStart = "com.impulsive.app.action.START_VPN"
        const val ActionStop = "com.impulsive.app.action.STOP_VPN"
        private const val VpnSessionName = "Impulsive"
        private const val TunnelAddress = "10.111.222.2"
        private const val CaptiveDnsAddress = "10.111.222.1"
        private const val UpstreamResolver = "1.1.1.3"
        private const val UpstreamResolverFallback = "1.0.0.3"
        private const val DnsPort = 53
        private const val MtuBytes = 4096
        private const val UpstreamTimeoutMillis = 5_000
        private const val WebBlockTimerMillis = 7_000L
        private const val WebBlockCooldownMillis = 30_000L
    }
}
