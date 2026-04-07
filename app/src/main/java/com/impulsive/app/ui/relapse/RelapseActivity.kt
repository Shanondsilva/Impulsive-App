package com.impulsive.app.ui.relapse

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.ui.theme.ImpulsiveTheme
import com.impulsive.app.viewmodel.RelapseViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class RelapseActivity : ComponentActivity() {

    private val vm: RelapseViewModel by viewModel()

    companion object {
        fun start(context: Context) {
            context.startActivity(
                Intent(context, RelapseActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ImpulsiveTheme {
                val state by vm.state.collectAsState()
                RelapseScreen(
                    isRestoring = state.isRestoring,
                    onRestore   = { vm.restoreSanctuary { finish() } },
                    onReturn    = { vm.returnToFocus { finish() } }
                )
            }
        }
    }
}

@Composable
private fun RelapseScreen(
    isRestoring: Boolean,
    onRestore: () -> Unit,
    onReturn: () -> Unit
) {
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
                text = "You bypassed the shield.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "That happens. It does not erase yesterday's progress. Your progress is still here. Nothing was erased.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RelapseActionCard(
                title = "Review the trigger",
                body  = "Understanding what drove the bypass is more valuable than regret. Which trigger was active in that moment?"
            )
            RelapseActionCard(
                title = "Restore the sanctuary",
                body  = "Re-enable the permissions that keep Impulsive running. The shield needs your support to protect you."
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRestore,
                enabled = !isRestoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor   = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Re-activate Shield", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                text     = "Return to Focus",
                style    = MaterialTheme.typography.labelLarge,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onReturn() }
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text         = "THIS IS A PRACTICE, NOT A PERFORMANCE",
                style        = MaterialTheme.typography.labelSmall,
                color        = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.5.sp,
                textAlign    = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RelapseActionCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text  = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
