package com.impulsive.app.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.impulsive.app.backend.domain.model.onboarding.OnboardingQuestionId
import com.impulsive.app.backend.domain.game.ReflexGameLaunchSource
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.backend.session.onboarding.OnboardingViewModel
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.screens.games.ReflexGameScreen
import com.impulsive.app.frontend.screens.intro.IntroScreen
import com.impulsive.app.frontend.screens.onboarding.LoginSignupGuestScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingDailyRelapseCountScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingQuestionScreen
import com.impulsive.app.frontend.screens.onboarding.OnboardingStartingPointScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen
import com.impulsive.app.frontend.screens.settings.SettingsScreen
import com.impulsive.app.frontend.screens.tasks.PatternBreakScreen
import com.impulsive.app.frontend.screens.tasks.TaskToCompleteScreen

object OnboardingRoutes {
    const val LogoIntro = "logo_intro"
    const val LoginSignupGuest = "login_signup_guest"
    const val WelcomePrivacy = "welcome_privacy"
    const val QuestionInterrupting = "question_interrupting"
    const val QuestionTiming = "question_timing"
    const val QuestionTriggers = "question_triggers"
    const val QuestionWeekOne = "question_week_one"
    const val QuestionDailyRelapseCount = "question_daily_relapse_count"
    const val StartingPoint = "starting_point"
    const val LevelOneReveal = "level_one_reveal"
    const val Settings = "settings"
    const val ReflexGame = "reflex_game"
    const val ReflexGameTask = "reflex_game_task"
    const val PatternBreakTask = "pattern_break_task"
    const val TaskToComplete = "task_to_complete"
}

@Composable
fun OnboardingNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = viewModel(),
    authViewModel: AuthViewModel,
) {
    val state by onboardingViewModel.state.collectAsState()
    var pendingDailyRelapseCount by remember { mutableStateOf<Int?>(null) }

    if (state.isLoading) {
        return
    }

    LaunchedEffect(pendingDailyRelapseCount, state.answers.dailyRelapseUrgeCount) {
        val pendingCount = pendingDailyRelapseCount
        if (pendingCount != null && state.answers.dailyRelapseUrgeCount == pendingCount) {
            pendingDailyRelapseCount = null
            navController.navigateForward(OnboardingRoutes.StartingPoint)
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (state.isCompleted) {
            OnboardingRoutes.LevelOneReveal
        } else {
            OnboardingRoutes.LogoIntro
        },
    ) {
        composable(OnboardingRoutes.LogoIntro) {
            IntroScreen(
                onIntroFinished = {
                    navController.navigateForward(OnboardingRoutes.LoginSignupGuest)
                },
            )
        }

        composable(OnboardingRoutes.LoginSignupGuest) {
            LoginSignupGuestScreen(
                onAuthenticated = {
                    navController.navigateForward(OnboardingRoutes.WelcomePrivacy)
                },
                authViewModel = authViewModel,
            )
        }

        composable(OnboardingRoutes.WelcomePrivacy) {
            WelcomePrivacyScreen(
                initialName = state.answers.name,
                initialAvatarId = state.answers.avatarId,
                onBeginSetup = { name, avatarId ->
                    onboardingViewModel.savePersonalization(
                        name = name,
                        avatarId = avatarId,
                    ) {
                        navController.navigateForward(OnboardingRoutes.QuestionInterrupting)
                    }
                },
            )
        }

        composable(OnboardingRoutes.QuestionInterrupting) {
            OnboardingQuestionScreen(
                questionId = OnboardingQuestionId.Interrupting,
                state = state,
                onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                onBack = navController::navigateBack,
                onContinue = {
                    navController.navigateForward(OnboardingRoutes.QuestionTriggers)
                },
                onSkip = null,
            )
        }

        composable(OnboardingRoutes.QuestionTriggers) {
            OnboardingQuestionScreen(
                questionId = OnboardingQuestionId.Triggers,
                state = state,
                onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                onBack = navController::navigateBack,
                onContinue = {
                    navController.navigateForward(OnboardingRoutes.QuestionTiming)
                },
                onSkip = null,
            )
        }

        composable(OnboardingRoutes.QuestionTiming) {
            OnboardingQuestionScreen(
                questionId = OnboardingQuestionId.Timing,
                state = state,
                onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                onBack = navController::navigateBack,
                onContinue = {
                    navController.navigateForward(OnboardingRoutes.QuestionWeekOne)
                },
                onSkip = null,
            )
        }

        composable(OnboardingRoutes.QuestionWeekOne) {
            OnboardingQuestionScreen(
                questionId = OnboardingQuestionId.WeekOneGoal,
                state = state,
                onMultiSelectAnswerChanged = onboardingViewModel::setMultiSelectAnswer,
                onSingleSelectAnswerChanged = onboardingViewModel::setSingleSelectAnswer,
                onBack = navController::navigateBack,
                onContinue = {
                    navController.navigateForward(OnboardingRoutes.QuestionDailyRelapseCount)
                },
                onSkip = null,
            )
        }

        composable(OnboardingRoutes.QuestionDailyRelapseCount) {
            OnboardingDailyRelapseCountScreen(
                state = state,
                initialCount = state.answers.dailyRelapseUrgeCount,
                onBack = navController::navigateBack,
                onContinue = { selectedCount ->
                    onboardingViewModel.setDailyRelapseUrgeCount(selectedCount)
                    pendingDailyRelapseCount = selectedCount
                },
            )
        }

        composable(OnboardingRoutes.StartingPoint) {
            OnboardingStartingPointScreen(
                state = state,
                onBack = navController::navigateBack,
                onContinue = {
                    onboardingViewModel.completeOnboarding {
                        navController.navigateToMainClearingOnboarding()
                    }
                },
            )
        }

        composable(OnboardingRoutes.LevelOneReveal) {
            HomeScreen(
                onOpenReflexGame = {
                    navController.navigate(OnboardingRoutes.ReflexGame)
                },
                onOpenReflexOverrideTask = {
                    navController.navigate(OnboardingRoutes.ReflexGameTask)
                },
                onOpenPatternBreakTask = {
                    navController.navigate(OnboardingRoutes.PatternBreakTask)
                },
                onOpenTasks = {
                    navController.navigate(OnboardingRoutes.TaskToComplete)
                },
                onOpenSettings = {
                    navController.navigate(OnboardingRoutes.Settings)
                },
            )
        }

        composable(OnboardingRoutes.Settings) {
            SettingsScreen(
                onBackHome = {
                    val isResumed = navController.currentBackStackEntry
                        ?.lifecycle
                        ?.currentState
                        ?.isAtLeast(Lifecycle.State.RESUMED) == true
                    if (isResumed) {
                        navController.navigateBackToMain()
                    }
                },
            )
        }

        composable(OnboardingRoutes.ReflexGame) {
            ReflexGameScreen(
                onExit = { navController.safePopBackStack() },
                launchSource = ReflexGameLaunchSource.RECOVERY_GAME,
            )
        }

        composable(OnboardingRoutes.ReflexGameTask) {
            ReflexGameScreen(
                onExit = { navController.safePopBackStack() },
                launchSource = ReflexGameLaunchSource.TASK_TO_COMPLETE,
            )
        }

        composable(OnboardingRoutes.PatternBreakTask) {
            PatternBreakScreen(
                onExit = { navController.safePopBackStack() },
            )
        }

        composable(OnboardingRoutes.TaskToComplete) {
            TaskToCompleteScreen(
                onBack = { navController.safePopBackStack() },
                onOpenReflexOverrideTask = {
                    navController.navigate(OnboardingRoutes.ReflexGameTask)
                },
                onOpenPatternBreakTask = {
                    navController.navigate(OnboardingRoutes.PatternBreakTask)
                },
            )
        }
    }
}

private fun NavHostController.navigateForward(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}

private fun NavHostController.navigateBack() {
    popBackStack()
}

private fun NavHostController.safePopBackStack() {
    val hasPrevious = previousBackStackEntry != null
    val isResumed = currentBackStackEntry
        ?.lifecycle
        ?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) == true
    if (hasPrevious && isResumed) {
        popBackStack()
    }
}

private fun NavHostController.navigateToMainClearingOnboarding() {
    navigate(OnboardingRoutes.LevelOneReveal) {
        popUpTo(OnboardingRoutes.LogoIntro) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

private fun NavHostController.navigateBackToMain() {
    navigate(OnboardingRoutes.LevelOneReveal) {
        popUpTo(OnboardingRoutes.LevelOneReveal) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
