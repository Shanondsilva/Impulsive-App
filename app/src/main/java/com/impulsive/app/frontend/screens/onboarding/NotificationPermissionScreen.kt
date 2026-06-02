package com.impulsive.app.frontend.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.frontend.theme.ImpulsivePsychological
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics

@Composable
fun NotificationPermissionScreen(
    onContinue: () -> Unit,
    onPermissionResult: (Boolean) -> Unit = {},
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onPermissionResult(granted)
        onContinue()
    }

    OnboardingScreenShell(
        backgroundColors = listOf(
            Color(0xFFFFFEFC),
            Color(0xFFFCF8FD),
            Color(0xFFF6F2FA),
        ),
        stepUi = OnboardingFlowStep.Notifications.toStepUi(showBack = false),
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        haptics.light()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onPermissionResult(true)
                            onContinue()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ImpulsivePsychological,
                        contentColor = Color(0xFF635880),
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Text(
                        text = "Allow notifications",
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedButton(
                    onClick = {
                        onPermissionResult(false)
                        onContinue()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF635880),
                    ),
                ) {
                    Text(
                        text = "Not now",
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { compactHeight ->
        Spacer(modifier = Modifier.height(if (compactHeight) 44.dp else 74.dp))

        Box(
            modifier = Modifier
                .size(92.dp)
                .background(ImpulsivePsychological.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = Color(0xFF635880),
                modifier = Modifier.size(42.dp),
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        Text(
            text = "Stay protected at the right moment",
            color = Color(0xFF1C1B1E),
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Impulsive can remind you before planned windows, focus sessions, and support check-ins.",
            color = Color(0xFF48454E),
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.92f),
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "You can change this later in Settings.",
            color = Color(0xFF6D6874),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
