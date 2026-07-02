package com.impulsive.app

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckInitializer {
    private const val TAG = "ImpulsiveAppCheckProbe"

    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()

        appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance(),
        )

        appCheck.setTokenAutoRefreshEnabled(true)

        appCheck.getAppCheckToken(true)
            .addOnSuccessListener { token ->
                Log.d(
                    TAG,
                    "App Check token exchange SUCCESS. tokenLength=${token.token.length} expireTimeMillis=${token.expireTimeMillis}",
                )
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "App Check token exchange FAILED.", error)
            }
    }
}
