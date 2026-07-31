package com.impulsive.app.frontend.screens.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowSuggestionsWorkScreen(
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How suggestions work") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Explanation(
                    "First moment",
                    "The first step stays simple. Impulsive begins with a short pause " +
                        "rather than comparing every support option immediately.",
                )
            }
            item {
                Explanation(
                    "Repeated moment",
                    "If another protected moment happens soon, Impulsive may suggest " +
                        "another available support option.",
                )
            }
            item {
                Explanation(
                    "Personal history",
                    "Suggestions can use your recent on-device support history, while " +
                        "keeping different kinds of evidence separate.",
                )
            }
            item {
                Explanation(
                    "Occasional variation",
                    "Impulsive occasionally varies suggestions so it does not repeat " +
                        "the same option every time.",
                )
            }
            item {
                Explanation(
                    "Your choice",
                    "You can choose another available option. The original suggestion " +
                        "and your actual choice are recorded separately.",
                )
            }
            item {
                Explanation(
                    "Private learning",
                    "Your Moment Plans and adaptive support history stay encrypted on " +
                        "this device and in your encrypted recovery backup when you enable it.",
                )
            }
        }
    }
}

@Composable
private fun Explanation(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
