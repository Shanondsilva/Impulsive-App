package com.impulsive.app.backend.service.protection

import android.content.Context
import kotlin.random.Random

class InterruptionMessageSelector(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun select(): String {
        synchronized(SelectionLock) {
            val previous = preferences.getInt(PreviousIndexKey, -1)
            val offset = Random.nextInt(Messages.size - 1)
            val selected = if (previous in Messages.indices) {
                if (offset >= previous) offset + 1 else offset
            } else {
                Random.nextInt(Messages.size)
            }
            preferences.edit().putInt(PreviousIndexKey, selected).apply()
            return Messages[selected]
        }
    }

    private companion object {
        const val PreferencesName = "interruption_message_selector"
        const val PreviousIndexKey = "previous_message_index"
        val SelectionLock = Any()

        val Messages = listOf(
            "Wait, maybe don't do it this time.",
            "Think about it for a minute.",
            "Hold on. You can still walk away.",
            "Are you sure this is what you want?",
            "Maybe choose differently this time.",
            "You know where this usually leads.",
            "Remember why you wanted to stop.",
            "Give yourself a minute before deciding.",
            "Don't let this moment make the choice for you.",
            "You can still change your mind.",
            "Maybe you don't need this right now.",
            "Be honest. Will this actually help?",
            "Pause before you do something you might regret.",
            "Not this time. Give yourself a chance.",
            "Take a breath and think again.",
            "You came here for a reason. Remember it.",
            "Slow down. You don't have to decide now.",
            "Think about how you want to feel tomorrow.",
            "You can get through this moment.",
            "What do you actually need right now?",
        )
    }
}
