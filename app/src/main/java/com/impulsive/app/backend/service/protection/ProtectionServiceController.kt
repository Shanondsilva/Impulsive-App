package com.impulsive.app.backend.service.protection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object ProtectionServiceController {
    fun start(context: Context) {
        val intent = Intent(context, AppMonitorService::class.java).apply {
            action = AppMonitorService.ActionStart
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, AppMonitorService::class.java).apply {
            action = AppMonitorService.ActionStop
        }
        context.startService(intent)
    }
}
