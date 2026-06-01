package com.impulsive.app.backend.data

import android.content.Context
import android.content.Intent
import com.impulsive.app.backend.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class UserDataManager(private val context: Context) {

    /** Wipes the Room database rows and every DataStore preference file. */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        runCatching { AppDatabase.getInstance(context).clearAllTables() }
        // DataStore caches in memory, so delete the files and rely on a process
        // restart (see restartApp) to drop the in-memory copies.
        val dataStoreDir = File(context.filesDir, "datastore")
        if (dataStoreDir.exists()) dataStoreDir.deleteRecursively()
    }

    /** Relaunches the app from a cold start, discarding all in-memory state. */
    fun restartApp() {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
        if (launch != null) context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }
}
