package com.impulsive.app.backend.service.protection

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.ServiceCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.impulsive.app.backend.data.local.device.ForegroundAppReader
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.OneMinuteAccessDataSource
import com.impulsive.app.backend.data.local.preferences.OneMinuteAccessState
import com.impulsive.app.backend.data.local.preferences.ProtectionSetupPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.VpnDiagnosticPreferencesDataSource
import com.impulsive.app.backend.data.local.preferences.WebsiteProtectionIncidentDataSource
import com.impulsive.app.backend.data.repository.BlockedDomainRepository
import com.impulsive.app.backend.domain.model.protection.BlockedDomainMatcher
import com.impulsive.app.backend.domain.model.protection.DnsMessage
import com.impulsive.app.backend.domain.model.protection.DnsPacket
import com.impulsive.app.backend.domain.model.protection.SafeSearchPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device DNS filter tunnel.
 *
 * Only the captive DNS address is routed into the VPN. Normal application
 * traffic therefore continues over the device's normal network path.
 *
 * DNS queries from applications selected for Website Protection are:
 *
 * 1. inspected against Impulsive's blocklist;
 * 2. blocked locally when appropriate;
 * 3. otherwise forwarded to the encrypted DoH resolver;
 * 4. written back to the originating DNS client.
 *
 * Each VPN tunnel generation owns its own DNS workers, resolver, streams and
 * delayed intervention work. Cancelling or refreshing a tunnel therefore
 * prevents work belonging to an old tunnel from leaking into a new one.
 */
class ImpulsiveVpnService : VpnService() {

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO,
        )

    private val notificationHelper by lazy {
        ProtectionNotificationHelper(
            applicationContext,
        )
    }

    private val interruptionMessageSelector by lazy {
        InterruptionMessageSelector(
            applicationContext,
        )
    }

    /**
     * Represents the complete lifecycle of the current tunnel generation.
     *
     * All DNS workers launched by runDnsLoop() are structured children of this
     * job. Cancelling this job therefore cancels the DNS work associated with
     * the old tunnel.
     */
    @Volatile
    private var tunnelJob: Job? = null

    /**
     * The ParcelFileDescriptor currently owned by this service.
     *
     * Closing this descriptor is also used to unblock a blocking TUN read when
     * the tunnel job is cancelled.
     */
    @Volatile
    private var tunnel: ParcelFileDescriptor? = null

    private val appSettingsDataSource by lazy {
        AppSettingsPreferencesDataSource(
            applicationContext,
        )
    }

    private val hideSensitiveNotifications by lazy {
        appSettingsDataSource
            .hideSensitiveNotifications
            .stateIn(
                serviceScope,
                SharingStarted.Eagerly,
                false,
            )
    }

    private val foregroundAppReader by lazy {
        ForegroundAppReader(
            applicationContext,
        )
    }

    private val websiteProtectionIncidentDataSource by lazy {
        WebsiteProtectionIncidentDataSource(
            applicationContext,
        )
    }

    private val websiteProtectionPackageAttributor by lazy {
        WebsiteProtectionPackageAttributor(
            connectivityManager =
                applicationContext.getSystemService(
                    ConnectivityManager::class.java,
                ),
            packageManager =
                packageManager,
        )
    }

    private val oneMinuteAccessDataSource by lazy {
        OneMinuteAccessDataSource(
            applicationContext,
        )
    }

    private val protectionSetupDataSource by lazy {
        ProtectionSetupPreferencesDataSource(
            applicationContext,
        )
    }

    private val oneMinuteAccessState by lazy {
        oneMinuteAccessDataSource
            .state
            .stateIn(
                serviceScope,
                SharingStarted.Eagerly,
                OneMinuteAccessState(),
            )
    }

    private val vpnDiagnosticPreferences by lazy {
        VpnDiagnosticPreferencesDataSource(
            applicationContext,
        )
    }
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
ActionRefreshAllowedApplications -> {
                teardownTunnel()
                startTunnelIfNeeded()
            }

            ActionStart,
            null,
            -> {
                checkLockdownMode()
                startTunnelIfNeeded()
            }
        }

        return START_STICKY
    }

    override fun onBind(
        intent: Intent?,
    ) = super.onBind(intent)

    override fun onRevoke() {
        stopTunnelAndSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }

        ProtectionInterruptionOverlay.dismissOwned(
            ProtectionInterruptionOverlay.Owner.Vpn,
        )

        teardownTunnel()

        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()

        super.onDestroy()
    }

    /**
     * Starts one complete tunnel generation.
     *
     * Important ordering:
     *
     * 1. Read application configuration.
     * 2. Load and seed blocklist.
     * 3. Check cancellation.
     * 4. Establish VPN.
     * 5. Immediately start DNS processing.
     *
     * This prevents Android from routing DNS into a tunnel while the service
     * is still waiting for the blocklist to load.
     */
    private fun startTunnelIfNeeded() {
        if (
            tunnel != null ||
            tunnelJob?.isActive == true
        ) {
            return
        }

        notificationHelper.ensureChannels()
        startAsForegroundService()

        /*
         * LAZY start ensures tunnelJob is assigned before the coroutine begins.
         * This makes generation-identity checks deterministic if startup fails
         * very quickly.
         */
        val generationJob =
            serviceScope.launch(
                start = CoroutineStart.LAZY,
            ) {
                runTunnelGeneration()
            }

        tunnelJob = generationJob
        generationJob.start()
    }

    /**
     * Owns the lifecycle of exactly one tunnel generation.
     */
    private suspend fun runTunnelGeneration() {
        val generationJob =
            currentCoroutineContext()[Job]
                ?: return

        val allowedPackages =
            try {
                protectionSetupDataSource
                    .state
                    .first()
                    .websiteProtectedAppPackageNames
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .filterNot {
                        it == applicationContext.packageName
                    }
                    .toSortedSet()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                FirebaseCrashlytics
                    .getInstance()
                    .recordException(throwable)

                requestStopIfCurrentGeneration(
                    generationJob,
                )
                return
            }

        if (allowedPackages.isEmpty()) {
            requestStopIfCurrentGeneration(
                generationJob,
            )
            return
        }

        /*
         * Load the blocklist before establishing the VPN.
         *
         * If loading fails, do not establish a tunnel that would capture DNS
         * without having a valid filtering state.
         */
        val blockedDomains =
            try {
                loadBlocklist()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                FirebaseCrashlytics
                    .getInstance()
                    .recordException(throwable)

                requestStopIfCurrentGeneration(
                    generationJob,
                )
                return
            }

        /*
         * A refresh/stop may have been requested while configuration or the
         * blocklist was loading.
         */
        currentCoroutineContext()
            .ensureActive()

        val established =
            try {
                establishTunnel(
                    allowedPackages,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                FirebaseCrashlytics
                    .getInstance()
                    .recordException(throwable)

                requestStopIfCurrentGeneration(
                    generationJob,
                )
                return
            }

        if (established == null) {
            requestStopIfCurrentGeneration(
                generationJob,
            )
            return
        }

        /*
         * Do not publish a tunnel that belongs to a generation cancelled while
         * Builder.establish() was running.
         */
        try {
            currentCoroutineContext()
                .ensureActive()
        } catch (cancellation: CancellationException) {
            runCatching {
                established.close()
            }

            throw cancellation
        }

        checkLockdownMode()

        tunnel = established
        isRunning = true

        var unexpectedTermination = false

        try {
            runDnsLoop(
                pfd = established,
                blockedDomains = blockedDomains,
                allowedPackages = allowedPackages,
            )

            /*
             * A normal active tunnel should not simply finish its read loop.
             * If it returns while the generation is still active, treat that
             * as an unexpected broken tunnel.
             */
            currentCoroutineContext()
                .ensureActive()

            unexpectedTermination = true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            /*
             * Closing the TUN descriptor during an intentional cancellation can
             * make the blocking read throw IOException rather than
             * CancellationException. Convert that state back into normal
             * coroutine cancellation instead of reporting a false crash.
             */
            currentCoroutineContext()
                .ensureActive()

            FirebaseCrashlytics
                .getInstance()
                .recordException(throwable)

            unexpectedTermination = true
        } finally {
            runCatching {
                established.close()
            }

            /*
             * Identity guard:
             *
             * An old tunnel generation must never clear state belonging to a
             * newer tunnel established after a refresh.
             */
            if (tunnel === established) {
                tunnel = null
                isRunning = false
            }
        }

        if (unexpectedTermination) {
            requestStopIfCurrentGeneration(
                generationJob,
            )
        }
    }

    /**
     * Stops the service only if the failing generation is still the generation
     * registered as current.
     *
     * This prevents a delayed failure callback from tunnel A from stopping a
     * newer tunnel B.
     */
    private fun requestStopIfCurrentGeneration(
        generationJob: Job,
    ) {
        Handler(mainLooper).post {
            if (tunnelJob === generationJob) {
                stopTunnelAndSelf()
            }
        }
    }

    private fun establishTunnel(
        allowedPackages: Set<String>,
    ): ParcelFileDescriptor? {
        if (allowedPackages.isEmpty()) {
            return null
        }

        val builder =
            Builder()
                .setSession(
                    VpnSessionName,
                )
                .setMtu(
                    MtuBytes,
                )
                .addAddress(
                    TunnelAddress,
                    32,
                )
                .addDnsServer(
                    CaptiveDnsAddress,
                )
                .addRoute(
                    CaptiveDnsAddress,
                    32,
                )
                .allowFamily(
                    OsConstants.AF_INET6,
                )
                .setBlocking(
                    true,
                )

        var addedApplicationCount = 0

        allowedPackages.forEach { packageName ->
            try {
                builder.addAllowedApplication(
                    packageName,
                )

                addedApplicationCount++
            } catch (_: PackageManager.NameNotFoundException) {
                /*
                 * An application can be uninstalled after the user selected it.
                 */
            }
        }

        /*
         * Without an allowed application, Android's VPN behavior could become
         * broader than intended. Do not establish the tunnel in that state.
         */
        if (addedApplicationCount == 0) {
            return null
        }

        return builder.establish()
    }

    /**
     * Returns the fully loaded blocklist.
     *
     * The caller decides whether the tunnel can safely start. A loading
     * failure therefore never silently becomes an empty blocklist.
     */
    private suspend fun loadBlocklist(): Set<String> {
        val repository =
            BlockedDomainRepository(
                applicationContext,
            )

        repository.ensureSeeded()

        return repository.loadBlockedDomains()
    }

    /**
     * Reads DNS packets from one tunnel generation.
     *
     * The surrounding coroutineScope is important:
     *
     * - every DNS query worker is a structured child;
     * - cancellation propagates to all in-flight queries;
     * - the resolver and streams are not closed until child workers have
     *   completed or been cancelled.
     */
    private suspend fun runDnsLoop(
        pfd: ParcelFileDescriptor,
        blockedDomains: Set<String>,
        allowedPackages: Set<String>,
    ) {
        val input =
            FileInputStream(
                pfd.fileDescriptor,
            )

        val output =
            FileOutputStream(
                pfd.fileDescriptor,
            )

        val outputLock = Any()

        val resolver =
            DnsOverHttpsResolver(
                protectSocket = { socket ->
                    protect(socket)
                },
                maxDnsMessageBytes =
                    MaxDnsMessageBytes,
            )

        val querySlots =
            Semaphore(
                MaxInFlightDnsQueries,
            )

        val encryptedDnsAlertPosted =
            AtomicBoolean(false)

        runResolverSelfTest(
            resolver,
        )

        val buffer =
            ByteArray(
                MtuBytes,
            )

        try {
            coroutineScope {
                /*
                 * Capture the tunnel-generation scope.
                 *
                 * Delayed intervention work is launched here so it is cancelled
                 * when this tunnel generation is cancelled, rather than
                 * surviving in serviceScope.
                 */
                while (
                    currentCoroutineContext()
                        .isActive
                ) {
                    val read =
                        input.read(
                            buffer,
                        )

                    if (read < 0) {
                        /*
                         * The TUN descriptor disappeared unexpectedly.
                         */
                        break
                    }

                    if (read == 0) {
                        continue
                    }

                    val parsed =
                        DnsPacket.parseIpv4Udp(
                            buffer,
                            read,
                        ) ?: continue

                    if (
                        parsed.destPort !=
                        ClientDnsPort
                    ) {
                        continue
                    }

                    /*
                     * Do not create an unbounded queue.
                     *
                     * Android's DNS client will retry a dropped request. A
                     * bounded number of concurrent requests is safer than
                     * indefinitely accumulating coroutines during a network
                     * outage.
                     */
                    if (!querySlots.tryAcquire()) {
                        continue
                    }

                    launch {
                        try {
                            handleDnsQuery(
                                parsed = parsed,
                                blockedDomains =
                                    blockedDomains,
                                resolver = resolver,
                                output = output,
                                outputLock =
                                    outputLock,
                                allowedPackages =
                                    allowedPackages,
                                encryptedDnsAlertPosted =
                                    encryptedDnsAlertPosted,
                            )
                        } catch (
                            cancellation:
                                CancellationException,
                        ) {
                            throw cancellation
                        } catch (
                            throwable:
                                Throwable,
                        ) {
                            FirebaseCrashlytics
                                .getInstance()
                                .recordException(
                                    throwable,
                                )
                        } finally {
                            querySlots.release()
                        }
                    }
                }
            }
        } finally {
            /*
             * coroutineScope above first cancels/waits for its children during
             * tunnel cancellation. Closing resources here therefore prevents
             * old query workers from racing against resolver/stream teardown.
             */
            runCatching {
                resolver.close()
            }

            runCatching {
                input.close()
            }

            runCatching {
                output.close()
            }
        }
    }

    private fun writeTunnelReply(
        output: FileOutputStream,
        outputLock: Any,
        parsed: DnsPacket.Udp4,
        payload: ByteArray,
    ) {
        /*
         * The complete IPv4 + UDP packet must fit inside the configured TUN
         * MTU.
         */
        if (
            payload.size >
            MaxDnsMessageBytes
        ) {
            return
        }

        val packet =
            DnsPacket.buildIpv4Udp(
                sourceIp =
                    parsed.destIp,
                destIp =
                    parsed.sourceIp,
                sourcePort =
                    parsed.destPort,
                destPort =
                    parsed.sourcePort,
                payload =
                    payload,
            )

        synchronized(
            outputLock,
        ) {
            output.write(
                packet,
            )
        }
    }

    private suspend fun handleDnsQuery(
        parsed: DnsPacket.Udp4,
        blockedDomains: Set<String>,
        resolver: DnsOverHttpsResolver,
        output: FileOutputStream,
        outputLock: Any,
        allowedPackages: Set<String>,
        encryptedDnsAlertPosted: AtomicBoolean,
    ) {
        val domain =
            DnsMessage.readQuestionName(
                parsed.payload,
            )

        val blockedEntry =
            if (domain != null) {
                BlockedDomainMatcher
                    .matchedBlockedEntry(
                        domain,
                        blockedDomains,
                    )
            } else {
                null
            }

        /*
         * BLOCKED DOMAIN PATH
         */
        if (blockedEntry != null) {
            val attributedPackage =
                websiteProtectionPackageAttributor.resolve(
                    tuple =
                        dnsConnectionTupleFromPacket(
                            parsed,
                        ),
                    selectedPackages =
                        allowedPackages,
                    vpnAllowedPackages =
                        allowedPackages,
                )

            if (attributedPackage != null) {
                val foregroundPackage =
                    foregroundAppReader.getCurrentForegroundPackage()

                if (foregroundPackage == attributedPackage) {
                    websiteProtectionIncidentDataSource.recordAdultActivity(
                        packageName =
                            attributedPackage,
                        sourceLabel =
                            foregroundAppReader.getApplicationLabel(
                                attributedPackage,
                            ),
                        blockedDomain =
                            blockedEntry,
                        nowEpochMillis =
                            System.currentTimeMillis(),
                    )
                } else {
                    ProtectionLog.debugThrottled(
                        key = "website_adult_activity_background:$attributedPackage",
                        message =
                            "Blocked Website Protection domain attributed to a background package; friction not started",
                    )
                }
            } else {
                ProtectionLog.warnThrottled(
                    key = "website_cooldown_unattributed",
                    message =
                        "Blocked Website Protection domain could not be attributed to a protected package",
                )
            }

            val nxResponse =
                DnsMessage
                    .buildNxDomainResponse(
                        parsed.payload,
                    )

            if (nxResponse != null) {
                writeTunnelReply(
                    output =
                        output,
                    outputLock =
                        outputLock,
                    parsed =
                        parsed,
                    payload =
                        nxResponse,
                )
            }

            return
        }
        /*
         * SAFESEARCH PATH
         *
         * Non-A questions (AAAA, HTTPS/type 65) for SafeSearch hosts get an
         * empty NOERROR answer so clients fall back to the rewritten A
         * record. Forwarding the original query here would hand the browser
         * the real IPv6 address and silently bypass SafeSearch.
         */
        val safeSearchHost =
            domain?.let {
                SafeSearchPolicy
                    .safeSearchHostFor(
                        it,
                    )
            }

        if (safeSearchHost != null) {
            if (!DnsMessage.isAInQuestion(parsed.payload)) {
                val emptyResponse =
                    DnsMessage.buildEmptyNoErrorResponse(parsed.payload)
                if (emptyResponse != null) {
                    writeTunnelReply(
                        output = output,
                        outputLock = outputLock,
                        parsed = parsed,
                        payload = emptyResponse,
                    )
                    return
                }
            }

            val hostQuery =
                DnsMessage
                    .buildQueryForName(
                        parsed.payload,
                        safeSearchHost,
                    )

            val safeSearchIp =
                hostQuery
                    ?.let {
                        resolveWithHealthTracking(
                            resolver =
                                resolver,
                            query =
                                it,
                            encryptedDnsAlertPosted =
                                encryptedDnsAlertPosted,
                        )
                    }
                    ?.let {
                        DnsMessage
                            .readFirstARecordIp(
                                it,
                            )
                    }

            val rewritten =
                safeSearchIp?.let {
                    DnsMessage
                        .buildARecordResponse(
                            parsed.payload,
                            it,
                        )
                }

            if (rewritten != null) {
                writeTunnelReply(
                    output =
                        output,
                    outputLock =
                        outputLock,
                    parsed =
                        parsed,
                    payload =
                        rewritten,
                )

                return
            }

            /*
             * If SafeSearch resolution or rewriting fails, resolve the user's
             * original DNS query normally instead of treating the site as
             * blocked.
             */
        }

        /*
         * NORMAL ALLOWED DOMAIN PATH
         *
         * This is the key behavior:
         *
         * - no blocklist match;
         * - original query goes to encrypted DoH;
         * - successful DNS response is returned to the originating application.
         *
         * A transport failure generates SERVFAIL, never NXDOMAIN.
         */
        val response =
            resolveWithHealthTracking(
                resolver =
                    resolver,
                query =
                    parsed.payload,
                encryptedDnsAlertPosted =
                    encryptedDnsAlertPosted,
            )
                ?: DnsMessage
                    .buildServFailResponse(
                        parsed.payload,
                    )
                ?: return

        writeTunnelReply(
            output =
                output,
            outputLock =
                outputLock,
            parsed =
                parsed,
            payload =
                response,
        )
    }

    private suspend fun resolveWithHealthTracking(
        resolver: DnsOverHttpsResolver,
        query: ByteArray,
        encryptedDnsAlertPosted: AtomicBoolean,
    ): ByteArray? {
        val response =
            resolver.resolve(
                query,
            )

        val health =
            resolver.healthSnapshot()

        if (response != null) {
            encryptedDnsAlertPosted.set(false)
            return response
        }

        val reason =
            health.lastFailureReason ?: "unknown"

        ProtectionLog.warnThrottled(
            key = "doh_resolve_failure",
            message =
                "DoH resolve failed " +
                    "(consecutiveFailures=${health.consecutiveFailureCount}, " +
                    "reason=$reason)",
        )

        if (
            health.consecutiveFailureCount >=
            ConsecutiveDnsFailureNotificationThreshold &&
            encryptedDnsAlertPosted.compareAndSet(
                false,
                true,
            )
        ) {
            notificationHelper.showEncryptedDnsUnreachableNotification()
        }

        return null
    }

    private suspend fun runResolverSelfTest(
        resolver: DnsOverHttpsResolver,
    ) {
        val query =
            buildResolverSelfTestQuery()

        val response =
            resolver.resolve(
                query,
            )

        val health =
            resolver.healthSnapshot()

        if (response != null) {
            ProtectionLog.debug(
                "DoH self-test succeeded",
            )
        } else {
            ProtectionLog.warn(
                "DoH self-test failed " +
                    "(reason=${health.lastFailureReason ?: "unknown"})",
            )
        }
    }

    private fun buildResolverSelfTestQuery(): ByteArray =
        byteArrayOf(
            0x45,
            0x53,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x07,
            0x65,
            0x78,
            0x61,
            0x6D,
            0x70,
            0x6C,
            0x65,
            0x03,
            0x63,
            0x6F,
            0x6D,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
        )

    private fun checkLockdownMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            vpnDiagnosticPreferences.setLockdownModeActive(false)
            return
        }

        val lockdownActive =
            runCatching {
                isLockdownEnabled
            }.getOrDefault(false)

        vpnDiagnosticPreferences.setLockdownModeActive(
            lockdownActive,
        )

        if (!lockdownActive) {
            return
        }

        ProtectionLog.warn(
            "vpn_lockdown_active: Android Block connections without VPN is enabled",
        )
        notificationHelper.showLockdownIncompatibleNotification()
    }
    private fun recoverInvalidatedOverlay() {
        val interruption =
            ProtectionInterruptionOverlay
                .consumeInvalidatedInterruption(
                    applicationContext,
                )
                ?: return

        if (
            notificationHelper
                .interruptionNotificationStatus() !=
            InterruptionNotificationStatus
                .Available
        ) {
            return
        }

        val decision =
            when (
                interruption.owner
            ) {
                ProtectionInterruptionOverlay
                    .Owner
                    .AppMonitor ->
                    InterruptionNotificationLimiter
                        .decideNotificationForApp(
                            packageName =
                                interruption
                                    .sourcePackageName,
                            nowMillis =
                                System
                                    .currentTimeMillis(),
                        )

                ProtectionInterruptionOverlay
                    .Owner
                    .Vpn ->
                    InterruptionNotificationLimiter
                        .decideNotificationForApp(
                            packageName =
                                interruption
                                    .sourcePackageName,
                            nowMillis =
                                System
                                    .currentTimeMillis(),
                        )
            }

        if (
            decision !is
            InterruptionNotificationDecision
                .Post
        ) {
            return
        }

        notificationHelper
            .showInterruptionFallback(
                sourcePackageName =
                    interruption
                        .sourcePackageName,
                sourceLabel =
                    interruption
                        .sourceLabel,
                message =
                    decision.message,
                hideSensitive =
                    hideSensitiveNotifications
                        .value,
                isFocusSession =
                    interruption
                        .isFocusSession,
            )
    }


    private fun startAsForegroundService() {
        val notification =
            notificationHelper
                .createVpnNotification()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES
                .UPSIDE_DOWN_CAKE
        ) {
            ServiceCompat
                .startForeground(
                    this,
                    ProtectionNotificationHelper
                        .VpnNotificationId,
                    notification,
                    ServiceInfo
                        .FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
        } else {
            ServiceCompat
                .startForeground(
                    this,
                    ProtectionNotificationHelper
                        .VpnNotificationId,
                    notification,
                    0,
                )
        }
    }

    /**
     * Cancels the current tunnel generation and closes its TUN descriptor.
     *
     * Closing the descriptor is necessary because the TUN read is deliberately
     * blocking. The close wakes that read while coroutine cancellation handles
     * the remaining structured child work.
     */
    private fun teardownTunnel() {
        val jobToCancel =
            tunnelJob

        val tunnelToClose =
            tunnel

        /*
         * Clear shared references first. This prevents an old generation's
         * cleanup from being confused with a subsequently started generation.
         */
        tunnelJob = null
        tunnel = null
        isRunning = false

        jobToCancel?.cancel()

        runCatching {
            tunnelToClose?.close()
        }
    }

    private fun stopTunnelAndSelf() {
        teardownTunnel()

        stopForeground(
            STOP_FOREGROUND_REMOVE,
        )

        stopSelf()
    }

    companion object {
        @Volatile
        private var activeInstance: ImpulsiveVpnService? = null

        fun requestStop(): Boolean {
            val service =
                activeInstance
                    ?: return false

            Handler(service.mainLooper).post {
                if (activeInstance === service) {
                    service.stopTunnelAndSelf()
                }
            }

            return true
        }

        @Volatile
        var isRunning: Boolean =
            false

        const val ActionStart =
            "com.impulsive.app.action.START_VPN"
        const val ActionRefreshAllowedApplications =
            "com.impulsive.app.action.REFRESH_VPN_ALLOWED_APPS"

        private const val VpnSessionName =
            "Impulsive"

        private const val TunnelAddress =
            "10.111.222.2"

        private const val CaptiveDnsAddress =
            "10.111.222.1"

        private const val ClientDnsPort =
            53

        /*
         * IPv4 tunnel:
         *
         * MTU
         * - IPv4 header (20 bytes)
         * - UDP header (8 bytes)
         * = maximum DNS message carried without exceeding the TUN MTU.
         */
        private const val MtuBytes =
            4_096

        private const val Ipv4HeaderBytes =
            20

        private const val UdpHeaderBytes =
            8

        private const val MaxDnsMessageBytes =
            MtuBytes -
                Ipv4HeaderBytes -
                UdpHeaderBytes

        /*
         * Bounded parallelism.
         *
         * Enough concurrency for parallel browser A/AAAA/resource lookups
         * without permitting unlimited DNS work during an upstream outage.
         */
        private const val MaxInFlightDnsQueries =
            32

        private const val ConsecutiveDnsFailureNotificationThreshold =
            20L



    }
}
