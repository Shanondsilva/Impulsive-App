package com.impulsive.app.ui.timer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.impulsive.app.ui.theme.JetBrainsMono
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.service.SessionTimerService
import com.impulsive.app.ui.intercept.InterceptActivity
import com.impulsive.app.ui.theme.ImpulsiveTheme
import kotlinx.coroutines.delay

class SessionTimerActivity : ComponentActivity() {

    companion object {
        const val ACTION_TICK    = "com.impulsive.app.TIMER_TICK"
        const val ACTION_EXPIRED = "com.impulsive.app.TIMER_EXPIRED"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImpulsiveTheme {
                SessionTimerScreen(
                    totalMs = SessionTimerService.SESSION_DURATION_MS,
                    onExpired = {
                        SessionTimerService.stop(this)
                        startActivity(
                            Intent(this, InterceptActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* blocked during session */ }
}

@Composable
private fun SessionTimerScreen(
    totalMs: Long,
    onExpired: () -> Unit
) {
    var elapsedMs by remember { mutableLongStateOf(0L) }

    // Resume from wherever the service started — survives reopen
    LaunchedEffect(Unit) {
        val serviceStart = SessionTimerService.sessionStartTime
            .takeIf { it > 0L } ?: System.currentTimeMillis()
        while (elapsedMs < totalMs) {
            delay(100)
            elapsedMs = System.currentTimeMillis() - serviceStart
        }
        onExpired()
    }

    val remainingMs  = (totalMs - elapsedMs).coerceAtLeast(0L)
    val remainingSec = remainingMs / 1000
    val progress     = (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val formatted    = "%02d:%02d".format(remainingSec / 60, remainingSec % 60)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 200),
        label = "timer_progress"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor   = MaterialTheme.colorScheme.surfaceContainerHigh

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
                text = "IMPULSIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "SESSION FOCUS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 2.sp
            )
        }

        // Circular countdown ring
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                // Remaining arc (counts down — sweeps backwards)
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = -360f * animatedProgress,
                    useCenter = false,
                    style = stroke
                )
            }
            Text(
                text = formatted,
                fontFamily = JetBrainsMono,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Linear progress bar
        LinearProgressIndicator(
            progress = { 1f - animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round
        )

        Text(
            text = "When this ends, you will be asked again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
