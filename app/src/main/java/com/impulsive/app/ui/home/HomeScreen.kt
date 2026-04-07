package com.impulsive.app.ui.home

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.impulsive.app.service.AppMonitorService

@Composable
fun HomeScreen(identityAnchor: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotificationPermission by remember { mutableStateOf(hasNotificationPermission(context)) }
    var hasUsagePermission         by remember { mutableStateOf(hasUsageStatsPermission(context)) }
    var hasBatteryExemption        by remember { mutableStateOf(hasBatteryOptimizationExemption(context)) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = hasNotificationPermission(context)
                hasUsagePermission        = hasUsageStatsPermission(context)
                hasBatteryExemption       = hasBatteryOptimizationExemption(context)
                if (hasNotificationPermission && hasUsagePermission) {
                    AppMonitorService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            !hasNotificationPermission -> PermissionScreen(
                title = "Enable notifications.",
                reasons = listOf(
                    "Why this is required" to
                            "Android 10+ prevents apps from launching screens over other apps directly. Impulsive uses a notification to legally show the intercept the moment you open a monitored app. Without it, the intercept cannot appear.",
                    "What it will not do" to
                            "Impulsive will never send promotional alerts or reminders. The background monitor notification is silent and carries no sound or vibration."
                ),
                buttonLabel = "Allow Notifications",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )

            !hasUsagePermission -> PermissionScreen(
                title = "Allow usage access.",
                reasons = listOf(
                    "Why this is required" to
                            "Impulsive needs to know which app is in the foreground so it can intercept the moment you open a monitored app. Usage Access is the only on-device API that provides this. No data is sent anywhere.",
                    "How to grant it" to
                            "Tap the button below, find Impulsive in the list, and toggle it on. Then press back to return."
                ),
                buttonLabel = "Grant Usage Access",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            !hasBatteryExemption -> PermissionScreen(
                title = "Disable battery restriction.",
                reasons = listOf(
                    "Why this is required" to
                            "Android OEMs (Samsung, Xiaomi, OnePlus) aggressively kill background services to save battery. Without this exemption, your device may stop the monitor overnight, leaving monitored apps unblocked.",
                    "What it will not do" to
                            "This does not increase battery usage significantly. Impulsive's monitor uses less power than a standard music player. It simply prevents your device from silently killing it."
                ),
                buttonLabel = "Disable Battery Restriction",
                onAction = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            )

            else -> ActiveScreen(identityAnchor = identityAnchor)
        }
    }
}

@Composable
private fun PermissionScreen(
    title: String,
    reasons: List<Pair<String, String>>,
    buttonLabel: String,
    onAction: () -> Unit
) {
    Text(
        text = "IMPULSIVE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 4.sp
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        reasons.forEach { (label, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(buttonLabel, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActiveScreen(identityAnchor: String) {
    Text(
        text = "IMPULSIVE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 4.sp
    )
    Spacer(Modifier.height(24.dp))
    Text(
        text = "The sanctuary is active.",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
    if (identityAnchor.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = identityAnchor.replace(",", ", "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

fun hasUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun hasBatteryOptimizationExemption(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}
