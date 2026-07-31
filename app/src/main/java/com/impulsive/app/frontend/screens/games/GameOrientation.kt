package com.impulsive.app.frontend.screens.games

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun Context.findActivity(): Activity? {
    var current = this

    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }

        current = current.baseContext
    }

    return null
}

/**
 * Keeps compact phone game windows in portrait, preserving the existing
 * intentional game behaviour. Large-screen windows are not orientation locked
 * because Android can ignore that request and the adaptive layout handles them.
 */
@Composable
internal fun LockPortraitOrientation(
    enabled: Boolean,
) {
    val context =
        LocalContext.current

    val activity =
        remember(context) {
            context.findActivity()
        }

    DisposableEffect(
        activity,
        enabled,
    ) {
        if (activity == null || !enabled) {
            return@DisposableEffect onDispose {}
        }

        val previousOrientation =
            activity.requestedOrientation

        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        onDispose {
            activity.requestedOrientation =
                previousOrientation
        }
    }
}
