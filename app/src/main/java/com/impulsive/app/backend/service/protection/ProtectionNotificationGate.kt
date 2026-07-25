package com.impulsive.app.backend.service.protection

import android.os.Handler
import android.os.Looper

internal enum class ProtectionNotificationMode {
    OFF,
    ON,
    SKIPPED,
}

internal enum class ProtectionNotificationOwner {
    APP_MONITOR,
    VPN,
}

internal sealed interface ProtectionNotificationSubmission {
    data object Posted : ProtectionNotificationSubmission
    data object Queued : ProtectionNotificationSubmission
    data object Suppressed : ProtectionNotificationSubmission
}

internal class ProtectionNotificationStateMachine(
    private val schedule: (delayMillis: Long, action: () -> Unit) -> Unit,
) {
    private data class PendingPost(
        val notificationId: Int,
        val post: () -> Unit,
    )

    private val lock = Any()
    private val pendingPosts = LinkedHashMap<Int, PendingPost>()

    private var mode = ProtectionNotificationMode.OFF
    private var owner: ProtectionNotificationOwner? = null
    private var skipGeneration = 0L
    private var skipPostCount = 0
    private var latestSkippedPost: PendingPost? = null

    fun currentMode(): ProtectionNotificationMode =
        synchronized(lock) {
            mode
        }

    fun onProtectionScreenShown(owner: ProtectionNotificationOwner) {
        synchronized(lock) {
            skipGeneration += 1L
            skipPostCount = 0
            latestSkippedPost = null
            mode = ProtectionNotificationMode.ON
            this.owner = owner
        }
    }

    fun submit(
        notificationId: Int,
        eligibleDuringSkippedState: Boolean = true,
        post: () -> Unit,
    ): ProtectionNotificationSubmission {
        var immediatePost: (() -> Unit)? = null

        val result = synchronized(lock) {
            when (mode) {
                ProtectionNotificationMode.OFF -> {
                    immediatePost = post
                    ProtectionNotificationSubmission.Posted
                }

                ProtectionNotificationMode.ON -> {
                    enqueueLocked(PendingPost(notificationId, post))
                    ProtectionNotificationSubmission.Queued
                }

                ProtectionNotificationMode.SKIPPED -> {
                    val pending = PendingPost(notificationId, post)
                    enqueueLocked(pending)
                    if (eligibleDuringSkippedState) {
                        latestSkippedPost = pending
                    }
                    ProtectionNotificationSubmission.Queued
                }
            }
        }

        immediatePost?.let(::runPost)
        return result
    }

    fun onProtectionScreenSkipped(owner: ProtectionNotificationOwner) {
        val generation = synchronized(lock) {
            if (mode != ProtectionNotificationMode.ON || this.owner != owner) {
                return
            }

            mode = ProtectionNotificationMode.SKIPPED
            skipPostCount = 0
            skipGeneration += 1L
            skipGeneration
        }

        scheduleSkippedPost(generation, FirstSkippedPostDelayMillis)
        scheduleSkippedPost(generation, SecondSkippedPostDelayMillis)
    }

    fun onProtectionScreenOff(owner: ProtectionNotificationOwner? = null) {
        val posts = synchronized(lock) {
            if (owner != null && this.owner != owner) {
                return
            }

            mode = ProtectionNotificationMode.OFF
            this.owner = null
            skipGeneration += 1L
            skipPostCount = 0
            latestSkippedPost = null

            pendingPosts.values
                .map(PendingPost::post)
                .also { pendingPosts.clear() }
        }

        posts.forEach(::runPost)
    }

    fun onProtectionScreenUnavailable(owner: ProtectionNotificationOwner) {
        onProtectionScreenOff(owner)
    }

    fun cancelQueued(notificationId: Int) {
        synchronized(lock) {
            pendingPosts.remove(notificationId)
            if (latestSkippedPost?.notificationId == notificationId) {
                latestSkippedPost = null
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            mode = ProtectionNotificationMode.OFF
            owner = null
            skipGeneration += 1L
            skipPostCount = 0
            latestSkippedPost = null
            pendingPosts.clear()
        }
    }

    private fun enqueueLocked(pendingPost: PendingPost) {
        pendingPosts[pendingPost.notificationId] = pendingPost

        while (pendingPosts.size > MaxQueuedNotifications) {
            val oldestKey = pendingPosts.entries.firstOrNull()?.key ?: break
            pendingPosts.remove(oldestKey)
            if (latestSkippedPost?.notificationId == oldestKey) {
                latestSkippedPost = null
            }
        }
    }

    private fun scheduleSkippedPost(
        generation: Long,
        delayMillis: Long,
    ) {
        schedule(delayMillis) {
            val post = synchronized(lock) {
                if (
                    mode != ProtectionNotificationMode.SKIPPED ||
                    skipGeneration != generation ||
                    skipPostCount >= MaxSkippedNotificationPosts
                ) {
                    null
                } else {
                    val candidate = latestSkippedPost?.takeIf { pending ->
                        pendingPosts[pending.notificationId] === pending
                    }
                    if (candidate != null) {
                        skipPostCount += 1
                    }
                    candidate?.post
                }
            }

            post?.let(::runPost)
        }
    }

    private fun runPost(post: () -> Unit) {
        runCatching(post)
    }

    private companion object {
        const val FirstSkippedPostDelayMillis = 5_000L
        const val SecondSkippedPostDelayMillis = 10_000L
        const val MaxSkippedNotificationPosts = 2
        const val MaxQueuedNotifications = 16
    }
}

internal object ProtectionNotificationGate {
    private val mainHandler by lazy {
        Handler(Looper.getMainLooper())
    }

    private val stateMachine by lazy {
        ProtectionNotificationStateMachine { delayMillis, action ->
            mainHandler.postDelayed(action, delayMillis)
        }
    }

    fun currentMode(): ProtectionNotificationMode =
        stateMachine.currentMode()

    fun onProtectionScreenShown(owner: ProtectionNotificationOwner) {
        stateMachine.onProtectionScreenShown(owner)
    }

    fun onProtectionScreenSkipped(owner: ProtectionNotificationOwner) {
        stateMachine.onProtectionScreenSkipped(owner)
    }

    fun onProtectionScreenOff(owner: ProtectionNotificationOwner? = null) {
        stateMachine.onProtectionScreenOff(owner)
    }

    fun onProtectionScreenUnavailable(owner: ProtectionNotificationOwner) {
        stateMachine.onProtectionScreenUnavailable(owner)
    }

    fun submit(
        notificationId: Int,
        eligibleDuringSkippedState: Boolean = true,
        post: () -> Unit,
    ): ProtectionNotificationSubmission =
        stateMachine.submit(
            notificationId = notificationId,
            eligibleDuringSkippedState = eligibleDuringSkippedState,
            post = post,
        )

    fun cancelQueued(notificationId: Int) {
        stateMachine.cancelQueued(notificationId)
    }

    fun clear() {
        stateMachine.clear()
    }
}
