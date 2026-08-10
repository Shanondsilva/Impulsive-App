package com.impulsive.app.backend.domain.model.safebrowse

/** Two hours, in milliseconds. The exact amount granted by one completed rewarded ad. */
const val TwoHourGrantMillis: Long = 2L * 60L * 60L * 1_000L

/** How often an active browser usage lease is checkpointed to persistent storage. */
const val CheckpointIntervalMillis: Long = 15_000L

/**
 * The maximum usage ever charged for a lease abandoned by process death, so a killed
 * process cannot silently consume hours of allowance while the app was not running.
 */
const val MaximumInterruptedLeaseChargeMillis: Long = 30_000L

/** The maximum number of reward receipt tokens retained to detect duplicates. */
const val MaximumRewardReceiptCount: Int = 100

/**
 * Immutable, persistence-layer snapshot of the Safe Browse usage ledger.
 *
 * Does not carry RewardedAd, Activity, Context, WebView, purchase objects or browsing
 * history — only the bare accounting values needed to compute remaining access.
 */
data class SafeBrowseAccessSnapshot(
    val remainingMillis: Long,
    val leaseActive: Boolean,
)

/** The effective, UI-facing Safe Browse access state. */
sealed interface SafeBrowseAccessState {
    /** The ledger has not resolved its first read yet -- never treat this as "no access". */
    data object Loading : SafeBrowseAccessState

    data object Locked : SafeBrowseAccessState

    data class Active(
        val remainingMillis: Long,
    ) : SafeBrowseAccessState

    /** An active Safe Browse Pass entitlement, independent of the timed reward ledger. */
    data class PassActive(
        val expiryTimeMillis: Long,
    ) : SafeBrowseAccessState

    data class Error(
        val message: String,
    ) : SafeBrowseAccessState
}

sealed interface SafeBrowseAccessEffect {
    data object OpenBrowser : SafeBrowseAccessEffect

    data object AccessExpired : SafeBrowseAccessEffect
}

enum class SafeBrowsePassRenewalState {
    NotApplicable,
    Renewing,
    CancelledUntilExpiry,
    Unknown,
}

/**
 * Server-verified Safe Browse Pass entitlement, cached locally for the currently signed-in
 * account only. Entirely independent of Impulsive Plus's premium entitlement and of the
 * timed reward ledger -- never derived from, or combined with, either.
 */
data class SafeBrowsePassEntitlement(
    val active: Boolean = false,
    val productId: String? = null,
    val basePlanId: String? = null,
    val expiryTimeMillis: Long = 0L,
    val isPrepaid: Boolean = false,
    val renewalState:
        SafeBrowsePassRenewalState =
        SafeBrowsePassRenewalState.Unknown,
    val lastVerifiedMillis: Long = 0L,
)

/**
 * Whether a cached Safe Browse Pass entitlement is still usable at [nowMillis]. Valid only
 * strictly before its server-verified expiry -- no grace window, no extension, no overflow
 * calculation.
 */
fun SafeBrowsePassEntitlement.isValidAt(
    nowMillis:
        Long,
): Boolean =
    active &&
        expiryTimeMillis >
        0L &&
        nowMillis >=
        0L &&
        nowMillis <
        expiryTimeMillis

sealed interface SafeBrowseRewardGrantResult {
    data class Granted(
        val remainingMillis: Long,
    ) : SafeBrowseRewardGrantResult

    data class Duplicate(
        val remainingMillis: Long,
    ) : SafeBrowseRewardGrantResult
}
