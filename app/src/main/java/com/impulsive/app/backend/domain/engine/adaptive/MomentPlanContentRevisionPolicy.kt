package com.impulsive.app.backend.domain.engine.adaptive

import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import java.nio.charset.StandardCharsets
import java.util.UUID

fun interface MomentPlanContentRevisionIdSource {
    fun newId(): String
}

object RandomMomentPlanContentRevisionIdSource : MomentPlanContentRevisionIdSource {
    override fun newId(): String = UUID.randomUUID().toString()
}

object MomentPlanContentRevisionIds {
    /**
     * Compatibility-only value for old in-memory callers. Persisted schema-10
     * rows are backfilled with a real deterministic or randomly generated UUID.
     */
    const val Unspecified = "00000000-0000-0000-0000-000000000000"

    fun isOpaqueUuid(value: String): Boolean =
        value.isNotBlank() &&
            runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}

data class CanonicalMomentPlanContent(
    val momentCue: MomentCue?,
    val futureSelfStatement: String,
    val actionText: String,
    val actionType: MomentPlanActionType,
    val actionTarget: String?,
)

/**
 * The title is a display label in the current editor and is deliberately not
 * behavioural content. Enabled/preferred/rehearsal/timestamp metadata is also
 * excluded. Destination and external-app identity are represented by the
 * canonical action type and target.
 */
class MomentPlanContentRevisionPolicy(
    private val idSource: MomentPlanContentRevisionIdSource,
) {
    fun revisionForNewPlan(): String = idSource.newValidatedId()

    fun revisionForEdit(
        existing: MomentPlan,
        candidate: MomentPlan,
    ): String =
        if (canonical(existing) == canonical(candidate)) {
            existing.contentRevisionId
        } else {
            idSource.newValidatedId()
        }

    fun canonical(plan: MomentPlan): CanonicalMomentPlanContent =
        CanonicalMomentPlanContent(
            momentCue = plan.momentCue,
            futureSelfStatement = plan.futureCueText.canonicalText(),
            actionText = plan.actionText.canonicalText(),
            actionType = plan.actionType,
            actionTarget = plan.actionTarget?.canonicalText()?.takeIf { it.isNotEmpty() },
        )

    private fun MomentPlanContentRevisionIdSource.newValidatedId(): String =
        newId().lowercase().also {
            require(MomentPlanContentRevisionIds.isOpaqueUuid(it)) {
                "Moment Plan content revision source must return a canonical UUID."
            }
        }

    private fun String.canonicalText(): String =
        replace("\r\n", "\n").replace('\r', '\n').trim()
}

object LegacyMomentPlanContentRevisionFactory {
    private const val Prefix = "legacy-plan-revision"

    fun create(
        planId: String,
        historicalUpdatedAtMillis: Long,
    ): String {
        require(runCatching { UUID.fromString(planId) }.isSuccess) {
            "Legacy Moment Plan ID must be a UUID."
        }
        require(historicalUpdatedAtMillis >= 0L) {
            "Legacy Moment Plan timestamp must not be negative."
        }
        val seed = "$Prefix|$planId|$historicalUpdatedAtMillis"
        return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
