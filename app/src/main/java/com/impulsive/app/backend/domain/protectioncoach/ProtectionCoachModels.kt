package com.impulsive.app.backend.domain.protectioncoach

import java.util.UUID

@JvmInline
value class ProtectionCoachSuggestionId(val value: String) {
    init {
        require(runCatching { UUID.fromString(value) }.isSuccess) {
            "Protection Coach suggestion IDs must be opaque UUIDs."
        }
    }

    companion object {
        fun create(): ProtectionCoachSuggestionId =
            ProtectionCoachSuggestionId(UUID.randomUUID().toString())
    }
}

object ProtectionCoachPolicyVersion {
    const val Current: Int = 1
}

enum class ProtectionCoachSuggestionType {
    ReviewSocialApps,
    ReviewBrowserProtection,
    CreateMorningWindow,
    CreateEveningWindow,
    StartProtectionEarlier,
    EndProtectionLater,
    CreateWeekdayWindow,
    CreateWeekendWindow,
    PractiseMomentPlan,
    ReviewProtectedApps,
    EnableSupportFamily,
}

enum class ProtectionCoachSuggestionStatus {
    Prepared,
    Presented,
    Accepted,
    AcceptedWithEdits,
    Dismissed,
    Suppressed,
    Expired,
}

enum class ProtectionCoachUnavailableReason {
    InsufficientRootMoments,
    InsufficientDistinctDays,
    InsufficientLookback,
    NoStrongTimeBucket,
    DuplicateActiveSuggestion,
    EquivalentScheduleAlreadyCoversWindow,
    SuppressedByUser,
    DismissalCooldown,
    UnsafeEvidence,
}

enum class ProtectionCoachOnboardingReason {
    SocialMedia,
    BrowserBrowsing,
    Boredom,
    StressOrAlone,
    LateNight,
    Morning,
    WeekOneCueAwareness,
    WeekOnePracticePlan,
    WeekOnePatterns,
}

data class ProtectionCoachEvidence(
    val evidenceWindowStartedAtMillis: Long? = null,
    val evidenceWindowEndedAtMillis: Long? = null,
    val protectedMomentCount: Int = 0,
    val distinctDayCount: Int = 0,
    val broadWindowStartMinute: Int? = null,
    val broadWindowEndMinute: Int? = null,
    val onboardingReason: ProtectionCoachOnboardingReason? = null,
) {
    init {
        require(protectedMomentCount >= 0)
        require(distinctDayCount >= 0)
        require(evidenceWindowStartedAtMillis == null || evidenceWindowEndedAtMillis == null ||
            evidenceWindowEndedAtMillis >= evidenceWindowStartedAtMillis)
        broadWindowStartMinute?.let(::requireValidMinute)
        broadWindowEndMinute?.let(::requireValidMinute)
    }
}

data class ProtectionCoachSuggestion(
    val suggestionId: ProtectionCoachSuggestionId,
    val policyVersion: Int = ProtectionCoachPolicyVersion.Current,
    val suggestionType: ProtectionCoachSuggestionType,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val status: ProtectionCoachSuggestionStatus = ProtectionCoachSuggestionStatus.Prepared,
    val presentedAtMillis: Long? = null,
    val acceptedAtMillis: Long? = null,
    val dismissedAtMillis: Long? = null,
    val suppressedAtMillis: Long? = null,
    val evidence: ProtectionCoachEvidence = ProtectionCoachEvidence(),
    val suggestedStartMinute: Int? = null,
    val suggestedEndMinute: Int? = null,
    val acceptedStartMinute: Int? = null,
    val acceptedEndMinute: Int? = null,
    val relatedMomentPlanId: String? = null,
    val relatedMomentPlanContentRevisionId: String? = null,
) {
    init {
        ProtectionCoachValidator.requireValid(this)
    }
}

sealed interface ProtectionCoachDecision {
    data class Present(val atMillis: Long) : ProtectionCoachDecision
    data class Accept(val atMillis: Long) : ProtectionCoachDecision
    data class AcceptWithEdits(
        val atMillis: Long,
        val acceptedStartMinute: Int,
        val acceptedEndMinute: Int,
    ) : ProtectionCoachDecision
    data class Dismiss(val atMillis: Long) : ProtectionCoachDecision
    data class Suppress(val atMillis: Long) : ProtectionCoachDecision
    data class Expire(val atMillis: Long) : ProtectionCoachDecision
}

data class ProtectionCoachPolicy(
    val policyVersion: Int = ProtectionCoachPolicyVersion.Current,
    val dismissalCooldownMillis: Long = 14L * 24L * 60L * 60L * 1_000L,
    val defaultExpiryMillis: Long = 30L * 24L * 60L * 60L * 1_000L,
)

object ProtectionCoachValidator {
    fun requireValid(suggestion: ProtectionCoachSuggestion) {
        require(suggestion.policyVersion > 0)
        require(suggestion.expiresAtMillis > suggestion.createdAtMillis)
        suggestion.suggestedStartMinute?.let(::requireValidMinute)
        suggestion.suggestedEndMinute?.let(::requireValidMinute)
        suggestion.acceptedStartMinute?.let(::requireValidMinute)
        suggestion.acceptedEndMinute?.let(::requireValidMinute)
        require(!containsUnsafeFreeText(suggestion.relatedMomentPlanId))
        require(!containsUnsafeFreeText(suggestion.relatedMomentPlanContentRevisionId))

        val terminalCount = listOfNotNull(
            suggestion.acceptedAtMillis,
            suggestion.dismissedAtMillis,
            suggestion.suppressedAtMillis,
        ).size + if (suggestion.status == ProtectionCoachSuggestionStatus.Expired) 1 else 0
        require(terminalCount <= 1) { "Only one terminal Protection Coach state is allowed." }
        if (suggestion.status == ProtectionCoachSuggestionStatus.AcceptedWithEdits) {
            require(suggestion.acceptedAtMillis != null)
            require(suggestion.acceptedStartMinute != null)
            require(suggestion.acceptedEndMinute != null)
        }
        if (suggestion.status == ProtectionCoachSuggestionStatus.Accepted) {
            require(suggestion.acceptedAtMillis != null)
        }
        if (suggestion.status == ProtectionCoachSuggestionStatus.Dismissed) {
            require(suggestion.dismissedAtMillis != null)
        }
        if (suggestion.status == ProtectionCoachSuggestionStatus.Suppressed) {
            require(suggestion.suppressedAtMillis != null)
        }
    }

    fun isKnownType(value: String): Boolean =
        ProtectionCoachSuggestionType.entries.any { it.name == value }

    fun isKnownStatus(value: String): Boolean =
        ProtectionCoachSuggestionStatus.entries.any { it.name == value }

    private fun containsUnsafeFreeText(value: String?): Boolean =
        value?.contains('@') == true || value?.contains("://") == true
}

internal fun requireValidMinute(value: Int) {
    require(value in 0..1_439) { "Local minute must be inside a local day." }
}
