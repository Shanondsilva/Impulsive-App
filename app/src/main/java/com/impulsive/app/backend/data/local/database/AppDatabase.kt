package com.impulsive.app.backend.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.impulsive.app.backend.data.local.dao.BlockedDomainDao
import com.impulsive.app.backend.data.local.dao.FeedbackResponseDao
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.data.local.entity.FeedbackResponseEntity
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity

@Database(
    entities = [
        RecoverySessionEntity::class,
        JournalNoteEntity::class,
        JournalChecklistItemEntity::class,
        BlockedDomainEntity::class,
        FeedbackResponseEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recoverySessionDao(): RecoverySessionDao
    abstract fun journalNoteDao(): JournalNoteDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun feedbackResponseDao(): FeedbackResponseDao

    companion object {
        private const val DatabaseName = "impulsive.db"

        internal val Migration1To2 = object : Migration(1, 2) {
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

        internal val Migration2To3 = object : Migration(2, 3) {
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

        internal val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS blocked_domain (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        domain TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        addedByUser INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_domain_domain ON blocked_domain(domain)",
                )
            }
        }

        internal val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS feedback_responses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        promptDateEpochDay INTEGER NOT NULL,
                        questionIndex INTEGER NOT NULL,
                        questionText TEXT NOT NULL,
                        positiveAnswerText TEXT NOT NULL,
                        honestAnswerText TEXT NOT NULL,
                        selectedAnswerIndex INTEGER,
                        createdAtMillis INTEGER NOT NULL,
                        answeredAtMillis INTEGER,
                        expiresAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_feedback_responses_promptDateEpochDay
                    ON feedback_responses(promptDateEpochDay)
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_feedback_responses_expiresAtMillis
                    ON feedback_responses(expiresAtMillis)
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_feedback_responses_answeredAtMillis
                    ON feedback_responses(answeredAtMillis)
                    """.trimIndent(),
                )
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
                    .addMigrations(
                        Migration1To2,
                        Migration2To3,
                        Migration3To4,
                        Migration4To5,
                    )
                    .build()
                    .also { database ->
                        instance = database
                    }
            }
        }
    }
}
