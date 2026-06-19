package com.impulsive.app.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricGate {

    private const val Authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK

    private const val DeviceCredentialAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun isBiometricAvailable(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(Authenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onUsePin: () -> Unit,
        onError: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) onUsePin() else onError()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(Authenticators)
            .build()
        prompt.authenticate(info)
    }

    fun canAuthenticateDeviceCredential(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(DeviceCredentialAuthenticators) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun promptDeviceCredential(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(DeviceCredentialAuthenticators)
            .build()
        prompt.authenticate(info)
    }
}
