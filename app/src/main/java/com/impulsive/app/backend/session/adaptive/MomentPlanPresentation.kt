package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType

object MomentPlanPresentation {
    private const val MinimumTitleMeaningfulCharacters = 2
    private const val MinimumActionMeaningfulCharacters = 3
    private const val MinimumFutureCueMeaningfulCharacters = 3

    private val RepeatedWhitespace = Regex("\\s+")

    fun sorted(plans: List<MomentPlan>): List<MomentPlan> =
        plans.sortedWith(
            compareByDescending<MomentPlan> { it.enabled }
                .thenByDescending { it.preferredForCue }
                .thenByDescending { it.updatedAtMillis },
        )

    fun cueLabel(cue: MomentCue?): String = when (cue) {
        MomentCue.Boredom -> "Boredom"
        MomentCue.Stress -> "Stress"
        MomentCue.BeingAlone -> "Being alone"
        MomentCue.Tiredness -> "Tiredness"
        MomentCue.AvoidingSomething -> "Avoiding something"
        MomentCue.AutomaticHabit -> "Automatic habit"
        null -> "Any difficult moment"
    }

    fun cueSentence(cue: MomentCue?): String =
        "I notice ${cueLabel(cue).lowercase()}"

    fun destinationLabel(target: String?): String =
        ImpulsiveDestination.entries
            .firstOrNull { it.storageValue == target }
            ?.let {
                when (it) {
                    ImpulsiveDestination.Focus -> "Focus"
                    ImpulsiveDestination.Journal -> "Journal"
                    ImpulsiveDestination.PivotGames -> "Pivot Games"
                    ImpulsiveDestination.ResetReading -> "Reset Reading"
                }
            }
            ?: "Unavailable destination"

    fun normalizeUserText(value: String): String =
        value.trim().replace(RepeatedWhitespace, " ")

    fun meaningfulCharacterCount(value: String): Int =
        normalizeUserText(value).count { character ->
            character.isLetterOrDigit()
        }

    fun hasMeaningfulTitle(value: String): Boolean =
        meaningfulCharacterCount(value) >= MinimumTitleMeaningfulCharacters

    fun hasMeaningfulAction(value: String): Boolean =
        meaningfulCharacterCount(value) >= MinimumActionMeaningfulCharacters

    fun hasMeaningfulFutureCue(value: String): Boolean =
        meaningfulCharacterCount(value) >= MinimumFutureCueMeaningfulCharacters

    /**
     * Legacy-safe title for read-only surfaces. Never mutates the stored
     * plan; a low-information legacy title falls back to a cue-based label
     * purely for display.
     */
    fun displayTitle(plan: MomentPlan): String {
        val normalized = normalizeUserText(plan.title)
        return if (hasMeaningfulTitle(normalized)) {
            normalized
        } else {
            "Plan for ${cueLabel(plan.momentCue).lowercase()}"
        }
    }

    /**
     * Legacy-safe action summary for read-only surfaces. Never mutates the
     * stored plan; a low-information legacy text-only action falls back to a
     * neutral prompt purely for display.
     */
    fun displayAction(
        plan: MomentPlan,
        selectedAppLabel: String? = null,
    ): String {
        val summary = normalizeUserText(
            actionSummary(
                type = plan.actionType,
                text = plan.actionText,
                target = plan.actionTarget,
                selectedAppLabel = selectedAppLabel,
            ),
        )
        val useful = when (plan.actionType) {
            MomentPlanActionType.TextOnly -> hasMeaningfulAction(summary)
            else -> summary.isNotBlank()
        }
        return if (useful) summary else "Open this plan to add a clearer next action."
    }

    fun shortPreview(plan: MomentPlan, maximumCharacters: Int = 72): String {
        val action = displayAction(plan)
        val shortened = if (action.length <= maximumCharacters) {
            action
        } else {
            action.take((maximumCharacters - 1).coerceAtLeast(1)).trimEnd() + "…"
        }
        return "${cueLabel(plan.momentCue)} → $shortened"
    }

    fun validationMessage(plan: MomentPlan): String? {
        val structuralIssue = AdaptiveModelValidator.validate(plan).firstOrNull()
        if (structuralIssue != null) {
            return when (structuralIssue.field) {
                "title" -> "Add a plan name of up to 60 characters."
                "actionText" -> "Add an action of up to 160 characters."
                "futureCueText" -> "Add a future feeling of up to 180 characters."
                "actionTarget" -> "Choose an available action target."
                else -> "Check the plan details and try again."
            }
        }
        if (!hasMeaningfulTitle(plan.title)) {
            return "Use at least two letters or numbers in the plan name."
        }
        if (!hasMeaningfulFutureCue(plan.futureCueText)) {
            return "Add a little more detail about how you want the next day to feel."
        }
        if (
            plan.actionType == MomentPlanActionType.TextOnly &&
            !hasMeaningfulAction(plan.actionText)
        ) {
            return "Use at least three letters or numbers for the action."
        }
        return null
    }

    fun actionSummary(
        type: MomentPlanActionType,
        text: String,
        target: String?,
        selectedAppLabel: String?,
    ): String = when (type) {
        MomentPlanActionType.TextOnly -> text.trim()
        MomentPlanActionType.OpenImpulsiveDestination ->
            "Open ${destinationLabel(target)}"
        MomentPlanActionType.LaunchSelectedApp -> {
            val normalizedAppLabel = selectedAppLabel
                ?.let(::normalizeUserText)
                .orEmpty()

            val normalizedStoredAction = normalizeUserText(text)

            when {
                normalizedAppLabel.isNotBlank() ->
                    "Open $normalizedAppLabel"

                hasMeaningfulAction(normalizedStoredAction) ->
                    normalizedStoredAction

                else ->
                    "Open selected app"
            }
        }
    }
}
