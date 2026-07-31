package com.impulsive.app.frontend.privacy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

object PrivateScreenRoutePolicy {
    private val privateRoutes = setOf(
        "moment_plan_editor?planId={planId}",
        "moment_plan_detail/{planId}",
        "moment_plan_rehearsal/{rehearsalId}",
        "adaptive_feedback/{decisionId}",
        "what_works_for_me",
        "personal_support_suggestions",
        "personal_support_privacy",
        "adaptive_explanation/{decisionId}",
        "path_shift",
    )

    fun isPrivate(routePattern: String?): Boolean =
        routePattern != null && routePattern in privateRoutes
}

interface SecureWindowHandle {
    val secure: Boolean
    fun setSecure(secure: Boolean)
}

class RouteSensitiveScreenPrivacyController(
    private val window: SecureWindowHandle,
) {
    private var ownsSecureFlag = false

    fun apply(protect: Boolean) {
        when {
            protect && !window.secure -> {
                window.setSecure(true)
                ownsSecureFlag = true
            }
            !protect && ownsSecureFlag -> {
                window.setSecure(false)
                ownsSecureFlag = false
            }
        }
    }

    fun release() {
        if (ownsSecureFlag) {
            window.setSecure(false)
            ownsSecureFlag = false
        }
    }
}

@Composable
fun rememberRouteSensitiveScreenPrivacyReady(
    routePattern: String?,
    enabled: Boolean,
): Boolean {
    val activity = LocalContext.current.findActivity()
    val controller = remember(activity) {
        activity?.window?.let { window ->
            RouteSensitiveScreenPrivacyController(AndroidSecureWindowHandle(window))
        }
    }
    val shouldProtect = enabled && PrivateScreenRoutePolicy.isPrivate(routePattern)
    var ready by remember(shouldProtect) { mutableStateOf(!shouldProtect) }

    DisposableEffect(controller, shouldProtect) {
        controller?.apply(shouldProtect)
        ready = true
        onDispose { }
    }
    DisposableEffect(controller) {
        onDispose {
            controller?.release()
        }
    }
    return ready
}

internal class AndroidSecureWindowHandle(
    private val window: Window,
) : SecureWindowHandle {
    override val secure: Boolean
        get() =
            window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0

    override fun setSecure(secure: Boolean) {
        if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
