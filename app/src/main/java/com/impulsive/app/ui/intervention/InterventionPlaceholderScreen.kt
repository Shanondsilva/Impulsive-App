package com.impulsive.app.ui.intervention

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun InterventionPlaceholderScreen(trigger: String, onComplete: () -> Unit) {
    val context = LocalContext.current

    val (title, body, actionLabel) = when (trigger) {
        "Bored" -> Triple(
            "Two-Minute Grounding",
            "Name 5 things you can see. 4 you can touch. 3 you can hear. 2 you can smell. 1 you can taste. Stay with the present moment.",
            "I'm grounded"
        )
        "Lonely" -> Triple(
            "Reach Out Instead",
            "The urge to scroll is often a proxy for connection. Send a message to someone who matters. A real one.",
            "Open Messages"
        )
        "Tired" -> Triple(
            "Rest Alarm",
            "Your resistance is low because your body needs rest. Set an alarm for 20 minutes and close your eyes.",
            "Set Rest Alarm"
        )
        "Habit" -> Triple(
            "Extended Hold",
            "This was automatic. The antidote is deliberate friction. Sit for 60 seconds without touching your phone.",
            "I waited"
        )
        else -> Triple("Pause", "Take a moment.", "Continue")
    }

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
                text = "INTERVENTION MODE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Button(
            onClick = {
                when (trigger) {
                    "Lonely" -> {
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                        }
                        runCatching { context.startActivity(smsIntent) }
                        onComplete()
                    }
                    "Tired" -> {
                        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                            putExtra(AlarmClock.EXTRA_MESSAGE, "Rest")
                        }
                        runCatching { context.startActivity(alarmIntent) }
                        onComplete()
                    }
                    else -> onComplete()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
