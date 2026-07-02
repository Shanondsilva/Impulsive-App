package com.impulsive.app.backend.data.local.database

import android.content.Context
import java.io.File
import java.io.IOException
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Converts an existing plaintext Room database to SQLCipher before Room opens it.
 *
 * This must run before Room.databaseBuilder(...).build() opens the database.
 */
internal object SqlCipherDatabaseMigrator {
    private val PlainSqliteHeader = byteArrayOf(
        0x53,
        0x51,
        0x4C,
        0x69,
        0x74,
        0x65,
        0x20,
        0x66,
        0x6F,
        0x72,
        0x6D,
        0x61,
        0x74,
        0x20,
        0x33,
        0x00,
    )

    @Volatile
    private var sqlCipherLoaded = false

    fun ensureSqlCipherLoaded() {
        if (sqlCipherLoaded) return

        synchronized(this) {
            if (!sqlCipherLoaded) {
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            }
        }
    }

    fun migratePlaintextDatabaseIfNeeded(
        context: Context,
        databaseName: String,
        passphrase: ByteArray,
    ) {
        val databaseFile = context.getDatabasePath(databaseName)

        if (!databaseFile.exists()) {
            return
        }

        if (databaseFile.length() == 0L) {
            databaseFile.delete()
            deleteSidecars(databaseFile)
            return
        }

        if (!hasPlainSqliteHeader(databaseFile)) {
            return
        }

        ensureSqlCipherLoaded()

        val parentDirectory = databaseFile.parentFile
            ?: throw IOException("Database directory is not available.")

        val encryptedFile = File(parentDirectory, "$databaseName.encrypted")
        val backupFile = File(parentDirectory, "$databaseName.plaintext-backup")

        encryptedFile.delete()
        backupFile.delete()
        deleteSidecars(encryptedFile)
        deleteSidecars(backupFile)

        exportPlaintextDatabase(
            sourceFile = databaseFile,
            encryptedFile = encryptedFile,
            passphrase = passphrase.toString(Charsets.UTF_8),
        )

        if (!databaseFile.renameTo(backupFile)) {
            encryptedFile.delete()
            deleteSidecars(encryptedFile)
            throw IOException("Could not back up plaintext Room database.")
        }

        deleteSidecars(databaseFile)

        if (!encryptedFile.renameTo(databaseFile)) {
            backupFile.renameTo(databaseFile)
            encryptedFile.delete()
            deleteSidecars(encryptedFile)
            throw IOException("Could not replace plaintext Room database with encrypted database.")
        }

        backupFile.delete()
        deleteSidecars(backupFile)
        deleteSidecars(encryptedFile)
    }

    private fun exportPlaintextDatabase(
        sourceFile: File,
        encryptedFile: File,
        passphrase: String,
    ) {
        val database = SQLiteDatabase.openOrCreateDatabase(
            sourceFile.absolutePath,
            "",
            null,
            null,
            null,
        )

        try {
            database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                cursor.moveToFirst()
            }

            val escapedEncryptedPath = encryptedFile.absolutePath.replace("'", "''")
            database.execSQL(
                "ATTACH DATABASE '$escapedEncryptedPath' AS encrypted KEY ?",
                arrayOf<Any>(passphrase),
            )

            database.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { cursor ->
                cursor.moveToFirst()
            }

            database.execSQL("DETACH DATABASE encrypted")
        } finally {
            database.close()
        }
    }

    private fun hasPlainSqliteHeader(file: File): Boolean {
        if (file.length() < PlainSqliteHeader.size) {
            return false
        }

        val header = ByteArray(PlainSqliteHeader.size)
        file.inputStream().use { input ->
            val bytesRead = input.read(header)
            if (bytesRead != PlainSqliteHeader.size) {
                return false
            }
        }

        return header.contentEquals(PlainSqliteHeader)
    }

    private fun deleteSidecars(databaseFile: File) {
        File("${databaseFile.absolutePath}-wal").delete()
        File("${databaseFile.absolutePath}-shm").delete()
        File("${databaseFile.absolutePath}-journal").delete()
    }
}
