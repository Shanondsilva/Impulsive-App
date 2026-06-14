package com.impulsive.app.frontend.screens.onboarding

import android.app.Activity
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.impulsive.app.R
import com.impulsive.app.backend.domain.model.auth.AuthProvider
import com.impulsive.app.backend.session.auth.AuthState
import com.impulsive.app.backend.session.auth.AuthViewModel
import com.impulsive.app.frontend.utils.rememberImpulsiveHaptics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Sign-in screen with Google, Facebook, and Continue-as-guest options.
 *
 * UI-only: all auth logic lives in [AuthViewModel]. The screen needs an
 * [Activity] reference (via [LocalContext]) because the underlying provider
 * SDKs must attach to a host Activity.
 */
@Composable
fun LoginSignupGuestScreen(
    onContinue: () -> Unit = {},
    onAuthenticated: () -> Unit = onContinue,
    authViewModel: AuthViewModel,
) {
    val state by authViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity()
    var localMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.user) {
        if (state.user != null) {
            onAuthenticated()
        }
    }

    LoginContent(
        state = state,
        message = state.errorMessage ?: localMessage,
        onSignInWithGoogle = {
            localMessage = null
            activity?.let(authViewModel::signInWithGoogle)
        },
        onSignInWithFacebook = {
            localMessage = null
            activity?.let(authViewModel::signInWithFacebook)
        },
        onContinueAsGuest = {
            localMessage = null
            authViewModel.continueAsGuest()
        },
        onDismissError = {
            localMessage = null
            authViewModel.consumeError()
        },
        onCreateAccount = {
            localMessage = null
        },
        onLogIn = {
            localMessage = null
        },
        onCreateAccountWithEmail = { email, password ->
            localMessage = null
            authViewModel.createAccountWithEmail(email, password)
        },
        onSignInWithEmail = { email, password ->
            localMessage = null
            authViewModel.signInWithEmail(email, password)
        },
        onRefreshEmailVerification = {
            localMessage = null
            authViewModel.refreshEmailVerification()
        },
    )
}

@Composable
private fun LoginContent(
    state: AuthState,
    message: String?,
    onSignInWithGoogle: () -> Unit,
    onSignInWithFacebook: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onDismissError: () -> Unit,
    onCreateAccount: () -> Unit,
    onLogIn: () -> Unit,
    onCreateAccountWithEmail: (String, String) -> Unit,
    onSignInWithEmail: (String, String) -> Unit,
    onRefreshEmailVerification: () -> Unit,
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
    val titleAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val titleScale = remember { Animatable(if (reducedMotion) 1f else 0.94f) }
    val actionsAlpha = remember { Animatable(if (reducedMotion) 1f else 0.78f) }
    var subtitleVisible by remember { mutableStateOf(reducedMotion) }
    var emailFormMode by remember { mutableStateOf<EmailFormMode?>(null) }
    var emailText by remember { mutableStateOf("") }
    var passwordText by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val isGuestLoading = state.inFlightProvider == AuthProvider.Guest

    if (state.isWaitingForEmailVerification) {
        EmailVerificationWaitingView(
            email = state.pendingEmailVerificationAddress,
            loading = state.inFlightProvider == AuthProvider.Email,
            message = message,
            onDismissError = onDismissError,
            onRefresh = onRefreshEmailVerification,
        )
        return
    }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            titleAlpha.snapTo(1f)
            titleScale.snapTo(1f)
            actionsAlpha.snapTo(1f)
            subtitleVisible = true
            return@LaunchedEffect
        }

        coroutineScope {
            launch {
                titleAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
                )
            }
            launch {
                titleScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
                )
            }
        }
        subtitleVisible = true
        actionsAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        )
    }

    val glowScale: Float
    val glowAlpha: Float
    if (reducedMotion) {
        glowScale = 1f
        glowAlpha = 0.18f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "welcome-glow")
        glowScale = infiniteTransition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "welcome-glow-scale",
        ).value
        glowAlpha = infiniteTransition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "welcome-glow-alpha",
        ).value
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val ultraCompactHeight = maxHeight < 620.dp
            val veryCompactHeight = maxHeight < 700.dp
            val compactHeight = maxHeight < 790.dp
            val horizontalPadding = if (maxWidth < 380.dp) 20.dp else 24.dp
            val topPadding = when {
                ultraCompactHeight -> 10.dp
                veryCompactHeight -> 14.dp
                compactHeight -> 28.dp
                else -> 44.dp
            }
            val brandHeight = when {
                ultraCompactHeight -> 76.dp
                veryCompactHeight -> 90.dp
                compactHeight -> 112.dp
                else -> 128.dp
            }
            val glowSize = when {
                ultraCompactHeight -> 122.dp
                veryCompactHeight -> 140.dp
                compactHeight -> 164.dp
                else -> 184.dp
            }
            val cardTopGap = when {
                ultraCompactHeight -> 10.dp
                veryCompactHeight -> 12.dp
                compactHeight -> 18.dp
                else -> 24.dp
            }
            val cardButtonGap = when {
                ultraCompactHeight -> 16.dp
                veryCompactHeight -> 18.dp
                compactHeight -> 22.dp
                else -> 28.dp
            }
            val buttonGap = when {
                ultraCompactHeight -> 8.dp
                veryCompactHeight -> 9.dp
                else -> 14.dp
            }
            val buttonHeight = when {
                ultraCompactHeight -> 46.dp
                veryCompactHeight -> 48.dp
                compactHeight -> 52.dp
                else -> 56.dp
            }
            val dividerPadding = when {
                ultraCompactHeight -> 6.dp
                veryCompactHeight -> 8.dp
                compactHeight -> 12.dp
                else -> 18.dp
            }
            val guestTopGap = when {
                ultraCompactHeight -> 8.dp
                veryCompactHeight -> 10.dp
                compactHeight -> 16.dp
                else -> 24.dp
            }
            val bottomPadding = when {
                ultraCompactHeight -> 12.dp
                veryCompactHeight -> 14.dp
                else -> 24.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding)
                    .padding(top = topPadding, bottom = bottomPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                WelcomeBrandHeader(
                    titleAlpha = titleAlpha.value,
                    titleScale = titleScale.value,
                    glowAlpha = glowAlpha,
                    glowScale = glowScale,
                    brandHeight = brandHeight,
                    glowSize = glowSize,
                    compact = compactHeight,
                    veryCompact = veryCompactHeight,
                    ultraCompact = ultraCompactHeight,
                )

                Spacer(modifier = Modifier.height(cardTopGap))

                WelcomeMessageCard(
                    visible = subtitleVisible,
                    compact = compactHeight,
                    veryCompact = veryCompactHeight,
                    ultraCompact = ultraCompactHeight,
                )

                Spacer(modifier = Modifier.height(cardButtonGap))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { alpha = actionsAlpha.value },
                ) {
                    LoginPrimaryButton(
                        text = "Create account",
                        enabled = !state.isLoading,
                        height = buttonHeight,
                        onClick = {
                            validationMessage = null
                            emailFormMode = EmailFormMode.CreateAccount
                            onCreateAccount()
                        },
                    )

                    Spacer(modifier = Modifier.height(buttonGap))

                    LoginOutlinedButton(
                        text = "Log in",
                        enabled = !state.isLoading,
                        height = buttonHeight,
                        onClick = {
                            validationMessage = null
                            emailFormMode = EmailFormMode.LogIn
                            onLogIn()
                        },
                    )

                    EmailAuthForm(
                        mode = emailFormMode,
                        email = emailText,
                        password = passwordText,
                        validationMessage = validationMessage,
                        loading = state.inFlightProvider == AuthProvider.Email,
                        enabled = !state.isLoading || state.inFlightProvider == AuthProvider.Email,
                        onEmailChange = {
                            emailText = it
                            validationMessage = null
                        },
                        onPasswordChange = {
                            passwordText = it
                            validationMessage = null
                        },
                        onSubmit = {
                            when {
                                emailText.isBlank() || !emailText.contains("@") -> {
                                    validationMessage = "Enter a valid email address."
                                }
                                passwordText.length < 6 -> {
                                    validationMessage = "Password must be at least 6 characters."
                                }
                                emailFormMode == EmailFormMode.CreateAccount -> {
                                    validationMessage = null
                                    onCreateAccountWithEmail(emailText, passwordText)
                                }
                                emailFormMode == EmailFormMode.LogIn -> {
                                    validationMessage = null
                                    onSignInWithEmail(emailText, passwordText)
                                }
                            }
                        },
                        onCancel = {
                            emailFormMode = null
                            validationMessage = null
                            passwordText = ""
                        },
                    )

                    LoginDivider(verticalPadding = dividerPadding)

                    LoginNeutralButton(
                        text = "Continue with Google",
                        icon = {
                            ProviderIcon(
                                resId = R.drawable.ic_google,
                                contentDescription = "Google",
                            )
                        },
                        enabled = !state.isLoading,
                        loading = state.inFlightProvider == AuthProvider.Google,
                        height = buttonHeight,
                        onClick = onSignInWithGoogle,
                    )

                    Spacer(modifier = Modifier.height(buttonGap))

                    LoginNeutralButton(
                        text = "Continue with Facebook",
                        icon = {
                            ProviderIcon(
                                resId = R.drawable.ic_facebook,
                                contentDescription = "Facebook",
                            )
                        },
                        enabled = !state.isLoading,
                        loading = state.inFlightProvider == AuthProvider.Facebook,
                        height = buttonHeight,
                        onClick = onSignInWithFacebook,
                    )
                }

                if (message != null) {
                    Spacer(modifier = Modifier.height(if (veryCompactHeight) 10.dp else 14.dp))
                    ErrorBanner(message = message, onDismiss = onDismissError)
                }

                Spacer(modifier = Modifier.height(guestTopGap))

                TextButton(
                    enabled = !state.isLoading,
                    onClick = onContinueAsGuest,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF48454E),
                        disabledContentColor = Color(0xFF635880),
                    ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isGuestLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF6F5A9A),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Continuing as guest...",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                text = "Continue as guest",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class EmailFormMode {
    CreateAccount,
    LogIn,
}

@Composable
private fun EmailVerificationWaitingView(
    email: String?,
    loading: Boolean,
    message: String?,
    onDismissError: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Check your email",
                color = Color(0xFF211C33),
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (email.isNullOrBlank()) {
                    "We sent a verification link. Open it, then return to Impulsive."
                } else {
                    "We sent a verification link to $email. Open it, then return to Impulsive."
                },
                color = Color(0xFF5F5A68),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6F5A9A),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE7DDF8),
                    disabledContentColor = Color(0xFF6F5A9A),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF6F5A9A),
                    )
                } else {
                    Text(
                        text = "I've verified my email",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (message != null) {
                Spacer(modifier = Modifier.height(14.dp))
                ErrorBanner(message = message, onDismiss = onDismissError)
            }
        }
    }
}

@Composable
private fun EmailAuthForm(
    mode: EmailFormMode?,
    email: String,
    password: String,
    validationMessage: String?,
    loading: Boolean,
    enabled: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    if (mode == null) return
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        disabledTextColor = Color.Black,
        cursorColor = Color(0xFF6F5A9A),
        focusedBorderColor = Color(0xFF6F5A9A),
        unfocusedBorderColor = Color(0xFFD0C3F1),
        disabledBorderColor = Color(0xFFD0C3F1),
        focusedLabelColor = Color(0xFF6F5A9A),
        unfocusedLabelColor = Color(0xFF635880),
        disabledLabelColor = Color(0xFF635880),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            enabled = enabled,
            singleLine = true,
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            singleLine = true,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        if (validationMessage != null) {
            Text(
                text = validationMessage,
                color = Color(0xFF8E1A1A),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6F5A9A),
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(
                    text = if (mode == EmailFormMode.CreateAccount) {
                        "Send verification email"
                    } else {
                        "Log in with email"
                    },
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = enabled,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = "Cancel",
                color = Color(0xFF635880),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun WelcomeBrandHeader(
    titleAlpha: Float,
    titleScale: Float,
    glowAlpha: Float,
    glowScale: Float,
    brandHeight: Dp,
    glowSize: Dp,
    compact: Boolean,
    veryCompact: Boolean,
    ultraCompact: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(brandHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(glowSize)
                    .graphicsLayer {
                        scaleX = glowScale
                        scaleY = glowScale
                        alpha = glowAlpha
                    }
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFD0C3F1),
                                Color(0xFFD0C3F1).copy(alpha = 0.42f),
                                Color.Transparent,
                            ),
                        ),
                        shape = RoundedCornerShape(94.dp),
                    ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    scaleX = titleScale
                    scaleY = titleScale
                },
            ) {
                Text(
                    text = "Impulsive",
                    color = Color(0xFF211C33),
                    fontSize = when {
                        ultraCompact -> 32.sp
                        veryCompact -> 34.sp
                        compact -> 38.sp
                        else -> 42.sp
                    },
                    lineHeight = when {
                        ultraCompact -> 36.sp
                        veryCompact -> 40.sp
                        compact -> 44.sp
                        else -> 48.sp
                    },
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(if (veryCompact) 6.dp else 10.dp))
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 3.dp)
                        .background(Color(0xFFD0C3F1), RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

@Composable
private fun WelcomeMessageCard(
    visible: Boolean,
    compact: Boolean,
    veryCompact: Boolean,
    ultraCompact: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 520,
                delayMillis = 80,
                easing = FastOutSlowInEasing,
            ),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F4FF), RoundedCornerShape(24.dp))
                .padding(
                    horizontal = if (ultraCompact) 18.dp else 22.dp,
                    vertical = if (veryCompact) 18.dp else 22.dp,
                ),
        ) {
            Text(
                text = "Pause the impulse. Choose your next move.",
                color = Color(0xFF2F2A3F),
                fontSize = if (veryCompact) 15.sp else if (compact) 16.sp else 17.sp,
                lineHeight = if (veryCompact) 20.sp else if (compact) 22.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(if (veryCompact) 8.dp else 10.dp))

            Text(
                text = "Create an account to save your progress, or continue privately as a guest.",
                color = Color(0xFF5F5A68),
                fontSize = if (ultraCompact) 13.sp else 14.sp,
                lineHeight = if (veryCompact) 18.sp else if (compact) 19.sp else 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoginPrimaryButton(
    text: String,
    enabled: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6F5A9A),
            contentColor = Color(0xFFFFFFFF),
            disabledContainerColor = Color(0xFFE7DDF8),
            disabledContentColor = Color(0xFF6F5A9A),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoginOutlinedButton(
    text: String,
    enabled: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    Button(
        onClick = {
            haptics.light()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .border(
                width = 2.dp,
                color = Color(0xFFD0C3F1),
                shape = RoundedCornerShape(28.dp),
            ),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color(0xFF635880),
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color(0xFF8B7BA8),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoginNeutralButton(
    text: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    loading: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val haptics = rememberImpulsiveHaptics(enabled = true)
    Button(
        onClick = {
            haptics.light()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF1ECF0),
            contentColor = Color(0xFF1C1B1E),
            disabledContainerColor = Color(0xFFF1ECF0),
            disabledContentColor = Color(0xFF6A6370),
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF1C1B1E),
                )
            } else {
                icon()
            }
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoginDivider(verticalPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(Color(0xFFE6E1E5)),
        )
        Text(
            text = "or",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = Color(0xFF79757E),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .height(1.dp)
                .weight(1f)
                .background(Color(0xFFE6E1E5)),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDECEC), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = Color(0xFF8E1A1A),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(text = "Dismiss", color = Color(0xFF8E1A1A), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProviderIcon(resId: Int, contentDescription: String) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = Modifier.size(20.dp),
    )
}

/**
 * Walk the ContextWrapper chain to find the host Activity. Necessary because
 * Compose's LocalContext returns the closest Context which may be a wrapper.
 */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
