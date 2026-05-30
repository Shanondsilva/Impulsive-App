package com.impulsive.app.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.domain.model.journal.JournalNoteType
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.screens.games.BlockCascadeScreen
import com.impulsive.app.frontend.screens.games.ReflexGameScreen
import com.impulsive.app.frontend.screens.games.RecoveryGamesScreen
import com.impulsive.app.frontend.screens.intro.IntroScreen
import com.impulsive.app.frontend.screens.journal.JournalEditorScreen
import com.impulsive.app.frontend.screens.journal.JournalHubScreen
import com.impulsive.app.frontend.screens.journal.JournalListScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen
import com.impulsive.app.frontend.screens.progress.ProgressDashboardScreen
import com.impulsive.app.frontend.screens.tasks.FutureSelfMessageScreen
import com.impulsive.app.frontend.screens.tasks.FutureSelfRecordScreen
import com.impulsive.app.frontend.screens.tasks.MindLessonScreen
import com.impulsive.app.frontend.screens.tasks.PatternBreakScreen
import com.impulsive.app.frontend.screens.tasks.ResetReadScreen
import com.impulsive.app.frontend.screens.tasks.TaskToCompleteScreen

object DemoRoutes {
    const val Intro = "intro"
    const val WelcomePrivacy = "welcome_privacy"
    const val Home = "home"
    const val Score = "score"
    const val TaskToComplete = "task_to_complete"
    const val RecoveryGames = "recovery_games"
    const val ReflexGame = "reflex_game"
    const val ReflexGameTask = "reflex_game_task"
    const val PatternBreakTask = "pattern_break_task"
    const val BlockCascade = "block_cascade"
    const val BlockCascadeTask = "block_cascade_task"
    const val MindLessonTask = "mind_lesson_task"
    const val ResetReadTask = "reset_read_task"
    const val FutureSelfMessageTask = "future_self_message_task"
    const val FutureSelfRecord = "future_self_record"
    const val JournalHub = "journal_hub"
    const val JournalList = "journal_list"
    const val JournalNoteNew = "journal_note_new/{type}"
    const val JournalNoteEdit = "journal_note_edit/{noteId}"

    fun journalNoteNew(type: JournalNoteType): String = "journal_note_new/${type.storageValue}"
    fun journalNoteEdit(noteId: Long): String = "journal_note_edit/$noteId"
}

@Composable
fun DemoNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = DemoRoutes.Intro,
    ) {
        composable(DemoRoutes.Intro) {
            IntroScreen(
                onIntroFinished = {
                    navController.navigate(DemoRoutes.WelcomePrivacy) {
                        popUpTo(DemoRoutes.Intro) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(DemoRoutes.WelcomePrivacy) {
            WelcomePrivacyScreen(
                onBeginSetup = { _, _ ->
                    navController.navigate(DemoRoutes.Home) {
                        popUpTo(DemoRoutes.WelcomePrivacy) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(DemoRoutes.Home) {
            HomeScreen(
                onOpenRecoveryGames = {
                    navController.navigate(DemoRoutes.RecoveryGames)
                },
                onOpenJournal = {
                    navController.navigate(DemoRoutes.JournalHub)
                },
                onOpenReflexOverrideTask = {
                    navController.navigate(DemoRoutes.ReflexGameTask)
                },
                onOpenPatternBreakTask = {
                    navController.navigate(DemoRoutes.PatternBreakTask)
                },
                onOpenBlockCascadeTask = {
                    navController.navigate(DemoRoutes.BlockCascadeTask)
                },
                onOpenMindLessonTask = {
                    navController.navigate(DemoRoutes.MindLessonTask)
                },
                onOpenResetReadTask = {
                    navController.navigate(DemoRoutes.ResetReadTask)
                },
                onOpenFutureSelfMessageTask = {
                    navController.navigate(DemoRoutes.FutureSelfMessageTask)
                },
                onOpenTasks = {
                    navController.navigate(DemoRoutes.TaskToComplete)
                },
                onOpenScore = {
                    navController.navigate(DemoRoutes.Score)
                },
            )
        }



        composable(DemoRoutes.Score) {
            ProgressDashboardScreen(
                onOpenHome = {
                    navController.navigate(DemoRoutes.Home) {
                        popUpTo(DemoRoutes.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenSettings = {},
            )
        }

        composable(DemoRoutes.JournalHub) {
            JournalHubScreen(
                onBack = { navController.popBackStack() },
                onOpenFutureSelf = { navController.navigate(DemoRoutes.FutureSelfRecord) },
                onOpenNormalJournal = { navController.navigate(DemoRoutes.JournalList) },
                onCreateNote = { type -> navController.navigate(DemoRoutes.journalNoteNew(type)) },
                onOpenNote = { noteId -> navController.navigate(DemoRoutes.journalNoteEdit(noteId)) },
            )
        }

        composable(DemoRoutes.JournalList) {
            JournalListScreen(
                onBack = { navController.popBackStack() },
                onCreateNote = { type -> navController.navigate(DemoRoutes.journalNoteNew(type)) },
                onOpenNote = { noteId -> navController.navigate(DemoRoutes.journalNoteEdit(noteId)) },
            )
        }

        composable(
            route = DemoRoutes.JournalNoteNew,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStackEntry ->
            val noteType = JournalNoteType.fromStorage(backStackEntry.arguments?.getString("type").orEmpty())
            JournalEditorScreen(
                noteId = 0L,
                initialType = noteType,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = DemoRoutes.JournalNoteEdit,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { backStackEntry ->
            JournalEditorScreen(
                noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L,
                initialType = JournalNoteType.Text,
                onBack = { navController.popBackStack() },
            )
        }

        composable(DemoRoutes.TaskToComplete) {
            TaskToCompleteScreen(
                onBack = { navController.popBackStack() },
                onOpenReflexOverrideTask = {
                    navController.navigate(DemoRoutes.ReflexGameTask)
                },
                onOpenPatternBreakTask = {
                    navController.navigate(DemoRoutes.PatternBreakTask)
                },
                onOpenBlockCascadeTask = {
                    navController.navigate(DemoRoutes.BlockCascadeTask)
                },
                onOpenMindLessonTask = {
                    navController.navigate(DemoRoutes.MindLessonTask)
                },
                onOpenResetReadTask = {
                    navController.navigate(DemoRoutes.ResetReadTask)
                },
                onOpenFutureSelfMessageTask = {
                    navController.navigate(DemoRoutes.FutureSelfMessageTask)
                },
            )
        }

        composable(DemoRoutes.RecoveryGames) {
            RecoveryGamesScreen(
                onBack = { navController.popBackStack() },
                onOpenReflexOverride = { navController.navigate(DemoRoutes.ReflexGame) },
                onOpenBlockCascade = { navController.navigate(DemoRoutes.BlockCascade) },
            )
        }

        composable(DemoRoutes.ReflexGame) {
            ReflexGameScreen(
                onExit = { navController.popBackStack() },
                launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
            )
        }

        composable(DemoRoutes.ReflexGameTask) {
            ReflexGameScreen(
                onExit = { navController.popBackStack() },
                launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
            )
        }

        composable(DemoRoutes.PatternBreakTask) {
            PatternBreakScreen(
                onExit = { navController.popBackStack() },
            )
        }

        composable(DemoRoutes.BlockCascade) {
            BlockCascadeScreen(
                onExit = { navController.popBackStack() },
                launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
            )
        }

        composable(DemoRoutes.BlockCascadeTask) {
            BlockCascadeScreen(
                onExit = { navController.popBackStack() },
                launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
            )
        }

        composable(DemoRoutes.MindLessonTask) {
            MindLessonScreen(
                onExit = { navController.popBackStack() },
            )
        }

        composable(DemoRoutes.ResetReadTask) {
            ResetReadScreen(
                onExit = { navController.popBackStack() },
            )
        }

        composable(DemoRoutes.FutureSelfMessageTask) {
            FutureSelfMessageScreen(
                onExit = { navController.popBackStack() },
                onRecordYours = { navController.navigate(DemoRoutes.FutureSelfRecord) },
            )
        }

        composable(DemoRoutes.FutureSelfRecord) {
            FutureSelfRecordScreen(
                onExit = { navController.popBackStack() },
            )
        }
    }
}
