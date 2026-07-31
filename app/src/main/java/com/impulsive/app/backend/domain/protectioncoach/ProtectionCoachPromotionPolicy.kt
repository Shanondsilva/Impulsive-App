package com.impulsive.app.backend.domain.protectioncoach

enum class PlusPromotionEligibilityReason {
    Eligible,
    DismissedOrWrongTiming,
    AlreadyShownThisSession,
    FrequencyCapped,
}

data class PlusPromotionDecision(
    val show: Boolean,
    val reason: PlusPromotionEligibilityReason,
)

object ProtectionCoachPromotionPolicy {
    private const val FourteenDaysMillis = 14L * 24L * 60L * 60L * 1_000L

    fun shouldShowPostSupportPromotion(
        completedSupportAction: Boolean,
        wasDismissed: Boolean,
        wasWrongTiming: Boolean,
        shownThisSession: Boolean,
        lastShownAtMillis: Long?,
        nowMillis: Long,
    ): PlusPromotionDecision {
        if (!completedSupportAction || wasDismissed || wasWrongTiming) {
            return PlusPromotionDecision(false, PlusPromotionEligibilityReason.DismissedOrWrongTiming)
        }
        if (shownThisSession) {
            return PlusPromotionDecision(false, PlusPromotionEligibilityReason.AlreadyShownThisSession)
        }
        if (lastShownAtMillis != null && nowMillis - lastShownAtMillis < FourteenDaysMillis) {
            return PlusPromotionDecision(false, PlusPromotionEligibilityReason.FrequencyCapped)
        }
        return PlusPromotionDecision(true, PlusPromotionEligibilityReason.Eligible)
    }
}
