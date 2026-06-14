package com.impulsive.app.frontend.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun GameSoundToggle(
    enabled: Boolean,
    tint: Color,
    onToggle: (Boolean) -> Unit,
) {
    IconButton(onClick = { onToggle(!enabled) }) {
        Icon(
            imageVector = if (enabled) {
                Icons.AutoMirrored.Filled.VolumeUp
            } else {
                Icons.AutoMirrored.Filled.VolumeOff
            },
            contentDescription = if (enabled) "Sound on" else "Sound off",
            tint = tint,
        )
    }
}
