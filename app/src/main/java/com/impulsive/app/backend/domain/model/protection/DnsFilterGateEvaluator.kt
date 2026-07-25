package com.impulsive.app.backend.domain.model.protection

/**
 * Decides whether the on-device DNS filter can be enabled right now. Pure logic with no
 * Android dependencies. The two inputs come from device checkers at the call site:
 * whether system Private DNS would bypass the local filter, and whether another VPN is
 * already active. Blockers are returned in a fixed order so the gate screen can present
 * them predictably.
 */
object DnsFilterGateEvaluator {
    enum class Blocker {
        PrivateDnsActive,
        AnotherVpnActive,
        LockdownModeActive,
    }

    data class GateResult(
        val blockers: List<Blocker>,
    ) {
        val canEnable: Boolean = blockers.isEmpty()
    }

    fun evaluate(
        privateDnsBypassesFilter: Boolean,
        anotherVpnActive: Boolean,
        lockdownModeActive: Boolean = false,
    ): GateResult {
        val blockers = buildList {
            if (privateDnsBypassesFilter) add(Blocker.PrivateDnsActive)
            if (anotherVpnActive) add(Blocker.AnotherVpnActive)
            if (lockdownModeActive) add(Blocker.LockdownModeActive)
        }
        return GateResult(blockers)
    }
}
