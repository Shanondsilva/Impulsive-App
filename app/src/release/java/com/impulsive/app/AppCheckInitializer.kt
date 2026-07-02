package com.impulsive.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal object AppCheckInitializer {
    fun install() {
        val appCheck = FirebaseAppCheck.getInstance()

        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )

        appCheck.setTokenAutoRefreshEnabled(true)
    }
}
