package com.impulsive.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.impulsive.app.viewmodel.OnboardingViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun OnboardingHost(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            val enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            val exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            enter togetherWith exit
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            1 -> OnboardingBaseline(
                baseline = state.baselineSessions,
                onBaselineChange = viewModel::setBaseline,
                onNext = viewModel::nextStep
            )
            2 -> OnboardingTriggers(
                selected = state.selectedTriggers,
                onToggle = viewModel::toggleTrigger,
                onNext = viewModel::nextStep,
                onSkip = viewModel::skipTriggers
            )
            3 -> OnboardingIdentity(
                selected = state.identityAnchors,
                onToggle = viewModel::toggleIdentityAnchor,
                onNext = viewModel::nextStep,
                onBack = viewModel::prevStep
            )
            4 -> OnboardingPath(
                selected = state.path,
                onSelect = viewModel::setPath,
                isSaving = state.isSaving,
                onComplete = { viewModel.completeOnboarding(onComplete) }
            )
        }
    }
}
