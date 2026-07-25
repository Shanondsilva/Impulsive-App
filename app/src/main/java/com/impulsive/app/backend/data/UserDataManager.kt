package com.impulsive.app.backend.data

import android.app.backup.BackupManager
import android.content.Context
import android.content.Intent
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.restore.RestoreBundleWriter
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryLocalKeyStore
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryLocalMetadataStore
import com.impulsive.app.backend.data.restore.cloud.CloudRecoveryUploadScheduler
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserDataManager(
    context: Context,
) {
    /*
     * Always retain only the application context, while preserving the local
     * property name required by the existing deletion-order source tests.
     */
    private val context = context.applicationContext

    /**
     * Permanently removes locally held Impulsive user data.
     *
     * This intentionally does not clear Firebase Authentication or provider
     * credential state. Full account deletion signs out separately before
     * invoking this operation. The mismatched-account reset invokes this
     * operation directly so the newly authenticated account survives the
     * cold restart.
     *
     * BackupManager.dataChanged() requests a future backup update. Security
     * does not depend on the old cloud snapshot disappearing immediately.
     * Any snapshot restored again remains protected by Firebase UID ownership
     * validation and the confirmed erase flow.
     */
    suspend fun deleteAllData() =
        withContext(Dispatchers.IO) {
            /*
             * Stop work that could rewrite or upload old local state while
             * deletion is running.
             */
            RestoreSnapshotRefreshScheduler.cancelAndAwait(context)
            CloudRecoveryUploadScheduler.cancelAndAwait(context)

            /*
             * Clear local recovery credentials and metadata only. This does
             * not access or delete a remote Google Drive backup.
             */
            CloudRecoveryLocalKeyStore(context).clearPermanently()
            CloudRecoveryLocalMetadataStore(context).clear()

            /*
             * Never hide a Room failure. The operation must not report success
             * or restart after database deletion has failed.
             */
            AppDatabase.getInstance(context).clearAllTables()

            /*
             * DataStore objects can retain in-memory values. Remove their
             * complete backing directory and cold-restart after the operation.
             */
            val dataStoreDir =
                File(
                    context.filesDir,
                    DataStoreDirectoryName,
                )

            if (dataStoreDir.exists()) {
                val deleted = dataStoreDir.deleteRecursively()

                if (!deleted || dataStoreDir.exists()) {
                    throw IOException(
                        "Failed to remove DataStore data during permanent deletion.",
                    )
                }
            }

            /*
             * Remove the dedicated automatic restore directory, including
             * both the completed restore envelope and any temporary file.
             */
            val restoreDir =
                File(
                    context.filesDir,
                    RestoreBundleWriter.DirectoryName,
                )

            if (restoreDir.exists()) {
                val deleted = restoreDir.deleteRecursively()

                if (!deleted || restoreDir.exists()) {
                    throw IOException(
                        "Failed to remove automatic restore data during permanent deletion.",
                    )
                }
            }

            /*
             * Clear only explicit Impulsive runtime SharedPreferences.
             *
             * Never delete the complete shared_prefs directory because
             * Firebase Authentication and identity-provider persistence must
             * survive the mismatched-account reset.
             */
            LocalStateSharedPreferences.forEach { preferencesName ->
                clearSharedPreferencesOrThrow(preferencesName)
            }

            /*
             * Request replacement of the Android backup only after all local
             * deletion operations have completed successfully.
             */
            BackupManager(context.applicationContext).dataChanged()
        }

    private fun clearSharedPreferencesOrThrow(
        preferencesName: String,
    ) {
        val committed =
            context
                .getSharedPreferences(
                    preferencesName,
                    Context.MODE_PRIVATE,
                )
                .edit()
                .clear()
                .commit()

        if (!committed) {
            throw IOException(
                "Failed to clear $preferencesName during permanent deletion.",
            )
        }
    }

    /**
     * Relaunches Impulsive from a cold start so Room, DataStore, navigation
     * and ViewModel memory cannot retain the previous profile.
     */
    fun restartApp() {
        val launch =
            context
                .packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    )
                }

        if (launch == null) {
            throw IllegalStateException(
                "Could not create the Impulsive restart intent.",
            )
        }

        context.startActivity(launch)
        Runtime.getRuntime().exit(0)
    }

    private companion object {
        const val DataStoreDirectoryName = "datastore"

        val LocalStateSharedPreferences =
            listOf(
                "website_protection_incidents_v3",
                "vpn_diagnostics",
                "interruption_message_selector",
            )
    }
}
