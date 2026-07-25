package com.impulsive.app.frontend.screens.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource
import com.impulsive.app.core.security.BiometricGate
import kotlinx.coroutines.launch

@Composable
fun AppLockGateScreen(
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit,
) {
    BackHandler(enabled = true) {}

    val context = LocalContext.current
    val activity = LocalActivity.current as? FragmentActivity
    val dataSource = remember(context) { AppLockPreferencesDataSource(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember(activity) {
        activity?.let(BiometricGate::isBiometricAvailable) == true
    }
    var showPin by remember { mutableStateOf(!biometricAvailable) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntilMillis by remember { mutableStateOf(0L) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        lockedUntilMillis = dataSource.currentAttemptState().lockedUntilEpochMillis
    }
    LaunchedEffect(lockedUntilMillis) {
        while (System.currentTimeMillis() < lockedUntilMillis) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(500L)
        }
        nowMillis = System.currentTimeMillis()
    }
    val locked = nowMillis < lockedUntilMillis
    val lockoutSecondsLeft = ((lockedUntilMillis - nowMillis + 999L) / 1000L).coerceAtLeast(0L)

    LaunchedEffect(biometricAvailable, activity) {
        if (biometricAvailable && activity != null) {
            BiometricGate.prompt(
                activity = activity,
                title = "Unlock Impulsive",
                subtitle = "Confirm it is you to continue.",
                onSuccess = {
                    scope.launch {
                        dataSource.resetAttempts()
                        onUnlocked()
                    }
                },
                onUsePin = {
                    error = null
                    showPin = true
                },
                onError = {
                    showPin = true
                },
            )
        }
    }

    LockScaffold {
        Text(
            text = "Unlock Impulsive",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "App lock is on. Use your fingerprint or PIN to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        if (showPin) {
            PinDots(pin.length)
            Spacer(Modifier.height(20.dp))
            if (locked) {
                Text(
                    text = "Too many attempts. Try again in ${lockoutSecondsLeft}s.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(14.dp))
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(14.dp))
            }
            PinPad(
                onDigit = { digit ->
                    if (!locked && pin.length < 4) {
                        val next = pin + digit
                        pin = next
                        error = null
                        if (next.length == 4) {
                            scope.launch {
                                if (dataSource.verifyPin(next)) {
                                    dataSource.resetAttempts()
                                    onUnlocked()
                                } else {
                                    pin = ""
                                    val state = dataSource.recordFailedAttempt(System.currentTimeMillis())
                                    lockedUntilMillis = state.lockedUntilEpochMillis
                                    error = if (state.lockedUntilEpochMillis > System.currentTimeMillis()) {
                                        "Too many attempts. Try again shortly."
                                    } else {
                                        "Incorrect PIN"
                                    }
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    pin = pin.dropLast(1)
                    error = null
                },
            )
        } else {
            Button(onClick = { showPin = true }) {
                Text("Use PIN")
            }
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onForgotPin) {
            Text("Forgot PIN?")
        }
    }
}

@Composable
fun SetPinScreen(
    onPinSet: () -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(enabled = true) { onCancel() }

    val context = LocalContext.current
    val dataSource = remember(context) { AppLockPreferencesDataSource(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var firstPin by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val confirming = firstPin != null

    LockScaffold {
        Text(
            text = if (confirming) "Confirm PIN" else "Set App Lock",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (confirming) {
                "Enter the same 4-digit PIN again."
            } else {
                "App lock keeps your support data private. You will unlock Impulsive with your fingerprint or PIN each time you open it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        PinDots(pin.length)
        Spacer(Modifier.height(20.dp))
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(14.dp))
        }
        PinPad(
            onDigit = { digit ->
                if (pin.length < 4) {
                    val next = pin + digit
                    pin = next
                    error = null
                    if (next.length == 4) {
                        val savedFirstPin = firstPin
                        if (savedFirstPin == null) {
                            firstPin = next
                            pin = ""
                        } else if (savedFirstPin == next) {
                            scope.launch {
                                dataSource.setPin(next)
                                onPinSet()
                            }
                        } else {
                            firstPin = null
                            pin = ""
                            error = "PINs did not match. Start again."
                        }
                    }
                }
            },
            onDelete = {
                pin = pin.dropLast(1)
                error = null
            },
        )
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
private fun LockScaffold(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 32.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LockBrandMark()
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}

@Composable
private fun LockBrandMark() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(4) { index ->
            val filled = index < count
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier.background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                                shape = CircleShape,
                            )
                        },
                    ),
            )
        }
    }
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "Del"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { label ->
                    PinPadButton(
                        label = label,
                        onClick = {
                            when {
                                label.isBlank() -> Unit
                                label == "Del" -> onDelete()
                                else -> onDigit(label)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinPadButton(
    label: String,
    onClick: () -> Unit,
) {
    if (label.isBlank()) {
        Spacer(modifier = Modifier.size(72.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label == "Del") {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                modifier = Modifier.size(24.dp),
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
