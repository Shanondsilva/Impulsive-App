package com.impulsive.app.backend.session.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType

object MomentPlanPresentation {
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

    fun shortPreview(plan: MomentPlan, maximumCharacters: Int = 72): String {
        val action = plan.actionText.trim()
        val shortened = if (action.length <= maximumCharacters) {
            action
        } else {
            action.take((maximumCharacters - 1).coerceAtLeast(1)).trimEnd() + "…"
        }
        return "${cueLabel(plan.momentCue)} → $shortened"
    }

    fun validationMessage(plan: MomentPlan): String? {
        val issue = AdaptiveModelValidator.validate(plan).firstOrNull() ?: return null
        return when (issue.field) {
            "title" -> "Add a plan name of up to 60 characters."
            "actionText" -> "Add an action of up to 160 characters."
            "futureCueText" -> "Add a future feeling of up to 180 characters."
            "actionTarget" -> "Choose an available action target."
            else -> "Check the plan details and try again."
        }
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
        MomentPlanActionType.LaunchSelectedApp ->
            "Open ${selectedAppLabel?.takeIf { it.isNotBlank() } ?: "selected app"}"
    }
}

