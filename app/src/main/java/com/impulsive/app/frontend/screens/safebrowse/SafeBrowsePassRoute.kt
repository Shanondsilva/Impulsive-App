package com.impulsive.app.frontend.screens.safebrowse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.impulsive.app.backend.data.repository.AuthResult
import com.impulsive.app.backend.domain.model.auth.PurchaseAccountGatePhase
import com.impulsive.app.backend.session.safebrowse.SafeBrowsePassViewModel
import com.impulsive.app.frontend.screens.premium.PurchaseAccountGateDialog
import kotlinx.coroutines.launch

@Composable
fun SafeBrowsePassRoute(
    passViewModel: SafeBrowsePassViewModel,
    purchaseAccountGatePhase: PurchaseAccountGatePhase,
    pendingAccountConflict: AuthResult.AccountConflict?,
    authErrorMessage: String?,
    onLinkGoogleForPurchase: () -> Unit,
    onLinkFacebookForPurchase: () -> Unit,
    onLinkEmailForPurchase: (email: String, password: String) -> Unit,
    onConfirmAccountSwitchForPurchase: () -> Unit,
    onDismissAccountSwitch: () -> Unit,
    onDismissAuthError: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by passViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAccountGate by rememberSaveable { mutableStateOf(false) }
    var standardPurchasePendingAfterAccountGate by rememberSaveable { mutableStateOf(false) }
    var prepaidTopUpPendingAfterAccountGate by rememberSaveable { mutableStateOf(false) }

    fun launchWhenReady(prepaidTopUp: Boolean) {
        context.findActivity()?.let { resumedActivity ->
            if (prepaidTopUp) {
                passViewModel.launchPrepaidTopUp(
                    activity = resumedActivity,
                    durableAccountReady = true,
                )
            } else {
                passViewModel.launchPurchase(
                    activity = resumedActivity,
                    durableAccountReady = true,
                )
            }
        }
    }

    fun requestPurchaseAction(prepaidTopUp: Boolean) {
        if (purchaseAccountGatePhase == PurchaseAccountGatePhase.Ready) {
            launchWhenReady(prepaidTopUp = prepaidTopUp)
            return
        }

        standardPurchasePendingAfterAccountGate = !prepaidTopUp
        prepaidTopUpPendingAfterAccountGate = prepaidTopUp
        showAccountGate = true
    }

    LaunchedEffect(
        purchaseAccountGatePhase,
        standardPurchasePendingAfterAccountGate,
        prepaidTopUpPendingAfterAccountGate,
    ) {
        if (purchaseAccountGatePhase != PurchaseAccountGatePhase.Ready) {
            return@LaunchedEffect
        }

        val launchStandard = standardPurchasePendingAfterAccountGate
        val launchTopUp = prepaidTopUpPendingAfterAccountGate

        if (!launchStandard && !launchTopUp) {
            return@LaunchedEffect
        }

        standardPurchasePendingAfterAccountGate = false
        prepaidTopUpPendingAfterAccountGate = false
        showAccountGate = false

        launchWhenReady(prepaidTopUp = launchTopUp)
    }

    SafeBrowsePassScreen(
        state = uiState,
        onBack = onBack,
        onSelectPeriod = passViewModel::selectPeriod,
        onPurchase = {
            requestPurchaseAction(prepaidTopUp = false)
        },
        onPrepaidTopUp = {
            requestPurchaseAction(prepaidTopUp = true)
        },
        onManageSubscription = {
            scope.launch {
                val uri = passViewModel.manageSubscriptionUri()

                val opened = uri != null &&
                    openSafeBrowsePassManagement(context = context, uri = uri)

                if (!opened) {
                    Toast.makeText(
                        context,
                        "Google Play subscription settings could not be opened.",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        onRetry = passViewModel::refresh,
        onRestorePurchases = passViewModel::restorePurchases,
    )

    if (showAccountGate) {
        PurchaseAccountGateDialog(
            productName = "Safe Browse Pass",
            phase = purchaseAccountGatePhase,
            authErrorMessage = authErrorMessage,
            pendingConflict = pendingAccountConflict,
            onLinkGoogle = onLinkGoogleForPurchase,
            onLinkFacebook = onLinkFacebookForPurchase,
            onLinkEmail = onLinkEmailForPurchase,
            onConfirmAccountSwitch = onConfirmAccountSwitchForPurchase,
            onDismissAccountSwitch = onDismissAccountSwitch,
            onDismissAuthError = onDismissAuthError,
            onDismiss = {
                standardPurchasePendingAfterAccountGate = false
                prepaidTopUpPendingAfterAccountGate = false
                showAccountGate = false
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun openSafeBrowsePassManagement(
    context: Context,
    uri: Uri,
): Boolean {
    if (
        uri.scheme != "https" ||
        uri.host != "play.google.com"
    ) {
        return false
    }

    val intent =
        Intent(
            Intent.ACTION_VIEW,
            uri,
        ).apply {
            if (context !is Activity) {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
        }

    return runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
