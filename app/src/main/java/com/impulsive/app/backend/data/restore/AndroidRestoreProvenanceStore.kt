package com.impulsive.app.backend.data.restore

import android.content.Context
import java.io.File
import java.io.IOException

class AndroidRestoreProvenanceStore(
    context: Context,
) {
    private val marker = File(context.noBackupFilesDir, MarkerFileName)

    fun markRestorePending() {
        val parent = marker.parentFile ?: throw IOException("Restore marker has no parent directory.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Could not create restore marker directory.")
        }
        marker.outputStream().use { stream -> stream.write(MarkerContents) }
        if (!marker.isFile) throw IOException("Restore marker was not created.")
    }

    fun isRestorePending(): Boolean = marker.isFile

    fun clearRestorePending() {
        if (marker.exists() && !marker.delete()) {
            throw IOException("Could not clear restore marker.")
        }
    }

    private companion object {
        const val MarkerFileName = "android_restore_pending"
        val MarkerContents = byteArrayOf(1)
    }
}