package com.impulsive.app.frontend.adaptive

import com.impulsive.app.backend.domain.engine.adaptive.AdaptiveModelValidator
import com.impulsive.app.backend.domain.model.adaptive.ImpulsiveDestination
import com.impulsive.app.backend.domain.model.adaptive.MomentCue
import com.impulsive.app.backend.domain.model.adaptive.MomentPlan
import com.impulsive.app.backend.domain.model.adaptive.MomentPlanActionType
import com.impulsive.app.backend.session.adaptive.MomentPlanPresentation
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentPlanPhase3Test {
    private val screens = source(
        "src/main/java/com/impulsive/app/frontend/screens/adaptive/MomentPlanScreens.kt",
    )
    private val viewModels = source(
        "src/main/java/com/impulsive/app/backend/session/adaptive/MomentPlanViewModels.kt",
    )
    private val navigation = source(
        "src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    )
    private val scanner = source(
        "src/main/java/com/impulsive/app/backend/data/local/device/InstalledAppScanner.kt",
    )
    private val manifest = source("src/main/AndroidManifest.xml")
    private val strings = source("src/main/res/values/strings.xml")
    private val home = source(
        "src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt",
    )
    private val settings = source(
        "src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
    )

    @Test
    fun emptyListStateIsRepresented() {
        assertTrue(viewModels.contains("val isEmpty: Boolean get() = !loading && plans.isEmpty()"))
        assertTrue(screens.contains("state.isEmpty -> EmptyPlans(onCreate)"))
    }

    @Test
    fun populatedListStateRendersPlans() {
        assertTrue(screens.contains("items(state.plans, key = { it.planId })"))
    }

    @Test
    fun enabledPlansSortBeforeDisabledPlans() {
        val sorted = MomentPlanPresentation.sorted(
            listOf(plan(id = uuid(1), enabled = false), plan(id = uuid(2), enabled = true)),
        )
        assertTrue(sorted.first().enabled)
    }

    @Test
    fun preferredPlansSortFirstWithinEnabledGroup() {
        val sorted = MomentPlanPresentation.sorted(
            listOf(
                plan(id = uuid(1), enabled = true),
                plan(id = uuid(2), enabled = true, preferred = true),
            ),
        )
        assertTrue(sorted.first().preferredForCue)
    }

    @Test
    fun preferredLabelIsRenderedFromExplicitState() {
        assertTrue(screens.contains("if (plan.preferredForCue)"))
        assertTrue(strings.contains("name=\"moment_plan_preferred\">Preferred</string>"))
    }

    @Test
    fun createPlanUsesDomainValidation() {
        assertTrue(viewModels.contains("MomentPlanPresentation.validationMessage(plan)"))
        assertTrue(
            source(
                "src/main/java/com/impulsive/app/backend/session/adaptive/MomentPlanPresentation.kt",
            ).contains("AdaptiveModelValidator.validate(plan)"),
        )
    }

    @Test
    fun editPlanPreservesStableId() {
        assertTrue(viewModels.contains("planId = snapshot.planId"))
        assertTrue(viewModels.contains("repository.update(plan)"))
    }

    @Test
    fun doubleSaveCannotCreateDuplicate() {
        assertTrue(viewModels.contains("if (snapshot.saving) return"))
        assertTrue(viewModels.contains("persisted = true"))
        assertTrue(viewModels.contains("if (persisted) repository.update(plan) else repository.create(plan)"))
    }

    @Test
    fun blankActionIsRejected() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(action = "  ")))
    }

    @Test
    fun blankFutureCueIsRejected() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(future = "\n")))
    }

    @Test
    fun blankTitleIsRejected() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(title = "")))
    }

    @Test
    fun titleLimitIsEnforced() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(title = "x".repeat(61))))
    }

    @Test
    fun actionLimitIsEnforced() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(action = "x".repeat(161))))
    }

    @Test
    fun futureCueLimitIsEnforced() {
        assertFalse(AdaptiveModelValidator.isSafeAndValid(plan(future = "x".repeat(181))))
    }

    @Test
    fun enabledLimitErrorIsVisible() {
        assertTrue(strings.contains("You can keep up to six Moment Plans active."))
        assertTrue(viewModels.contains("MomentPlanSaveResult.EnabledPlanLimitReached"))
    }

    @Test
    fun disablingPreferredPlanClearsPreferredStatus() {
        assertTrue(viewModels.contains("preferredForCue = plan.preferredForCue && enabled"))
    }

    @Test
    fun preferredMutationUsesTransactionalRepositoryOperation() {
        assertTrue(viewModels.contains("repository.setPreferred(plan.planId"))
        assertFalse(viewModels.contains("copy(preferredForCue = true)"))
    }

    @Test
    fun deleteConfirmationIsPresent() {
        assertTrue(strings.contains("Delete this Moment Plan?"))
        assertTrue(screens.contains("DeletePlanDialog("))
    }

    @Test
    fun deleteTouchesOnlyMomentPlanRepository() {
        val deleteSection = viewModels.substring(
            viewModels.indexOf("fun delete()"),
            viewModels.indexOf("private fun mutate(", viewModels.indexOf("fun delete()")),
        )
        assertTrue(deleteSection.contains("repository.delete(plan.planId)"))
        assertFalse(deleteSection.contains("AdaptiveDecision"))
    }

    @Test
    fun structuredRehearsalOwnsTimestampUpdate() {
        val rehearsalCoordinator = source(
            "src/main/java/com/impulsive/app/backend/session/adaptive/" +
                "MomentPlanRehearsalCoordinator.kt",
        )
        assertTrue(
            rehearsalCoordinator.contains("markRehearsedIfContentRevisionMatches"),
        )
        assertFalse(viewModels.contains("rehearsedAtMillis = System.currentTimeMillis()"))
    }

    @Test
    fun rehearsalDoesNotCreateAdaptiveDecision() {
        assertFalse(viewModels.contains("AdaptiveDecisionRepository"))
        assertFalse(viewModels.contains("AdaptiveDecision("))
    }

    @Test
    fun textActionIsStoredAsTrimmedUserText() {
        assertTrue(viewModels.contains("actionText = snapshot.actionText.trim()"))
        assertNull(MomentPlanPresentation.validationMessage(plan(action = "Walk for five minutes.")))
    }

    @Test
    fun everyImpulsiveDestinationIsAllowlisted() {
        ImpulsiveDestination.entries.forEach { destination ->
            val candidate = plan(
                type = MomentPlanActionType.OpenImpulsiveDestination,
                action = "Open ${destination.name}",
                target = destination.storageValue,
            )
            assertTrue(AdaptiveModelValidator.isSafeAndValid(candidate))
        }
    }

    @Test
    fun arbitraryDestinationIsRejected() {
        val candidate = plan(
            type = MomentPlanActionType.OpenImpulsiveDestination,
            target = "anything_else",
        )
        assertFalse(AdaptiveModelValidator.isSafeAndValid(candidate))
    }

    @Test
    fun appPickerUsesLauncherActivitiesOnly() {
        assertTrue(scanner.contains("Intent(Intent.ACTION_MAIN)"))
        assertTrue(scanner.contains("addCategory(Intent.CATEGORY_LAUNCHER)"))
        assertTrue(viewModels.contains("scanner.getLaunchableAppCandidates()"))
    }

    @Test
    fun arbitraryPackageInputIsUnavailable() {
        assertFalse(screens.contains("onValueChange = viewModel::updateActionTarget"))
        assertTrue(screens.contains("viewModel.selectApp(it)"))
    }

    @Test
    fun missingSelectedAppIsHandledSafelyOnEdit() {
        assertTrue(viewModels.contains("apps.none { it.packageName == state.actionTarget }"))
        assertTrue(viewModels.contains("\"Choose an app that is currently available.\""))
    }

    @Test
    fun stepThreeScopesBlankTextValidationToTextOnlyActions() {
        val stepValidation = viewModels.substring(
            viewModels.indexOf(
                "private fun stepValidationMessage",
            ),
            viewModels.indexOf(
                "private fun updateEditor",
            ),
        )

        assertTrue(
            stepValidation.contains(
                "3 -> when (state.actionType)",
            ),
        )

        val textOnlyBranch = stepValidation.substring(
            stepValidation.indexOf(
                "MomentPlanActionType.TextOnly ->",
            ),
            stepValidation.indexOf(
                "MomentPlanActionType.OpenImpulsiveDestination,",
            ),
        )

        assertTrue(
            textOnlyBranch.contains(
                "state.actionText.isBlank()",
            ),
        )

        assertTrue(
            textOnlyBranch.contains(
                "MomentPlanPresentation.hasMeaningfulAction",
            ),
        )

        val targetBranches = stepValidation.substring(
            stepValidation.indexOf(
                "MomentPlanActionType.OpenImpulsiveDestination,",
            ),
        )

        assertTrue(
            targetBranches.contains(
                "selectedTargetValidationMessage(state)",
            ),
        )

        assertFalse(
            targetBranches.contains(
                "state.actionText.isBlank()",
            ),
        )
    }

    @Test
    fun homeEmptyCardCopyIsPresent() {
        assertTrue(home.contains("R.string.moment_plan_home_title"))
        assertTrue(home.contains("private fun MomentPlanCompactCard("))
        assertFalse(home.contains("MomentPlanHomeCard("))
    }

    @Test
    fun homeActiveCardUsesShortSafePreview() {
        val preview = MomentPlanPresentation.shortPreview(
            plan(action = "Open my project for two minutes"),
        )
        assertEquals("Boredom → Open my project for two minutes", preview)
        assertTrue(preview.length < 100)
    }

    @Test
    fun settingsShowsAllRequiredPreferenceRows() {
        listOf(
            "My Moment Plans",
            "Personal suggestions",
            "Game suggestions",
            "Reading suggestions",
            "Moment Plan suggestions",
        ).forEach { assertTrue(strings.contains(it)) }
        assertTrue(settings.contains("PersonalSupportSettingsGroup("))
    }

    @Test
    fun settingsUpdatesPreferenceRepositoryValues() {
        assertTrue(viewModels.contains("repository.update(preferences, System.currentTimeMillis())"))
        assertTrue(settings.contains("personalSuggestionsEnabled = checked"))
        assertTrue(settings.contains("momentPlanSuggestionsEnabled = checked"))
    }

    @Test
    fun routesContainPlanIdAndNeverPlanText() {
        assertTrue(navigation.contains("moment_plan_detail/{planId}"))
        assertTrue(navigation.contains("moment_plan_editor?planId={planId}"))
        assertFalse(navigation.contains("moment_plan_detail/{actionText}"))
        assertFalse(navigation.contains("moment_plan_editor?futureCueText"))
    }

    @Test
    fun missingPlanIdShowsSafeNotFoundUi() {
        assertTrue(viewModels.contains("loading = false, missing = true"))
        assertTrue(screens.contains("state.missing -> NotFoundContent"))
    }

    @Test
    fun processRecreationReloadsPlanFromSavedStateId() {
        assertTrue(viewModels.contains("SavedStateHandle"))
        assertTrue(viewModels.contains("savedStateHandle.get<String>(\"planId\")"))
        assertTrue(viewModels.contains("repository.getById(requestedPlanId)"))
    }

    @Test
    fun stableUuidIsCreatedOnceForNewPlan() {
        assertTrue(viewModels.contains("savedStateHandle.get<String>(NEW_PLAN_ID_KEY)"))
        assertTrue(viewModels.contains("UUID.randomUUID().toString().also"))
    }

    @Test
    fun contactsPermissionWasNotAdded() {
        assertFalse(manifest.contains("android.permission.READ_CONTACTS"))
        assertFalse(manifest.contains("android.permission.WRITE_CONTACTS"))
    }

    @Test
    fun arbitraryUrlAndUriInputAreAbsent() {
        assertFalse(screens.contains("KeyboardType.Uri"))
        assertFalse(screens.contains("URL field"))
        assertFalse(viewModels.contains("Intent.ACTION_VIEW"))
    }

    @Test
    fun newUserFacingStringsContainNoEmDash() {
        val newStrings = strings.substring(strings.indexOf("<!-- Moment Plans -->"))
        assertFalse(newStrings.contains('—'))
    }

    @Test
    fun forbiddenMedicalAndShamingCopyIsAbsent() {
        val newStrings = strings.substring(strings.indexOf("<!-- Moment Plans -->")).lowercase()
        listOf(
            "addiction",
            "relapse",
            "treatment",
            "cure",
            "clinically proven",
            "guaranteed",
            "failure",
            "weak willpower",
            "shame",
            "best intervention",
        ).forEach { forbidden -> assertFalse(newStrings.contains(forbidden)) }
    }

    @Test
    fun uiNeverAccessesDaoDirectly() {
        assertFalse(screens.contains("Dao"))
        assertFalse(home.contains("momentPlanDao"))
        assertFalse(settings.contains("adaptivePreferenceDao"))
    }

    @Test
    fun appLockUsesExistingAuthenticatedGuard() {
        assertTrue(home.contains("rememberAppLockGuardController()"))
        assertTrue(home.contains("enabled = appLockEnabled"))
        assertTrue(settings.contains("appLockGuard.run("))
    }

    @Test
    fun phaseThreeDoesNotLaunchSelectedAction() {
        assertFalse(screens.contains("startActivity("))
        assertFalse(viewModels.contains("startActivity("))
        assertFalse(viewModels.contains("getLaunchIntentForPackage"))
    }

    private fun plan(
        id: String = uuid(0),
        title: String = "Clear morning",
        action: String = "Open my project for two minutes",
        future: String = "Tomorrow morning, I want to feel clear.",
        type: MomentPlanActionType = MomentPlanActionType.TextOnly,
        target: String? = null,
        enabled: Boolean = true,
        preferred: Boolean = false,
    ) = MomentPlan(
        planId = id,
        title = title,
        momentCue = MomentCue.Boredom,
        actionText = action,
        futureCueText = future,
        actionType = type,
        actionTarget = target,
        enabled = enabled,
        preferredForCue = preferred,
        createdAtMillis = 100L,
        updatedAtMillis = 200L,
    )

    private fun uuid(index: Int): String =
        UUID.nameUUIDFromBytes("moment-plan-$index".toByteArray()).toString()

    private fun source(path: String): String = File(path).readText()
}
