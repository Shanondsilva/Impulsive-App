package com.impulsive.app.backup

import android.content.Context
import android.os.Environment
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.impulsive.app.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Encrypted backup and restore of the Room database.
 *
 * WARNING: Room's WAL must be checkpointed and the DB connection closed before
 * copying the database file. The AppDatabase is closed here, then reinitialized
 * via forceOpen() — caller must restart the app (or at minimum re-inject a new DB
 * instance) after restore.
 *
 * Encryption: AES256_GCM_HKDF_4KB via AndroidX Security EncryptedFile.
 */
class BackupManager(private val context: Context) : KoinComponent {

    private val db: AppDatabase by inject()

    companion object {
        private const val DB_NAME = "impulsive.db"
    }

    private fun masterKey(): MasterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    /**
     * Exports an encrypted backup to the app's external files directory.
     * Returns the created backup [File].
     */
    suspend fun backup(): File = withContext(Dispatchers.IO) {
        // Checkpoint WAL then close
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        db.close()

        val srcFile = context.getDatabasePath(DB_NAME)
        val outDir = context.getExternalFilesDir(null) ?: context.filesDir
        val timestamp = System.currentTimeMillis()
        val destFile = File(outDir, "impulsive_backup_$timestamp.enc")

        // Remove old file if exists (EncryptedFile requires destination to not exist)
        if (destFile.exists()) destFile.delete()

        val encryptedFile = EncryptedFile.Builder(
            context,
            destFile,
            masterKey(),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { out ->
            srcFile.inputStream().use { it.copyTo(out) }
        }

        destFile
    }

    /**
     * Decrypts a backup file and overwrites the current Room database.
     * The app MUST be restarted (or re-initialized via Koin) after calling this.
     */
    suspend fun restore(backupFile: File) = withContext(Dispatchers.IO) {
        // Keep a safety copy before overwriting
        val destFile = context.getDatabasePath(DB_NAME)
        val safetyFile = File(destFile.parent, "$DB_NAME.pre_restore_backup")
        if (destFile.exists()) destFile.copyTo(safetyFile, overwrite = true)

        try {
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
            db.close()

            val encryptedFile = EncryptedFile.Builder(
                context,
                backupFile,
                masterKey(),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            encryptedFile.openFileInput().use { input ->
                destFile.outputStream().use { out ->
                    input.copyTo(out)
                }
            }

            // Clean up safety copy on success
            if (safetyFile.exists()) safetyFile.delete()
        } catch (e: Exception) {
            // Restore safety copy on failure
            if (safetyFile.exists()) safetyFile.copyTo(destFile, overwrite = true)
            throw e
        }
    }
}
