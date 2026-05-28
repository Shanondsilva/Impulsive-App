package com.impulsive.app.frontend.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.impulsive.app.frontend.screens.dashboard.HomeScreen
import com.impulsive.app.frontend.theme.ImpulsiveTheme

@Preview(name = "HomeScreen", showBackground = true, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    ImpulsiveTheme {
        HomeScreen()
    }
}
