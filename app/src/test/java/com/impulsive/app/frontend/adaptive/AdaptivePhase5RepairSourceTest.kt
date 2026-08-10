package com.impulsive.app.frontend.adaptive

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePhase5RepairSourceTest {
    private val root = File("src/main")
    private val viewModel = File(
        root,
        "java/com/impulsive/app/backend/session/adaptive/AdaptiveMomentViewModel.kt",
    ).readText()
    private val screen = File(
        root,
        "java/com/impulsive/app/frontend/screens/adaptive/AdaptiveMomentScreens.kt",
    ).readText()
    private val promptStore = File(
        root,
        "java/com/impulsive/app/backend/session/adaptive/AdaptiveOptionalPromptState.kt",
    ).readText()
    private val settings = File(
        root,
        "java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt",
    ).readText()
    private val navigation = File(
        root,
        "java/com/impulsive/app/frontend/navigation/AppNavHost.kt",
    ).readText()
    private val notification = File(
        root,
        "java/com/impulsive/app/backend/service/protection/ProtectionNotificationHelper.kt",
    ).readText()
    private val database = File(
        root,
        "java/com/impulsive/app/backend/data/local/database/AppDatabase.kt",
    ).readText()

    @Test
    fun optionalPromptsUseExplicitSavedTriState() {
        assertTrue(viewModel.contains("enum class OptionalPromptUiState"))
        assertTrue(viewModel.contains("Unanswered"))
        assertTrue(viewModel.contains("Selected"))
        assertTrue(viewModel.contains("Skipped"))
        assertTrue(promptStore.contains("CuePromptStateKey"))
        assertTrue(promptStore.contains("UrgePromptStateKey"))
        assertTrue(promptStore.contains("SavedStateHandle"))
        assertFalse(screen.contains("onCue(null)"))
        assertFalse(screen.contains("onUrge(null)"))
    }

    @Test
    fun skipCollapsesAndCanBeReopened() {
        assertTrue(screen.contains("cuePromptState == OptionalPromptUiState.Skipped"))
        assertTrue(screen.contains("urgePromptState == OptionalPromptUiState.Skipped"))
        assertTrue(screen.contains("SkippedPromptRow(label = \"Cue\""))
        assertTrue(screen.contains("SkippedPromptRow(label = \"Rating\""))
        assertTrue(screen.contains("onChange = onReopenCue"))
        assertTrue(screen.contains("onChange = onReopenUrge"))
    }

    @Test
    fun skippedValuesAreNotWrittenAsMomentContext() {
        val contextWrite = viewModel.substring(
            viewModel.indexOf("decisions.recordMomentContextOnce"),
            viewModel.indexOf("val choiceResult"),
        )
        assertTrue(contextWrite.contains("OptionalPromptUiState.Selected"))
        assertFalse(contextWrite.contains("OptionalPromptUiState.Skipped"))
    }

    @Test
    fun explicitFollowUpHasSynchronousDoubleTapGuardOnlyInChoose() {
        val choose = viewModel.substring(
            viewModel.indexOf("fun choose("),
            viewModel.indexOf("fun startMomentPlan("),
        )
        assertTrue(choose.contains("if (snapshot.savingChoice || snapshot.routing) return"))
        assertTrue(choose.contains("savingChoice = true"))
        assertTrue(choose.contains("followUpSupport.chooseAnother"))
        assertFalse(
            viewModel.substring(
                viewModel.indexOf("init {"),
                viewModel.indexOf("fun choose("),
            ).contains("followUpSupport.chooseAnother"),
        )
    }

    @Test
    fun personalSupportUsesCompactSettingsRowsAndKeepsAppLock() {
        assertTrue(settings.contains("PersonalSupportSettingsGroup("))
        assertFalse(settings.contains("PersonalSupportSettingsCard("))
        val group = settings.substring(
            settings.indexOf("private fun PersonalSupportSettingsGroup"),
            settings.indexOf("private fun MultiSelectEditDialog"),
        )
        assertTrue(group.contains("AccordionGroup("))
        assertFalse(group.contains("Card("))
        assertEquals(2, Regex("""SettingsRow\(""").findAll(group).count())
        assertEquals(0, Regex("""SettingsSwitch\(""").findAll(group).count())
        assertFalse(group.contains("\"Future Path\""))
        assertFalse(group.contains("R.string.tips_title"))
        assertFalse(group.contains("\"Suggestion preferences\""))
        assertFalse(group.contains("\"Safe Browse Pass\""))
        assertTrue(group.contains("\"What Works for Me\""))
        assertTrue(group.contains("\"Privacy and data\""))
        assertTrue(group.contains("SafeBrowseAdPrivacyChoicesRow()"))
        assertFalse(group.contains("\"How suggestions work\""))
        assertFalse(group.contains("\"Reset personal learning\""))
        assertFalse(group.contains("\"Delete all Moment data\""))

        val callSite = settings.substring(
            settings.indexOf("PersonalSupportSettingsGroup("),
            settings.indexOf("ProtectionFocusGroup("),
        )
        assertTrue(callSite.contains("appLockGuard.run("))
        assertTrue(callSite.contains("action = onOpenWhatWorksForMe"))
        assertTrue(callSite.contains("action = onOpenPrivacyAndData"))
    }

    @Test
    fun allPersonalSupportSwitchesStillUpdateRepositories() {
        listOf(
            "personalSuggestionsEnabled = checked",
            "gameSuggestionsEnabled = checked",
            "readingSuggestionsEnabled = checked",
            "momentPlanSuggestionsEnabled = checked",
        ).forEach { update ->
            assertTrue(settings.contains(update))
        }
    }

    @Test
    fun websiteFallbackWordingIsSourceNeutralAndPrivateDataFree() {
        assertTrue(notification.contains("Protected content was detected"))
        assertFalse(notification.contains("A protected app is open"))
        val body = notification.substring(
            notification.indexOf("internal const val InterruptionFallbackNotificationBody"),
            notification.indexOf("class ProtectionNotificationHelper"),
        )
        listOf("package", "browser name", "domain", "URL", "website").forEach {
            assertFalse(body.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun laterMigrationsDoNotAddLifecycleWritesToPhaseFiveRepair() {
        assertTrue(database.contains("version = 14"))
        assertTrue(database.contains("Migration11To12"))
        assertTrue(database.contains("Migration12To13"))
        assertFalse(screen.contains("markCompleted"))
        assertFalse(screen.contains("updateFeedback"))
        assertFalse(viewModel.contains("markCompleted"))
        assertFalse(viewModel.contains("updateFeedback"))
    }

    @Test
    fun chooserRefreshesFromRoomOnResumeWithoutResettingPrompts() {
        assertTrue(screen.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(screen.contains("viewModel.refreshAfterReturn()"))
        val refresh = viewModel.substring(
            viewModel.indexOf("fun refreshAfterReturn()"),
            viewModel.indexOf("fun choose("),
        )
        assertTrue(refresh.contains("chooserRefresh.load(decisionId)"))
        assertTrue(refresh.contains("decision = loaded.decision"))
        assertFalse(refresh.contains("selectedCue ="))
        assertFalse(refresh.contains("urgeRating ="))
        assertFalse(refresh.contains("followUpSupport.chooseAnother"))
    }

    @Test
    fun everyTapBranchesOnLatestPersistedDecisionAndClearsGuard() {
        val choose = viewModel.substring(
            viewModel.indexOf("fun choose("),
            viewModel.indexOf("fun startMomentPlan("),
        )
        assertTrue(choose.contains("choiceOperationGuard.tryStart()"))
        assertTrue(choose.contains("decisions.getById(cachedDecision.decisionId)"))
        assertTrue(choose.contains("decision.startedAtMillis != null"))
        assertTrue(choose.contains("followUpSupport.chooseAnother"))
        assertTrue(choose.contains("previousDecisionId = decision.decisionId"))
        assertTrue(choose.contains("routeRequest = followUp.routeRequest"))
        assertTrue(choose.contains("finally"))
        assertTrue(choose.contains("choiceOperationGuard.clear()"))
    }

    @Test
    fun momentPlanStartsOnlyFromGenuineActionAndBackUsesOutcomeHandling() {
        val route = navigation.substring(
            navigation.indexOf("route = AppRoutes.MomentPlanRun"),
            navigation.indexOf("route = AppRoutes.ImpulsiveBlock"),
        )
        assertFalse(route.contains("AdaptiveStartedEffect(decisionId)"))
        assertTrue(screen.contains("viewModel.dismissCurrentIntervention()"))
        assertTrue(viewModel.contains("markStartedAfterSuccessfulEntry()"))
        assertFalse(route.substringBefore("MomentPlanRunScreen(").contains(
            "navigate(AppRoutes.adaptiveMoment(decisionId))",
        ))
    }
}
