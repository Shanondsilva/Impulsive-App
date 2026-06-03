package com.impulsive.app.frontend.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberImpulsiveHaptics(enabled: Boolean): ImpulsiveHaptics {
    val context = LocalContext.current.applicationContext
    return remember(enabled, context) {
        ImpulsiveHaptics(
            enabled = enabled,
            vibrator = resolveVibrator(context),
        )
    }
}

private fun resolveVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

@Stable
class ImpulsiveHaptics internal constructor(
    private val enabled: Boolean,
    private val vibrator: Vibrator?,
) {
    fun light() {
        play(Strength.LIGHT)
    }

    fun confirm() {
        play(Strength.STRONG)
    }

    fun start() {
        play(Strength.LIGHT)
    }

    fun complete() {
        play(Strength.STRONG)
    }

    private enum class Strength { LIGHT, STRONG }

    private fun play(strength: Strength) {
        if (!enabled) return
        val device = vibrator ?: return
        if (!device.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effectId = when (strength) {
                Strength.LIGHT -> VibrationEffect.EFFECT_TICK
                Strength.STRONG -> VibrationEffect.EFFECT_CLICK
            }
            device.vibrate(VibrationEffect.createPredefined(effectId))
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val durationMs = if (strength == Strength.LIGHT) 12L else 28L
            val amplitude = if (strength == Strength.LIGHT) 90 else 180
            device.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            return
        }

        @Suppress("DEPRECATION")
        device.vibrate(if (strength == Strength.LIGHT) 12L else 28L)
    }
}
