package com.impulsive.app.backend.service.protection

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.impulsive.app.MainActivity
import com.impulsive.app.backend.data.local.preferences.AppSettingsPreferencesDataSource
import com.impulsive.app.backend.domain.model.protection.BlockLaunchTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object ProtectionInterruptionOverlay {
    enum class Owner {
        AppMonitor,
        Vpn,
    }

    private enum class OverlayDismissReason {
        Normal,
        Unavailable,
    }

    data class ActiveInterruption(
        val owner: Owner,
        val sourcePackageName: String,
        val sourceLabel: String,
        val message: String,
        val isFocusSession: Boolean,
        val incidentStartedAtMillis: Long,
        val adaptiveDecisionId: String? = null,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val overlayVisible = AtomicBoolean(false)
    private val overlayAttached = AtomicBoolean(false)
    private val overlayAttaching = AtomicBoolean(false)
    private val intentionalRemoval = AtomicBoolean(false)

    private val activeInterruption =
        AtomicReference<ActiveInterruption?>(null)

    private val invalidatedInterruption =
        AtomicReference<ActiveInterruption?>(null)

    private var currentView: View? = null
    private var currentOwner: Owner? = null
    private var currentWindowManager: WindowManager? = null
    private var attachTimeout: Runnable? = null
    private var failsafeRemoval: Runnable? = null

    fun isShowing(context: Context): Boolean {
        val appContext = context.applicationContext
        val hasPermission = Settings.canDrawOverlays(appContext)
        val hasActiveInterruption = activeInterruption.get() != null

        val valid =
            hasPermission &&
                hasActiveInterruption &&
                (
                    overlayAttaching.get() ||
                        (overlayVisible.get() && overlayAttached.get())
                )

        if (
            !valid &&
            (
                overlayAttaching.get() ||
                    overlayVisible.get() ||
                    overlayAttached.get()
            )
        ) {
            mainHandler.post {
                invalidateCurrentOverlayIfNeeded(appContext)
            }
        }

        return valid
    }

    fun consumeInvalidatedInterruption(
        context: Context,
    ): ActiveInterruption? {
        val appContext = context.applicationContext

        if (isShowing(appContext)) {
            return null
        }

        invalidatedInterruption.getAndSet(null)?.let { invalidated ->
            return invalidated
        }

        val active = activeInterruption.getAndSet(null)
            ?: return null

        val owner = currentOwner

        overlayVisible.set(false)
        overlayAttached.set(false)
        overlayAttaching.set(false)

        owner?.let { current ->
            ProtectionNotificationGate.onProtectionScreenUnavailable(
                current.toNotificationOwner(),
            )
        }

        val staleView = currentView

        mainHandler.post {
            removeStaleWindowState(staleView)
        }

        return active
    }

    fun show(
        context: Context,
        owner: Owner,
        sourcePackageName: String,
        sourceLabel: String,
        message: String,
        isFocusSession: Boolean,
        incidentStartedAtMillis: Long = System.currentTimeMillis(),
        adaptiveDecisionId: String? = null,
        onShown: () -> Unit = {},
        onFailure: () -> Unit,
    ) {
        val appContext = context.applicationContext
        val failureDelivered = AtomicBoolean(false)
        val shownDelivered = AtomicBoolean(false)

        fun notifyFailure() {
            if (failureDelivered.compareAndSet(false, true)) {
                ProtectionNotificationGate.onProtectionScreenUnavailable(
                    owner.toNotificationOwner(),
                )
                onFailure()
            }
        }

        mainHandler.post {
            if (overlayAttaching.get() || hasValidAttachedOverlay(appContext)) {
                return@post
            }

            invalidateCurrentOverlayIfNeeded(appContext)

            if (!Settings.canDrawOverlays(appContext)) {
                ProtectionLog.warn(
                    "Overlay unavailable: SYSTEM_ALERT_WINDOW permission not granted",
                )
                notifyFailure()
                return@post
            }

            val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (windowManager == null) {
                ProtectionLog.error("Overlay unavailable: WindowManager service missing")
                notifyFailure()
                return@post
            }
            val view = try {
                createView(
                    context = appContext,
                    owner = owner,
                    sourcePackageName = sourcePackageName,
                    sourceLabel = sourceLabel,
                    message = message,
                    isFocusSession = isFocusSession,
                    incidentStartedAtMillis = incidentStartedAtMillis,
                    adaptiveDecisionId = adaptiveDecisionId,
                )
            } catch (error: RuntimeException) {
                ProtectionLog.error(
                    "Protection overlay view creation failed " +
                        "(exception=${error.javaClass.simpleName})",
                )
                notifyFailure()
                return@post
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0.9f
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
            ) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.setBlurBehindRadius(24.dp(appContext))
            }

            val interruption = ActiveInterruption(
                owner = owner,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                message = message,
                isFocusSession = isFocusSession,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = adaptiveDecisionId,
            )

            // Register all current state before addView: attachment can happen
            // during or immediately around the WindowManager call.
            currentView = view
            currentOwner = owner
            currentWindowManager = windowManager
            activeInterruption.set(interruption)
            invalidatedInterruption.set(null)
            overlayAttaching.set(true)
            overlayAttached.set(false)
            overlayVisible.set(false)

            fun markShown() {
                if (currentView !== view || currentOwner != owner) return
                if (!shownDelivered.compareAndSet(false, true)) return

                cancelAttachTimeout()
                overlayAttaching.set(false)
                overlayAttached.set(true)
                overlayVisible.set(true)
                ProtectionNotificationGate.onProtectionScreenShown(
                    owner.toNotificationOwner(),
                )
                view.animate().cancel()
                view.visibility = View.VISIBLE
                view.alpha = 1f
                view.requestLayout()
                view.invalidate()
                view.postInvalidateOnAnimation()

                view.post {
                    if (
                        currentView === view &&
                        currentOwner == owner &&
                        view.isAttachedToWindow
                    ) {
                        view.animate().cancel()
                        view.visibility = View.VISIBLE
                        view.alpha = 1f
                        view.requestLayout()
                        view.invalidate()
                        view.postInvalidateOnAnimation()
                    }
                }

                view.isFocusableInTouchMode = true
                view.requestFocus()

                scheduleFailsafeRemoval()
                performHapticIfEnabled(appContext, view)

                ProtectionLog.debug(
                    "Protection overlay attached and displayed: package=$sourcePackageName",
                )
                onShown()
            }

            view.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(attachedView: View) {
                        if (currentView !== attachedView) return
                        markShown()
                    }

                    override fun onViewDetachedFromWindow(detachedView: View) {
                        if (intentionalRemoval.get()) {
                            return
                        }

                        if (currentView !== detachedView) {
                            return
                        }

                        val wasStillAttaching = overlayAttaching.getAndSet(false)

                        overlayAttached.set(false)
                        overlayVisible.set(false)
                        cancelAttachTimeout()

                        val lostInterruption = activeInterruption.getAndSet(null)

                        currentView = null
                        currentOwner = null
                        currentWindowManager = null

                        failsafeRemoval?.let(mainHandler::removeCallbacks)
                        failsafeRemoval = null

                        ProtectionNotificationGate.onProtectionScreenUnavailable(
                            owner.toNotificationOwner(),
                        )

                        if (wasStillAttaching) {
                            ProtectionLog.error(
                                "Protection overlay detached before becoming visible",
                            )
                            notifyFailure()
                        } else if (lostInterruption != null) {
                            invalidatedInterruption.compareAndSet(
                                null,
                                lostInterruption,
                            )
                            ProtectionLog.warn("Protection overlay unexpectedly detached")
                        }
                    }
                },
            )

            try {
                view.animate().cancel()
                view.visibility = View.VISIBLE
                view.alpha = 1f
                windowManager.addView(view, params)

                if (view.isAttachedToWindow) {
                    markShown()
                } else if (
                    currentView === view &&
                    currentOwner == owner &&
                    overlayAttaching.get()
                ) {
                    lateinit var timeout: Runnable
                    timeout = Runnable {
                        if (attachTimeout === timeout) {
                            attachTimeout = null
                        }
                        if (
                            currentView !== view ||
                            currentOwner != owner ||
                            !overlayAttaching.get()
                        ) {
                            return@Runnable
                        }

                        ProtectionLog.error(
                            message =
                                "Protection overlay attachment timed out",
                            debugDetails =
                                "package=$sourcePackageName",
                        )
                        removeViewAfterFailedAdd(windowManager, view)
                        notifyFailure()
                    }
                    attachTimeout = timeout
                    mainHandler.postDelayed(timeout, AttachTimeoutMillis)
                }
            } catch (error: RuntimeException) {
                cancelAttachTimeout()
                removeViewAfterFailedAdd(windowManager, view)
                ProtectionLog.error(
                    "Protection overlay addView failed (exception=${error.javaClass.simpleName})",
                )
                notifyFailure()
                return@post
            }
        }
    }

    fun dismissOwned(owner: Owner) {
        mainHandler.post {
            if (currentOwner == owner) {
                removeCurrent(OverlayDismissReason.Normal)
            } else {
                ProtectionNotificationGate.onProtectionScreenOff(
                    owner.toNotificationOwner(),
                )
            }
        }
    }

    fun dismissAny() {
        mainHandler.post { removeCurrent(OverlayDismissReason.Normal) }
    }

    private fun createView(
        context: Context,
        owner: Owner,
        sourcePackageName: String,
        sourceLabel: String,
        message: String,
        isFocusSession: Boolean,
        incidentStartedAtMillis: Long,
        adaptiveDecisionId: String?,
    ): View {
        /*
         * An ordinary protected app/site interruption no longer asks the user to
         * choose. It shows a brief decorative bridge and enters the
         * authoritative game-only Support Cycle automatically. Focus keeps its
         * own presentation below.
         */
        if (!isFocusSession && adaptiveDecisionId != null) {
            return createProtectedMomentBridge(
                context = context,
                owner = owner,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = adaptiveDecisionId,
            )
        }

        val root = FrameLayout(context).apply {
            visibility = View.VISIBLE
            alpha = 1f
            setBackgroundColor(Color.argb(190, 0, 0, 0))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    leaveProtectedApp(context, owner, this)
                    true
                } else {
                    false
                }
            }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(22.dp(context), 28.dp(context), 22.dp(context), 22.dp(context))
            background = ImpulsiveBubbleCardDrawable(context.resources.displayMetrics.density)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER
                marginStart = 20.dp(context)
                marginEnd = 20.dp(context)
            },
        )

        val logoFrame = FrameLayout(context)
        val breathingRing = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(2.dp(context), if (isFocusSession) FocusCoralColor else LavenderColor)
            }
        }
        logoFrame.addView(
            breathingRing,
            FrameLayout.LayoutParams(76.dp(context), 76.dp(context)).apply {
                gravity = Gravity.CENTER
            },
        )
        logoFrame.addView(
            ImageView(context).apply {
                setImageResource(com.impulsive.app.R.drawable.impulsive_logo)
                contentDescription = "Impulsive logo"
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            },
            FrameLayout.LayoutParams(56.dp(context), 56.dp(context)).apply {
                gravity = Gravity.CENTER
            },
        )
        card.addView(
            logoFrame,
            LinearLayout.LayoutParams(84.dp(context), 84.dp(context)),
        )
        startBreathingPulse(breathingRing)
        card.addView(TextView(context).apply {
            text = if (isFocusSession) FocusEyebrowText else "IMPULSIVE"
            textSize = 13f
            letterSpacing = 0.18f
            setTextColor(MutedTextColor)
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(context), 0, 0)
        })
        card.addView(TextView(context).apply {
            text = if (isFocusSession) FocusPrimaryMessage else message
            textSize = 23f
            setTextColor(MainTextColor)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
            setPadding(0, 16.dp(context), 0, if (isFocusSession) 6.dp(context) else 24.dp(context))
        })
        if (isFocusSession) {
            card.addView(TextView(context).apply {
                text = FocusSupportingMessage
                textSize = 14f
                setTextColor(MutedTextColor)
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.15f)
                setPadding(0, 0, 0, 24.dp(context))
            })
        }
        val resetChoices = LinearLayout(context).apply {
            orientation = if (context.resources.configuration.screenWidthDp >= 360) {
                LinearLayout.HORIZONTAL
            } else {
                LinearLayout.VERTICAL
            }
            gravity = Gravity.CENTER
        }
        card.addView(
            resetChoices,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        var resetLaunchInFlight = false

        fun launchResetOnce(launchTarget: BlockLaunchTarget) {
            if (resetLaunchInFlight) return

            resetLaunchInFlight = true
            launchReset(
                context = context,
                owner = owner,
                expectedView = root,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                launchTarget = launchTarget,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = adaptiveDecisionId,
                onLaunchFailure = { resetLaunchInFlight = false },
            )
        }

        // One explicit branch per interruption identity: an active Focus
        // interruption never shares the ordinary adaptive/Game/Reading choices,
        // and vice versa.
        when {
            isFocusSession -> {
                resetChoices.addView(
                    resetChoice(context, FocusPrimaryActionLabel, FocusCoralColor) {
                        launchResetOnce(BlockLaunchTarget.FocusRecovery)
                    },
                    resetChoiceLayoutParams(context, horizontal = false, isFirst = true),
                )
            }
            /*
             * A protected interruption carrying an adaptive decision is handled
             * by the automatic bridge before this card is ever built, so no
             * "choose a different direction" option exists any more.
             *
             * This remaining branch covers an interruption with no adaptive
             * decision, where a game is still offered directly; protected entry
             * no longer routes to Reading.
             */
            else -> {
                resetChoices.addView(
                    resetChoice(context, "Pivot by Game", LavenderColor) {
                        launchResetOnce(BlockLaunchTarget.RandomRecoveryGame)
                    },
                    resetChoiceLayoutParams(context, horizontal = false, isFirst = true),
                )
            }
        }

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        footer.addView(
            View(context).apply { setBackgroundColor(DividerColor) },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.dp(context),
            ).apply {
                topMargin = 20.dp(context)
                marginStart = 8.dp(context)
                marginEnd = 8.dp(context)
            },
        )
        footer.addView(softAction(context, "Leave this app") {
            leaveProtectedApp(context, owner, root)
        })
        card.addView(
            footer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        resetChoices.alpha = 1f
        resetChoices.translationY = 0f

        footer.alpha = 1f
        footer.translationY = 0f
        return root
    }

    private fun resetChoice(
        context: Context,
        label: String,
        backgroundColor: Int,
        action: () -> Unit,
    ): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(MainTextColor)
        background = pressableRounded(context, backgroundColor, 18.dp(context).toFloat())
        setPadding(10.dp(context), 10.dp(context), 10.dp(context), 10.dp(context))
        minHeight = 62.dp(context)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    /** Soft filled neutral action that sits inside the card (unlike a bare outline). */
    private fun softAction(context: Context, label: String, action: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(MainTextColor)
            background = pressableRounded(context, SoftNeutralColor, 18.dp(context).toFloat())
            setOnClickListener { action() }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                52.dp(context),
            ).apply { topMargin = 16.dp(context) }
        }

    /** Rounded background with a subtle stroke and a darkened pressed state. */
    private fun pressableRounded(
        context: Context,
        color: Int,
        radius: Float,
    ): StateListDrawable = StateListDrawable().apply {
        val stroke = darken(color, 0.85f)
        val pressed = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(darken(color, 0.90f))
            setStroke(1.dp(context), stroke)
            cornerRadius = radius
        }
        val normal = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            setStroke(1.dp(context), stroke)
            cornerRadius = radius
        }
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(), normal)
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt(),
        (Color.green(color) * factor).toInt(),
        (Color.blue(color) * factor).toInt(),
    )

    /**
     * Gentle breathing pulse on the ring behind the logo - a slow scale/fade at a
     * calm pace. Cancelled automatically when the ring detaches so the infinite
     * animator can never outlive the overlay.
     */
    private fun startBreathingPulse(ring: View) {
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BreathCycleMillis
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                val scale = 1f + 0.14f * t
                ring.scaleX = scale
                ring.scaleY = scale
                ring.alpha = 0.55f - 0.35f * t
            }
        }
        ring.addOnAttachStateChangeListener(
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    animator.cancel()
                }
            },
        )
        animator.start()
    }

    /**
     * Brief automatic bridge for a protected app/site interruption.
     *
     * Deliberately contains no logo, wordmark, message, card, button or timer:
     * there is nothing to decide, so presenting choices would be misleading. The
     * orb is decorative and non-interactive, and the protected Moment launches
     * on its own without waiting for input.
     */
    private fun createProtectedMomentBridge(
        context: Context,
        owner: Owner,
        sourcePackageName: String,
        sourceLabel: String,
        incidentStartedAtMillis: Long,
        adaptiveDecisionId: String,
    ): View {
        val root = FrameLayout(context).apply {
            visibility = View.VISIBLE
            alpha = 1f
            setBackgroundColor(ProtectedBridgeBackgroundColor)
            // Back still leaves, exactly as before: this is not a trap.
            isFocusable = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    leaveProtectedApp(context, owner, this)
                    true
                } else {
                    false
                }
            }
        }

        val orb = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(LavenderColor)
            }
            // Decorative only: never an actionable element for TalkBack.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        root.addView(
            orb,
            FrameLayout.LayoutParams(
                ProtectedBridgeOrbDiameterDp.dp(context),
                ProtectedBridgeOrbDiameterDp.dp(context),
                Gravity.CENTER,
            ),
        )

        var launched = false
        fun launchProtectedMomentOnce() {
            if (launched) return
            launched = true
            launchReset(
                context = context,
                owner = owner,
                expectedView = root,
                sourcePackageName = sourcePackageName,
                sourceLabel = sourceLabel,
                launchTarget = BlockLaunchTarget.ProtectedMoment,
                incidentStartedAtMillis = incidentStartedAtMillis,
                adaptiveDecisionId = adaptiveDecisionId,
                onLaunchFailure = { launched = false },
            )
        }

        if (animationsEnabled()) {
            orb.alpha = 0f
            orb.scaleX = ProtectedBridgeOrbStartScale
            orb.scaleY = ProtectedBridgeOrbStartScale
            orb.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ProtectedBridgeDurationMillis)
                .withEndAction { launchProtectedMomentOnce() }
                .start()
        } else {
            /*
             * Animations disabled system-wide: show the orb statically and
             * continue promptly rather than animating anyway.
             */
            orb.alpha = 1f
            mainHandler.postDelayed(
                { launchProtectedMomentOnce() },
                ProtectedBridgeReducedMotionDelayMillis,
            )
        }

        return root
    }

    /**
     * Whether the platform currently has animations enabled. Respects the
     * system-wide setting instead of introducing a separate stored preference.
     */
    private fun animationsEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        } else {
            true
        }

    private fun launchReset(
        context: Context,
        owner: Owner,
        expectedView: View,
        sourcePackageName: String,
        sourceLabel: String,
        launchTarget: BlockLaunchTarget,
        incidentStartedAtMillis: Long,
        adaptiveDecisionId: String?,
        onLaunchFailure: () -> Unit,
    ) {
        if (currentOwner != owner || currentView !== expectedView) {
            onLaunchFailure()
            return
        }

        /*
         * Keep the overlay attached during the foreground handoff. MainActivity.onResume()
         * dismisses it once Impulsive has actually resumed; if launch fails, the overlay
         * stays available so the user is not dropped back into the protected app.
         */
        val intent = MainActivity.createBlockIntent(
            context = context,
            sourcePackageName = sourcePackageName,
            sourceLabel = sourceLabel,
            launchTarget = launchTarget,
            detectedAtMillis = incidentStartedAtMillis,
            adaptiveDecisionId = adaptiveDecisionId,
        )
        runCatching {
            context.startActivity(intent)
        }.onFailure { error ->
            ProtectionLog.error(
                "Protection pivot launch failed " +
                    "(target=$launchTarget, exception=${error.javaClass.simpleName})",
            )
            onLaunchFailure()
        }
    }

    private fun leaveProtectedApp(context: Context, owner: Owner, expectedView: View) {
        removeIfCurrent(owner, expectedView)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(homeIntent) }
    }

    private fun performHapticIfEnabled(context: Context, view: View) {
        scope.launch {
            val enabled = runCatching {
                withContext(Dispatchers.IO) {
                    AppSettingsPreferencesDataSource(context).hapticsEnabled.first()
                }
            }.getOrDefault(false)
            if (enabled && currentView === view) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }

    private fun removeIfCurrent(
        owner: Owner,
        expectedView: View,
        reason: OverlayDismissReason = OverlayDismissReason.Normal,
    ) {
        if (currentOwner == owner && currentView === expectedView) {
            removeCurrent(reason)
        }
    }

    private fun hasValidAttachedOverlay(
        context: Context,
    ): Boolean {
        val view = currentView

        return Settings.canDrawOverlays(context) &&
            overlayVisible.get() &&
            overlayAttached.get() &&
            view != null &&
            view.isAttachedToWindow &&
            activeInterruption.get() != null
    }

    private fun invalidateCurrentOverlayIfNeeded(
        context: Context,
    ) {
        val hasPermission = Settings.canDrawOverlays(context)
        val hasInterruption = activeInterruption.get() != null

        if (
            hasPermission &&
            overlayAttaching.get() &&
            hasInterruption
        ) {
            return
        }

        val view = currentView
        val owner = currentOwner

        val stillValid =
            hasPermission &&
                overlayVisible.get() &&
                overlayAttached.get() &&
                view != null &&
                view.isAttachedToWindow &&
                hasInterruption

        if (stillValid) {
            return
        }

        val lostInterruption =
            activeInterruption.getAndSet(null)

        if (lostInterruption != null) {
            invalidatedInterruption.compareAndSet(
                null,
                lostInterruption,
            )
        }

        overlayAttaching.set(false)
        overlayVisible.set(false)
        overlayAttached.set(false)

        removeStaleWindowState(view)

        owner?.let { current ->
            ProtectionNotificationGate.onProtectionScreenUnavailable(
                current.toNotificationOwner(),
            )
        }
    }

    private fun removeStaleWindowState(
        expectedView: View?,
    ) {
        if (
            expectedView != null &&
            currentView !== expectedView
        ) {
            return
        }

        cancelAttachTimeout()
        overlayAttaching.set(false)
        failsafeRemoval?.let(mainHandler::removeCallbacks)
        failsafeRemoval = null

        val staleView = currentView
        val staleWindowManager = currentWindowManager

        currentView = null
        currentOwner = null
        currentWindowManager = null

        if (
            staleView != null &&
            staleWindowManager != null
        ) {
            intentionalRemoval.set(true)

            try {
                runCatching {
                    staleWindowManager.removeViewImmediate(staleView)
                }
            } finally {
                intentionalRemoval.set(false)
            }
        }
    }

    private fun removeCurrent(
        reason: OverlayDismissReason,
    ) {
        cancelAttachTimeout()
        overlayAttaching.set(false)
        failsafeRemoval?.let(mainHandler::removeCallbacks)
        failsafeRemoval = null

        val view = currentView
        val owner = currentOwner
        val windowManager = currentWindowManager

        activeInterruption.set(null)
        invalidatedInterruption.set(null)

        currentView = null
        currentOwner = null
        currentWindowManager = null

        overlayAttached.set(false)
        overlayVisible.set(false)

        if (view != null && windowManager != null) {
            intentionalRemoval.set(true)

            try {
                runCatching {
                    windowManager.removeViewImmediate(view)
                }
            } finally {
                intentionalRemoval.set(false)
            }
        }

        when (reason) {
            OverlayDismissReason.Normal ->
                ProtectionNotificationGate.onProtectionScreenOff(
                    owner?.toNotificationOwner(),
                )

            OverlayDismissReason.Unavailable ->
                owner?.let { current ->
                    ProtectionNotificationGate.onProtectionScreenUnavailable(
                        current.toNotificationOwner(),
                    )
                }
        }
    }

    private fun removeViewAfterFailedAdd(
        windowManager: WindowManager,
        view: View,
    ) {
        cancelAttachTimeout()
        overlayAttaching.set(false)
        intentionalRemoval.set(true)

        try {
            runCatching {
                windowManager.removeViewImmediate(view)
            }
        } finally {
            intentionalRemoval.set(false)
        }

        activeInterruption.set(null)

        if (currentView === view) {
            currentView = null
            currentOwner = null
            currentWindowManager = null
        }

        overlayAttached.set(false)
        overlayVisible.set(false)
    }

    private fun cancelAttachTimeout() {
        attachTimeout?.let(mainHandler::removeCallbacks)
        attachTimeout = null
    }

    private fun scheduleFailsafeRemoval() {
        val removal = Runnable {
            removeCurrent(OverlayDismissReason.Unavailable)
        }
        failsafeRemoval = removal
        mainHandler.postDelayed(removal, FailsafeTimeoutMillis)
    }

    private fun resetChoiceLayoutParams(
        context: Context,
        horizontal: Boolean,
        isFirst: Boolean,
    ): LinearLayout.LayoutParams = if (horizontal) {
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (isFirst) marginEnd = 6.dp(context) else marginStart = 6.dp(context)
        }
    } else {
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (!isFirst) topMargin = 10.dp(context)
        }
    }

    private fun Int.dp(context: Context): Int =
        (this * context.resources.displayMetrics.density).toInt()

    private fun Owner.toNotificationOwner(): ProtectionNotificationOwner =
        when (this) {
            Owner.AppMonitor -> ProtectionNotificationOwner.APP_MONITOR
            Owner.Vpn -> ProtectionNotificationOwner.VPN
        }

    private const val AttachTimeoutMillis = 1_500L
    private const val FailsafeTimeoutMillis = 10L * 60L * 1000L
    private val MainTextColor = Color.parseColor("#2F2637")
    private val MutedTextColor = Color.parseColor("#706777")
    private val LavenderColor = Color.parseColor("#D0C3F1")
    private val SkyColor = Color.parseColor("#C7DBF2")
    private val SoftNeutralColor = Color.argb(150, 255, 255, 255)
    private val DividerColor = Color.argb(34, 47, 38, 55)
    private const val BreathCycleMillis = 2_400L

    // Automatic protected bridge: brief, restrained, and never a product timer.
    private const val ProtectedBridgeDurationMillis = 550L
    private const val ProtectedBridgeReducedMotionDelayMillis = 120L
    private const val ProtectedBridgeOrbDiameterDp = 132
    private const val ProtectedBridgeOrbStartScale = 0.86f
    private val ProtectedBridgeBackgroundColor = Color.argb(235, 18, 14, 24)

    // Active Focus interruption identity: the approved Focus coral, kept
    // distinct from the ordinary lavender/sky choices so the overlay never
    // misrepresents a Focus block as an ordinary protection choice.
    private val FocusCoralColor = Color.parseColor("#F5A7A6")
    private const val FocusEyebrowText = "FOCUS MODE"
    private const val FocusPrimaryMessage = "Focus Mode is active."
    private const val FocusSupportingMessage = "This app is blocked until your focus timer ends."
    private const val FocusPrimaryActionLabel = "Review focus options"
}
