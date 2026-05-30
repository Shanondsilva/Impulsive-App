package com.impulsive.app.frontend.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun rememberImpulsiveHaptics(enabled: Boolean): ImpulsiveHaptics {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(enabled, hapticFeedback) {
        ImpulsiveHaptics(
            enabled = enabled,
            hapticFeedback = hapticFeedback,
        )
    }
}

@Stable
class ImpulsiveHaptics internal constructor(
    private val enabled: Boolean,
    private val hapticFeedback: HapticFeedback,
) {
    fun light() {
        perform(HapticFeedbackType.TextHandleMove)
    }

    fun confirm() {
        perform(HapticFeedbackType.LongPress)
    }

    fun start() {
        perform(HapticFeedbackType.TextHandleMove)
    }

    fun complete() {
        perform(HapticFeedbackType.LongPress)
    }

    private fun perform(type: HapticFeedbackType) {
        if (enabled) {
            hapticFeedback.performHapticFeedback(type)
        }
    }
}
