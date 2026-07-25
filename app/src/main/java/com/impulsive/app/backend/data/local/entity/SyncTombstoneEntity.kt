package com.impulsive.app.backend.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local deletion marker used by backup/restore merging.
 *
 * Cloud sync was removed, but tombstones are still required: JournalNoteDao
 * writes one whenever a journal note or checklist item is deleted, and the
 * restore-bundle pipeline (RestoreBundleWriter / RestoreBundleImporter /
 * ManualBackupManager / UserDataExporter) uses them so that restoring an
 * older backup does not resurrect entries the user has since deleted.
 * Do not remove without reworking restore merging.
 */
@Entity(
    tableName = "sync_tombstones",
    indices = [
        Index(
            value = ["recordType", "parentKey", "recordKey"],
            unique = true,
        ),
    ],
)
data class SyncTombstoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordType: String,
    @ColumnInfo(defaultValue = "''")
    val parentKey: String = "",
    val recordKey: String,
    val deletedAtMillis: Long,
) {
    companion object {
        const val TYPE_JOURNAL_NOTE = "journal_note"
        const val TYPE_CHECKLIST_ITEM = "checklist_item"
        const val TYPE_RECOVERY_SESSION = "recovery_session"

        fun journalNote(recordKey: String, deletedAtMillis: Long): SyncTombstoneEntity =
            SyncTombstoneEntity(
                recordType = TYPE_JOURNAL_NOTE,
                parentKey = "",
                recordKey = recordKey,
                deletedAtMillis = deletedAtMillis,
            )

        fun checklistItem(
            parentKey: String,
            recordKey: String,
            deletedAtMillis: Long,
        ): SyncTombstoneEntity =
            SyncTombstoneEntity(
                recordType = TYPE_CHECKLIST_ITEM,
                parentKey = parentKey,
                recordKey = recordKey,
                deletedAtMillis = deletedAtMillis,
            )

        fun recoverySession(recordKey: String, deletedAtMillis: Long): SyncTombstoneEntity =
            SyncTombstoneEntity(
                recordType = TYPE_RECOVERY_SESSION,
                parentKey = "",
                recordKey = recordKey,
                deletedAtMillis = deletedAtMillis,
            )
    }
}
