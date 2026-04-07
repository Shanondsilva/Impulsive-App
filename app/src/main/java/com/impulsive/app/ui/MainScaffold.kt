package com.impulsive.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import com.impulsive.app.ui.checkin.WeeklyCheckInScreen
import com.impulsive.app.ui.focus.FocusScreen
import com.impulsive.app.ui.home.HomeScreen
import com.impulsive.app.ui.settings.SettingsScreen

@Composable
fun MainScaffold(
    identityAnchor: String,
    openCheckIn: Boolean = false
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showCheckIn by rememberSaveable { mutableStateOf(openCheckIn) }

    if (showCheckIn) {
        WeeklyCheckInScreen(onComplete = { showCheckIn = false })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = androidx.compose.ui.unit.Dp.Unspecified
            ) {
                listOf("Home", "Focus", "Settings").forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(
                                text = when (index) {
                                    0 -> "⌂"
                                    1 -> "◎"
                                    else -> "⚙"
                                },
                                fontSize = 18.sp
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(identityAnchor = identityAnchor)
                1 -> FocusScreen(onNavigateToCheckIn = { showCheckIn = true })
                2 -> SettingsScreen()
            }
        }
    }
}
