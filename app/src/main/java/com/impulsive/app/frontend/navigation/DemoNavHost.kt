package com.impulsive.app.frontend.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.impulsive.app.frontend.screens.intro.IntroScreen
import com.impulsive.app.frontend.screens.onboarding.WelcomePrivacyScreen

object DemoRoutes {
    const val Intro = "intro"
    const val WelcomePrivacy = "welcome_privacy"
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
                    // Next onboarding screen goes here
                },
            )
        }
    }
}
