package com.impulsive.app.frontend.screens.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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

    val activity = LocalContext.current as FragmentActivity
    val dataSource = remember(activity) { AppLockPreferencesDataSource(activity.applicationContext) }
    val scope = rememberCoroutineScope()
    val biometricAvailable = remember(activity) { BiometricGate.isBiometricAvailable(activity) }
    var showPin by remember { mutableStateOf(!biometricAvailable) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) {
            BiometricGate.prompt(
                activity = activity,
                title = "Unlock Impulsive",
                subtitle = "Confirm it is you to continue.",
                onSuccess = onUnlocked,
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "App lock is on. Use your fingerprint or PIN to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (showPin) {
            PinDots(pin.length)
            Spacer(Modifier.height(16.dp))
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(10.dp))
            }
            PinPad(
                onDigit = { digit ->
                    if (pin.length < 4) {
                        val next = pin + digit
                        pin = next
                        error = null
                        if (next.length == 4) {
                            scope.launch {
                                if (dataSource.verifyPin(next)) {
                                    onUnlocked()
                                } else {
                                    pin = ""
                                    error = "Incorrect PIN"
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
        Spacer(Modifier.height(12.dp))
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (confirming) {
                "Enter the same 4-digit PIN again."
            } else {
                "App lock keeps your recovery data private. You will unlock Impulsive with your fingerprint or PIN each time you open it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PinDots(pin.length)
        Spacer(Modifier.height(16.dp))
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
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
        Spacer(Modifier.height(12.dp))
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < count) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = label.isNotBlank(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
