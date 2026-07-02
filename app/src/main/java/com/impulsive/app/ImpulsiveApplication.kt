package com.impulsive.app

import android.app.Application
import com.google.firebase.FirebaseApp

class ImpulsiveApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        AppCheckInitializer.install()
    }
}
