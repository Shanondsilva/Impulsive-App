package com.impulsive.app.frontend.screens.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.impulsive.app.backend.domain.model.adaptive.FeedbackCode
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.session.adaptive.AdaptiveFeedbackMode
import com.impulsive.app.backend.session.adaptive.AdaptiveFeedbackViewModel

@Composable
fun AdaptiveFeedbackScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AdaptiveFeedbackViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.mode) {
            AdaptiveFeedbackMode.Loading,
            AdaptiveFeedbackMode.Saving,
            -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.mode == AdaptiveFeedbackMode.Saving) {
                        "Saving privately on this device..."
                    } else {
                        "Loading..."
                    },
                )
            }
            AdaptiveFeedbackMode.Ready -> {
                Text(
                    if (state.dismissed) {
                        "Was this useful right now?"
                    } else {
                        "How did that feel?"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                state.intervention?.let {
                    Text(
                        interventionLabel(it),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Your answer stays on this device and helps Impulsive vary future suggestions.",
                )
                Spacer(Modifier.height(22.dp))
                FeedbackButton("It helped") {
                    viewModel.submitFeedback(FeedbackCode.Helped)
                }
                FeedbackButton("It helped a little") {
                    viewModel.submitFeedback(FeedbackCode.HelpedALittle)
                }
                FeedbackButton("It didn't help") {
                    viewModel.submitFeedback(FeedbackCode.DidNotHelp)
                }
                FeedbackButton("The timing was wrong") {
                    viewModel.submitFeedback(FeedbackCode.WrongTiming)
                }
                TextButton(
                    onClick = viewModel::skip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Skip")
                }
            }
            AdaptiveFeedbackMode.Saved -> {
                Text(
                    "Thanks. Your response was saved privately on this device.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
                OutlinedButton(
                    onClick = viewModel::changeAnswer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Change answer")
                }
            }
            AdaptiveFeedbackMode.RetryableFailure -> {
                Text(
                    "Your response could not be saved yet.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text("Protection is still active. You can try again.")
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::changeAnswer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try again")
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
            AdaptiveFeedbackMode.Unavailable -> {
                Text(
                    "Feedback is unavailable for this support moment.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
            }
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

private fun interventionLabel(intervention: InterventionFamily): String = when (intervention) {
    InterventionFamily.ShortPause -> "Short Pause"
    InterventionFamily.PivotGame -> "Pivot Game"
    InterventionFamily.PivotReading -> "Reset Reading"
    InterventionFamily.MomentPlan -> "Moment Plan"
}
