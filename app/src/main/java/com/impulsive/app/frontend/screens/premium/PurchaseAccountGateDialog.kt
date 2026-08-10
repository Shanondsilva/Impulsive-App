package com.impulsive.app.frontend.screens.premium

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.impulsive.app.backend.data.repository.AuthResult
import com.impulsive.app.backend.domain.model.auth.PurchaseAccountGatePhase

@Composable
internal fun PurchaseAccountGateDialog(
    productName: String = "Plus",
    phase: PurchaseAccountGatePhase,
    authErrorMessage: String?,
    pendingConflict: AuthResult.AccountConflict?,
    onLinkGoogle: () -> Unit,
    onLinkFacebook: () -> Unit,
    onLinkEmail: (email: String, password: String) -> Unit,
    onConfirmAccountSwitch: () -> Unit,
    onDismissAccountSwitch: () -> Unit,
    onDismissAuthError: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showEmailForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(phase) {
        when (phase) {
            PurchaseAccountGatePhase.Ready,
            is PurchaseAccountGatePhase.AwaitingEmailVerification,
            PurchaseAccountGatePhase.AccountConflict,
            -> password = ""

            is PurchaseAccountGatePhase.Linking,
            PurchaseAccountGatePhase.RequiresDurableAccount,
            -> Unit
        }
    }

    val conflictVisible =
        phase == PurchaseAccountGatePhase.AccountConflict && pendingConflict != null

    AlertDialog(
        onDismissRequest = {
            if (!conflictVisible && phase !is PurchaseAccountGatePhase.Linking) {
                password = ""
                onDismiss()
            }
        },
        title = {
            Text(
                when {
                    conflictVisible -> "This account already exists"
                    phase is PurchaseAccountGatePhase.AwaitingEmailVerification ->
                        "Check your email"
                    else -> "Connect an account before subscribing"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    conflictVisible -> {
                        Text(
                            "This account is already connected to another Impulsive account. " +
                                "You can switch to that account, or cancel and keep using your " +
                                "current guest account. Your recovery data on this device will " +
                                "not be deleted.",
                        )
                        Button(
                            onClick = {
                                password = ""
                                onConfirmAccountSwitch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Switch account")
                        }
                        OutlinedButton(
                            onClick = {
                                password = ""
                                onDismissAccountSwitch()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Keep guest account")
                        }
                    }

                    phase is PurchaseAccountGatePhase.AwaitingEmailVerification -> {
                        Text(
                            "Verify your email, then return to Impulsive. " +
                                "Your subscription purchase will remain unavailable until " +
                                "the account is ready.",
                        )
                        phase.email?.let { Text(it) }
                        TextButton(
                            onClick = {
                                password = ""
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Close")
                        }
                    }

                    phase is PurchaseAccountGatePhase.Linking -> {
                        Text("Connecting your account…")
                    }

                    else -> {
                        Text(
                            "Your $productName purchase is linked to your Impulsive account. " +
                                "Connect Google, Facebook, or email before purchasing so you can " +
                                "restore $productName after reinstalling the app or changing devices. " +
                                "Your recovery data stays on this device.",
                        )

                        if (showEmailForm) {
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { onLinkEmail(email.trim(), password) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Link email")
                            }
                        } else {
                            Button(
                                onClick = onLinkGoogle,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Connect Google")
                            }
                            OutlinedButton(
                                onClick = onLinkFacebook,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Connect Facebook")
                            }
                            TextButton(
                                onClick = { showEmailForm = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Use email")
                            }
                        }

                        TextButton(
                            onClick = {
                                password = ""
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Not now")
                        }
                    }
                }

                authErrorMessage?.let { message ->
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                    Text(message)
                    TextButton(
                        onClick = onDismissAuthError,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}
