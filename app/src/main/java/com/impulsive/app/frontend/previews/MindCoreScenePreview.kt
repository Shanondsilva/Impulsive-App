package com.impulsive.app.frontend.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.impulsive.app.core.util.TimeOfDay
import com.impulsive.app.frontend.components.MindCoreScene
import com.impulsive.app.frontend.theme.ImpulsiveTheme

@Preview(
    name = "MindCoreScene levels 1-5",
    showBackground = true,
    backgroundColor = 0xFFF3FBF6L,
    heightDp = 1600,
)
@Composable
private fun MindCoreScenePreview() {
    ImpulsiveTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            (1..5).forEach { lvl ->
                Text("Level $lvl", style = MaterialTheme.typography.labelMedium)
                MindCoreScene(
                    level = lvl,
                    timeOfDay = TimeOfDay.Afternoon,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
