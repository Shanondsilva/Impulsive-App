package com.impulsive.app.backend.session.settings

data class AppSettingsState(
    val hapticsEnabled: Boolean = true,
    val soundEffectsEnabled: Boolean = false,
    val hideSensitiveNotifications: Boolean = false,
)
