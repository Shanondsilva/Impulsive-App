package com.impulsive.app.frontend.screens.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.impulsive.app.backend.data.local.preferences.AppLockPreferencesDataSource
import com.impulsive.app.core.security.BiometricGate
import kotlinx.coroutines.launch

class AppLockGuardController {
    internal var pending by mutableStateOf<(() -> Unit)?>(null)

    fun run(enabled: Boolean, action: () -> Unit) {
        if (!enabled) action() else pending = action
    }
}

@Composable
fun rememberAppLockGuardController(): AppLockGuardController = remember { AppLockGuardController() }

@Composable
fun AppLockGuardHost(
    controller: AppLockGuardController,
    title: String = "Confirm it's you",
    subtitle: String = "Authenticate to change your protection settings.",
) {
    val pending = controller.pending ?: return
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    var showPin by remember(pending) { mutableStateOf(false) }
    var pin by remember(pending) { mutableStateOf("") }
    var error by remember(pending) { mutableStateOf(false) }

    fun consumeAndRun() {
        val action = controller.pending
        controller.pending = null
        action?.invoke()
    }

    LaunchedEffect(pending) {
        if (BiometricGate.isBiometricAvailable(activity)) {
            BiometricGate.prompt(
                activity = activity,
                title = title,
                subtitle = subtitle,
                onSuccess = { consumeAndRun() },
                onUsePin = { showPin = true },
                onError = { controller.pending = null },
            )
        } else {
            showPin = true
        }
    }

    if (showPin) {
        AlertDialog(
            onDismissRequest = { controller.pending = null },
            title = { Text(title) },
            text = {
                PinEntryField(
                    pin = pin,
                    isError = error,
                    onPinChange = { value ->
                        val next = value.filter { it.isDigit() }.take(4)
                        pin = next
                        error = false
                        if (next.length == 4) {
                            scope.launch {
                                val ok = AppLockPreferencesDataSource(context).verifyPin(next)
                                if (ok) {
                                    consumeAndRun()
                                } else {
                                    error = true
                                    pin = ""
                                }
                            }
                        }
                    },
                )
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { controller.pending = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PinEntryField(
    pin: String,
    isError: Boolean,
    onPinChange: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = pin,
            onValueChange = onPinChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = isError,
        )
        if (isError) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Incorrect PIN",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
