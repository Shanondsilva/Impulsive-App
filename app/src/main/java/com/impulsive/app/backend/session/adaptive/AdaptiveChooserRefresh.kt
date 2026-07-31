package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveDecisionRepository
import com.impulsive.app.backend.domain.repository.adaptive.MomentPlanRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first

/**
 * One-shot lifecycle refresh. Room remains authoritative and no polling or
 * navigation payload is used to infer whether an intervention started.
 */
class AdaptiveChooserRefresh(
    private val decisions: AdaptiveDecisionRepository,
    private val plans: MomentPlanRepository,
) {
    suspend fun load(decisionId: String): AdaptiveMomentLoadedData? {
        if (decisionId.isBlank()) return null
        val decision = decisions.getById(decisionId) ?: return null
        return AdaptiveMomentLoadedData(
            decision = decision,
            availablePlans = plans.observeEnabled().first(),
        )
    }
}

class AdaptiveChoiceOperationGuard {
    private val inFlight = AtomicBoolean(false)

    fun tryStart(): Boolean = inFlight.compareAndSet(false, true)

    fun clear() {
        inFlight.set(false)
    }
}
