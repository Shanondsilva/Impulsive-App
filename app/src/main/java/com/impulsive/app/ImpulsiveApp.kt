package com.impulsive.app

import android.app.Application
import com.impulsive.app.di.appModule
import com.impulsive.app.worker.WeeklyCheckInWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ImpulsiveApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@ImpulsiveApp)
            modules(appModule)
        }

        // Register notification channel (safe to call repeatedly)
        WeeklyCheckInWorker.createNotificationChannel(this)

        // Schedule Sunday 8PM check-in reminder (KEEP policy — won't duplicate)
        WeeklyCheckInWorker.schedule(this)
    }
}
