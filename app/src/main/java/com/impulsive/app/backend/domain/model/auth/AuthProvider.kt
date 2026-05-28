package com.impulsive.app.backend.domain.model.auth

/**
 * Identity provider the user authenticated through.
 *
 * Stays in [com.impulsive.app.backend.domain.model] per PROJECT_STRUCTURE.md —
 * UI uses it for rendering the right badge, repositories use it as an enum key.
 */
enum class AuthProvider {
    Google,
    Apple,
    Facebook,
    Guest,
}
