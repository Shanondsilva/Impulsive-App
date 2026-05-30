package com.impulsive.app.backend.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity

@Database(
    entities = [
        RecoverySessionEntity::class,
        JournalNoteEntity::class,
        JournalChecklistItemEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recoverySessionDao(): RecoverySessionDao
    abstract fun journalNoteDao(): JournalNoteDao

    companion object {
        private const val DatabaseName = "impulsive.db"

        private val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        checklist TEXT NOT NULL,
                        sketch TEXT NOT NULL,
                        reminderAtMillis INTEGER,
                        source TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE journal_notes ADD COLUMN category TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal_notes ADD COLUMN highlightColor TEXT")
                db.execSQL("ALTER TABLE journal_notes ADD COLUMN sortOrder INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS journal_checklist_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        noteId INTEGER NOT NULL,
                        text TEXT NOT NULL,
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        FOREIGN KEY(noteId) REFERENCES journal_notes(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_checklist_items_noteId ON journal_checklist_items(noteId)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DatabaseName,
                )
                    .addMigrations(Migration1To2, Migration2To3)
                    .build()
                    .also { database ->
                        instance = database
                    }
            }
        }
    }
}
