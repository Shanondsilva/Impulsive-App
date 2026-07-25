package com.impulsive.app.backend.domain.model.auth

private val DurablePurchaseProviders = setOf(
    AuthProvider.Google,
    AuthProvider.Facebook,
    AuthProvider.Email,
)

fun AuthUser?.isDurablePurchaseAccount(): Boolean {
    val user = this ?: return false

    if (user.provider == AuthProvider.Guest) {
        return false
    }

    return user.linkedProviders.any(DurablePurchaseProviders::contains)
}

sealed interface PurchaseAccountGatePhase {
    data object Ready : PurchaseAccountGatePhase
    data object RequiresDurableAccount : PurchaseAccountGatePhase
    data class Linking(val provider: AuthProvider) : PurchaseAccountGatePhase
    data class AwaitingEmailVerification(val email: String?) : PurchaseAccountGatePhase
    data object AccountConflict : PurchaseAccountGatePhase
}

fun resolvePurchaseAccountGatePhase(
    user: AuthUser?,
    inFlightProvider: AuthProvider?,
    pendingEmailVerificationAddress: String?,
    hasAccountConflict: Boolean,
): PurchaseAccountGatePhase {
    if (hasAccountConflict) {
        return PurchaseAccountGatePhase.AccountConflict
    }

    if (pendingEmailVerificationAddress != null) {
        return PurchaseAccountGatePhase.AwaitingEmailVerification(
            pendingEmailVerificationAddress,
        )
    }

    if (user.isDurablePurchaseAccount()) {
        return PurchaseAccountGatePhase.Ready
    }

    if (inFlightProvider != null) {
        return PurchaseAccountGatePhase.Linking(inFlightProvider)
    }

    return PurchaseAccountGatePhase.RequiresDurableAccount
}
