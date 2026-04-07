package com.impulsive.app.ui.intercept

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.service.AppMonitorService
import com.impulsive.app.ui.gateway.GatewayScreen
import com.impulsive.app.ui.gateway.WalkAwayScreen
import com.impulsive.app.ui.intervention.BoxBreathingScreen
import com.impulsive.app.ui.intervention.InterventionPlaceholderScreen
import com.impulsive.app.ui.theme.ImpulsiveTheme
import com.impulsive.app.viewmodel.InterceptScreen
import com.impulsive.app.viewmodel.InterceptViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class InterceptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }

    private val viewModel: InterceptViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Tell ViewModel which app triggered this intercept
        intent.getStringExtra(EXTRA_PACKAGE)?.let { viewModel.setInterceptedPackage(it) }
        setContent {
            ImpulsiveTheme {
                val state by viewModel.state.collectAsState()
                when (state.screen) {
                    InterceptScreen.HOLD -> HoldScreen(
                        holdProgress = state.holdProgress,
                        holdSeconds = state.holdSeconds,
                        onPressStart = viewModel::onPressStart,
                        onPressRelease = viewModel::onPressRelease,
                        onDismiss = { dismiss() }
                    )
                    InterceptScreen.TRIGGER_ROUTING -> TriggerRoutingScreen(
                        onTriggerSelected = viewModel::selectTrigger
                    )
                    InterceptScreen.INTERVENTION -> when (state.selectedTrigger) {
                        "Stressed" -> BoxBreathingScreen(onComplete = viewModel::completeIntervention)
                        else -> InterventionPlaceholderScreen(
                            trigger = state.selectedTrigger,
                            onComplete = viewModel::completeIntervention
                        )
                    }
                    InterceptScreen.GATEWAY -> GatewayScreen(
                        sessionsLeft = state.sessionsLeftThisWeek,
                        onWalkAway = viewModel::walkAway,
                        onContinue = {
                            viewModel.continueSession(this@InterceptActivity)
                            dismiss()
                        }
                    )
                    InterceptScreen.WALK_AWAY -> WalkAwayScreen(
                        onFinish = { dismiss() }
                    )
                }
            }
        }
    }

    private fun dismiss() {
        startService(
            Intent(this, AppMonitorService::class.java).apply {
                action = AppMonitorService.ACTION_INTERCEPT_DISMISSED
            }
        )
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Back press does nothing — user must engage with the screen
    }
}

@Composable
private fun HoldScreen(
    holdProgress: Float,
    holdSeconds: Float,
    onPressStart: () -> Unit,
    onPressRelease: () -> Unit,
    onDismiss: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = holdProgress,
        animationSpec = tween(durationMillis = 80),
        label = "hold_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "This urge will pass.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You are the space it moves through.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Take fifteen seconds to observe the impulse without acting upon it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Press-and-hold ring
        Box(
            modifier = Modifier
                .size(240.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onPressStart()
                        // Wait for finger to lift
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        onPressRelease()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                // Progress
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = stroke
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "HOLD TO CONTINUE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (holdProgress == 0f) "15.0"
                    else "%.1f".format(holdSeconds),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "seconds remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Neural pathways for impulse control are strengthened each time you deliberately delay a reaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Tap anytime to dismiss impulse",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.noRippleClickable { onDismiss() }
            )
        }
    }
}

// Extension to suppress ripple on dismiss tap
private fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
            onClick()
        }
    }
