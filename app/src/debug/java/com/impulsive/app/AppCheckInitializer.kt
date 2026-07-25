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

        appCheck.getAppCheckToken(false)
            .addOnSuccessListener {
                Log.d(TAG, "App Check token exchange SUCCESS.")
            }
            .addOnFailureListener { error ->
                Log.e(
                    TAG,
                    "App Check token exchange FAILED " +
                        "(exception=${error.javaClass.simpleName}).",
                )
            }
    }
}
