package com.impulsive.app.backend.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.impulsive.app.backend.data.local.dao.AdaptiveDecisionDao
import com.impulsive.app.backend.data.local.dao.AdaptivePreferenceDao
import com.impulsive.app.backend.data.local.dao.BlockedDomainDao
import com.impulsive.app.backend.data.local.dao.CloudRestoreReceiptDao
import com.impulsive.app.backend.data.local.dao.FeedbackResponseDao
import com.impulsive.app.backend.data.local.dao.JournalNoteDao
import com.impulsive.app.backend.data.local.dao.MomentPlanDao
import com.impulsive.app.backend.data.local.dao.MomentPlanRehearsalDao
import com.impulsive.app.backend.data.local.dao.PathShiftCycleDao
import com.impulsive.app.backend.data.local.dao.ProtectionCoachSuggestionDao
import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.dao.SafeExitDao
import com.impulsive.app.backend.data.local.dao.SyncTombstoneDao
import com.impulsive.app.backend.data.local.entity.AdaptiveDecisionEntity
import com.impulsive.app.backend.data.local.entity.AdaptivePreferenceEntity
import com.impulsive.app.backend.data.local.entity.BlockedDomainEntity
import com.impulsive.app.backend.data.local.entity.CloudRestoreReceiptEntity
import com.impulsive.app.backend.data.local.entity.FeedbackResponseEntity
import com.impulsive.app.backend.data.local.entity.JournalChecklistItemEntity
import com.impulsive.app.backend.data.local.entity.JournalNoteEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanEntity
import com.impulsive.app.backend.data.local.entity.MomentPlanRehearsalEntity
import com.impulsive.app.backend.data.local.entity.PathShiftCycleEntity
import com.impulsive.app.backend.data.local.entity.ProtectionCoachSuggestionEntity
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.data.local.entity.SyncTombstoneEntity
import com.impulsive.app.backend.domain.engine.adaptive.LegacyMomentPlanContentRevisionFactory
import com.impulsive.app.security.storage.DatabasePassphraseStore
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        RecoverySessionEntity::class,
        JournalNoteEntity::class,
        JournalChecklistItemEntity::class,
        BlockedDomainEntity::class,
        FeedbackResponseEntity::class,
        SyncTombstoneEntity::class,
        CloudRestoreReceiptEntity::class,
        AdaptiveDecisionEntity::class,
        MomentPlanEntity::class,
        AdaptivePreferenceEntity::class,
        MomentPlanRehearsalEntity::class,
        PathShiftCycleEntity::class,
        ProtectionCoachSuggestionEntity::class,
        SafeExitEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recoverySessionDao(): RecoverySessionDao
    abstract fun journalNoteDao(): JournalNoteDao
    abstract fun blockedDomainDao(): BlockedDomainDao
    abstract fun feedbackResponseDao(): FeedbackResponseDao
    abstract fun syncTombstoneDao(): SyncTombstoneDao
    abstract fun cloudRestoreReceiptDao(): CloudRestoreReceiptDao
    abstract fun adaptiveDecisionDao(): AdaptiveDecisionDao
    abstract fun momentPlanDao(): MomentPlanDao
    abstract fun adaptivePreferenceDao(): AdaptivePreferenceDao
    abstract fun momentPlanRehearsalDao(): MomentPlanRehearsalDao
    abstract fun pathShiftCycleDao(): PathShiftCycleDao
    abstract fun protectionCoachSuggestionDao(): ProtectionCoachSuggestionDao
    abstract fun safeExitDao(): SafeExitDao

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

        internal val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_tombstones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordType TEXT NOT NULL,
                        parentKey TEXT NOT NULL DEFAULT '',
                        recordKey TEXT NOT NULL,
                        deletedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_sync_tombstones_recordType_parentKey_recordKey
                    ON sync_tombstones(recordType, parentKey, recordKey)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS cloud_restore_receipts (
                        receiptId TEXT NOT NULL PRIMARY KEY,
                        payloadSha256 TEXT NOT NULL,
                        proofType TEXT NOT NULL,
                        previousUid TEXT,
                        previousGoogleSubjectHash TEXT,
                        currentUid TEXT NOT NULL,
                        currentGoogleSubjectHash TEXT,
                        importedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        internal val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS adaptive_decisions (
                        decisionId TEXT NOT NULL,
                        protectionIncidentToken TEXT NOT NULL,
                        sourceKind TEXT NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        momentWindowStartedAtMillis INTEGER NOT NULL,
                        momentIntensity TEXT NOT NULL,
                        momentCue TEXT,
                        baselineUrgeRating INTEGER,
                        assignmentMode TEXT NOT NULL,
                        eligibleInterventionsMask INTEGER NOT NULL,
                        assignedSuggestion TEXT,
                        actualIntervention TEXT,
                        selectionProbability REAL,
                        reasonCode TEXT NOT NULL,
                        momentPlanId TEXT,
                        userOverrodeSuggestion INTEGER NOT NULL,
                        presentedAtMillis INTEGER,
                        startedAtMillis INTEGER,
                        completedAtMillis INTEGER,
                        dismissedAtMillis INTEGER,
                        feedbackCode TEXT NOT NULL,
                        feedbackUpdatedAtMillis INTEGER,
                        repeatDetectedWithin20Minutes INTEGER,
                        firstRepeatAtMillis INTEGER,
                        observationDeadlineAtMillis INTEGER NOT NULL,
                        observationFinalisedAtMillis INTEGER,
                        PRIMARY KEY(decisionId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS moment_plans (
                        planId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        momentCue TEXT,
                        actionText TEXT NOT NULL,
                        futureCueText TEXT NOT NULL,
                        actionType TEXT NOT NULL,
                        actionTarget TEXT,
                        enabled INTEGER NOT NULL,
                        preferredForCue INTEGER NOT NULL,
                        createdAtMillis INTEGER NOT NULL,
                        updatedAtMillis INTEGER NOT NULL,
                        rehearsedAtMillis INTEGER,
                        PRIMARY KEY(planId)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS adaptive_preferences (
                        id INTEGER NOT NULL,
                        personalSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                        gameSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                        readingSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                        momentPlanSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                        randomisedExplorationEnabled INTEGER NOT NULL DEFAULT 1,
                        updatedAtMillis INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO adaptive_preferences (
                        id,
                        personalSuggestionsEnabled,
                        gameSuggestionsEnabled,
                        readingSuggestionsEnabled,
                        momentPlanSuggestionsEnabled,
                        randomisedExplorationEnabled,
                        updatedAtMillis
                    )
                    VALUES (1, 1, 1, 1, 1, 1, 0)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_adaptive_decisions_protectionIncidentToken
                    ON adaptive_decisions(protectionIncidentToken)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_createdAtMillis
                    ON adaptive_decisions(createdAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_observationFinalisedAtMillis_observationDeadlineAtMillis
                    ON adaptive_decisions(
                        observationFinalisedAtMillis,
                        observationDeadlineAtMillis
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_actualIntervention_observationFinalisedAtMillis_createdAtMillis
                    ON adaptive_decisions(
                        actualIntervention,
                        observationFinalisedAtMillis,
                        createdAtMillis
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_momentCue_observationFinalisedAtMillis_createdAtMillis
                    ON adaptive_decisions(
                        momentCue,
                        observationFinalisedAtMillis,
                        createdAtMillis
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_momentPlanId
                    ON adaptive_decisions(momentPlanId)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plans_enabled_updatedAtMillis
                    ON moment_plans(enabled, updatedAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plans_momentCue_enabled_preferredForCue
                    ON moment_plans(momentCue, enabled, preferredForCue)
                    """.trimIndent(),
                )
            }
        }

        internal val Migration8To9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    ALTER TABLE adaptive_decisions
                    ADD COLUMN momentPlanUpdatedAtMillis INTEGER
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS moment_plan_rehearsals (
                        rehearsalId TEXT NOT NULL,
                        planId TEXT NOT NULL,
                        planUpdatedAtMillisAtStart INTEGER NOT NULL,
                        mode TEXT NOT NULL,
                        startedAtMillis INTEGER NOT NULL,
                        completedAtMillis INTEGER,
                        dismissedAtMillis INTEGER,
                        PRIMARY KEY(rehearsalId),
                        CHECK(
                            completedAtMillis IS NULL
                            OR dismissedAtMillis IS NULL
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plan_rehearsals_planId_startedAtMillis
                    ON moment_plan_rehearsals(planId, startedAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plan_rehearsals_planId_completedAtMillis
                    ON moment_plan_rehearsals(planId, completedAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plan_rehearsals_completedAtMillis_dismissedAtMillis_startedAtMillis
                    ON moment_plan_rehearsals(
                        completedAtMillis,
                        dismissedAtMillis,
                        startedAtMillis
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plan_rehearsals_completedAtMillis
                    ON moment_plan_rehearsals(completedAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_momentPlanId_momentPlanUpdatedAtMillis_startedAtMillis
                    ON adaptive_decisions(
                        momentPlanId,
                        momentPlanUpdatedAtMillis,
                        startedAtMillis
                    )
                    """.trimIndent(),
                )
                installRehearsalInvariantTriggers(db)
            }
        }

        internal val Migration9To10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE moment_plans " +
                        "ADD COLUMN contentRevisionId TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE moment_plan_rehearsals " +
                        "ADD COLUMN planContentRevisionId TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE adaptive_decisions " +
                        "ADD COLUMN recommendationPolicyVersion INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL("ALTER TABLE adaptive_decisions ADD COLUMN assignedProtocolId TEXT")
                db.execSQL("ALTER TABLE adaptive_decisions ADD COLUMN assignedProtocolVersion INTEGER")
                db.execSQL("ALTER TABLE adaptive_decisions ADD COLUMN actualProtocolId TEXT")
                db.execSQL("ALTER TABLE adaptive_decisions ADD COLUMN actualProtocolVersion INTEGER")
                db.execSQL(
                    "ALTER TABLE adaptive_decisions " +
                        "ADD COLUMN assignedPlanContentRevisionId TEXT",
                )
                db.execSQL(
                    "ALTER TABLE adaptive_decisions " +
                        "ADD COLUMN actualPlanContentRevisionId TEXT",
                )
                db.execSQL(
                    "ALTER TABLE adaptive_decisions " +
                        "ADD COLUMN eligibleMomentPlanCount INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE adaptive_preferences " +
                        "ADD COLUMN privateScreenProtectionEnabled INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE adaptive_preferences " +
                        "ADD COLUMN historyRetentionPolicy TEXT NOT NULL DEFAULT 'SixMonths'",
                )

                backfillPlanContentRevisions(db)

                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_moment_plan_rehearsals_planId_planContentRevisionId_completedAtMillis
                    ON moment_plan_rehearsals(
                        planId,
                        planContentRevisionId,
                        completedAtMillis
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_adaptive_decisions_momentPlanId_actualPlanContentRevisionId_startedAtMillis
                    ON adaptive_decisions(
                        momentPlanId,
                        actualPlanContentRevisionId,
                        startedAtMillis
                    )
                    """.trimIndent(),
                )
                installRehearsalInvariantTriggers(db)
            }
        }

        internal val Migration10To11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE adaptive_preferences " +
                        "ADD COLUMN pathShiftEnabled INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS path_shift_cycles (
                        cycleId TEXT NOT NULL PRIMARY KEY,
                        createdAtMillis INTEGER NOT NULL,
                        lookbackStartedAtMillis INTEGER NOT NULL,
                        lookbackEndedAtMillis INTEGER NOT NULL,
                        forecastWindowStartedAtMillis INTEGER NOT NULL,
                        forecastWindowEndsAtMillis INTEGER NOT NULL,
                        forecastPolicyVersion INTEGER NOT NULL,
                        evidenceStrength TEXT NOT NULL,
                        inputProtectedMomentCount INTEGER NOT NULL,
                        inputDistinctDayCount INTEGER NOT NULL,
                        estimatedLowerCount INTEGER NOT NULL,
                        estimatedUpperCount INTEGER NOT NULL,
                        commonWindowStartMinute INTEGER,
                        commonWindowEndMinute INTEGER,
                        preparedPlanId TEXT,
                        preparedPlanContentRevisionId TEXT,
                        preparedAtMillis INTEGER,
                        reviewFinalisedAtMillis INTEGER,
                        observedProtectedMomentCount INTEGER NOT NULL,
                        preparedPlanSelectedCount INTEGER NOT NULL,
                        preparedPlanStartedCount INTEGER NOT NULL,
                        preparedPlanCompletedCount INTEGER NOT NULL,
                        preparedPlanDismissedCount INTEGER NOT NULL,
                        wrongTimingCount INTEGER NOT NULL,
                        repeatDetectedCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        cancelledAtMillis INTEGER,
                        CHECK(forecastWindowEndsAtMillis > forecastWindowStartedAtMillis),
                        CHECK(lookbackEndedAtMillis > lookbackStartedAtMillis),
                        CHECK(estimatedLowerCount >= 0),
                        CHECK(estimatedUpperCount >= estimatedLowerCount),
                        CHECK(inputProtectedMomentCount >= 0),
                        CHECK(inputDistinctDayCount >= 0),
                        CHECK(observedProtectedMomentCount >= 0),
                        CHECK(preparedPlanSelectedCount >= 0),
                        CHECK(preparedPlanStartedCount >= 0),
                        CHECK(preparedPlanCompletedCount >= 0),
                        CHECK(preparedPlanDismissedCount >= 0),
                        CHECK(wrongTimingCount >= 0),
                        CHECK(repeatDetectedCount >= 0),
                        CHECK(
                            (preparedPlanId IS NULL AND preparedPlanContentRevisionId IS NULL)
                            OR
                            (preparedPlanId IS NOT NULL AND preparedPlanContentRevisionId IS NOT NULL)
                        ),
                        CHECK(
                            status IN ('Active', 'Finalised', 'Cancelled')
                        ),
                        CHECK(
                            (status = 'Active'
                                AND reviewFinalisedAtMillis IS NULL
                                AND cancelledAtMillis IS NULL)
                            OR
                            (status = 'Finalised'
                                AND reviewFinalisedAtMillis IS NOT NULL
                                AND reviewFinalisedAtMillis >= forecastWindowEndsAtMillis
                                AND cancelledAtMillis IS NULL)
                            OR
                            (status = 'Cancelled'
                                AND reviewFinalisedAtMillis IS NULL
                                AND cancelledAtMillis IS NOT NULL)
                        )
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_path_shift_cycles_status_forecastWindowEndsAtMillis
                    ON path_shift_cycles(status, forecastWindowEndsAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_path_shift_cycles_createdAtMillis
                    ON path_shift_cycles(createdAtMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_path_shift_cycles_preparedPlanId_preparedPlanContentRevisionId
                    ON path_shift_cycles(preparedPlanId, preparedPlanContentRevisionId)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS
                    index_path_shift_cycles_reviewFinalisedAtMillis
                    ON path_shift_cycles(reviewFinalisedAtMillis)
                    """.trimIndent(),
                )
                installPathShiftInvariantTriggers(db)
            }
        }

        internal val Migration11To12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createProtectionCoachSuggestionsTable(db)
                installProtectionCoachInvariantTriggers(db)
            }
        }

        internal val Migration12To13 =
            object : Migration(
                12,
                13,
            ) {
                override fun migrate(
                    db: SupportSQLiteDatabase,
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS adaptive_preferences_new (
                            id INTEGER NOT NULL,
                            personalSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                            gameSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                            readingSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                            momentPlanSuggestionsEnabled INTEGER NOT NULL DEFAULT 1,
                            randomisedExplorationEnabled INTEGER NOT NULL DEFAULT 1,
                            updatedAtMillis INTEGER NOT NULL DEFAULT 0,
                            privateScreenProtectionEnabled INTEGER NOT NULL DEFAULT 1,
                            historyRetentionPolicy TEXT NOT NULL DEFAULT 'SixMonths',
                            pathShiftEnabled INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        INSERT INTO adaptive_preferences_new (
                            id,
                            personalSuggestionsEnabled,
                            gameSuggestionsEnabled,
                            readingSuggestionsEnabled,
                            momentPlanSuggestionsEnabled,
                            randomisedExplorationEnabled,
                            updatedAtMillis,
                            privateScreenProtectionEnabled,
                            historyRetentionPolicy,
                            pathShiftEnabled
                        )
                        SELECT
                            id,
                            personalSuggestionsEnabled,
                            gameSuggestionsEnabled,
                            readingSuggestionsEnabled,
                            momentPlanSuggestionsEnabled,
                            randomisedExplorationEnabled,
                            updatedAtMillis,
                            privateScreenProtectionEnabled,
                            historyRetentionPolicy,
                            1
                        FROM adaptive_preferences
                        """.trimIndent(),
                    )

                    db.execSQL(
                        "DROP TABLE adaptive_preferences",
                    )

                    db.execSQL(
                        """
                        ALTER TABLE adaptive_preferences_new
                        RENAME TO adaptive_preferences
                        """.trimIndent(),
                    )

                    installFuturePathInvariantTriggers(
                        db,
                    )
                }
            }


        internal val Migration13To14 =
            object : Migration(
                13,
                14,
            ) {
                override fun migrate(
                    db: SupportSQLiteDatabase,
                ) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS safe_exit_records (
                            sourceKey TEXT NOT NULL,
                            source TEXT NOT NULL,
                            sourceId TEXT NOT NULL,
                            completedAt TEXT NOT NULL,
                            PRIMARY KEY(sourceKey)
                        )
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_safe_exit_records_completedAt
                        ON safe_exit_records(completedAt)
                        """.trimIndent(),
                    )

                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS
                        index_safe_exit_records_source_completedAt
                        ON safe_exit_records(
                            source,
                            completedAt
                        )
                        """.trimIndent(),
                    )
                }
            }

        private fun createProtectionCoachSuggestionsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS protection_coach_suggestions (
                    suggestionId TEXT NOT NULL PRIMARY KEY,
                    policyVersion INTEGER NOT NULL,
                    suggestionType TEXT NOT NULL,
                    createdAtMillis INTEGER NOT NULL,
                    expiresAtMillis INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    presentedAtMillis INTEGER,
                    acceptedAtMillis INTEGER,
                    dismissedAtMillis INTEGER,
                    suppressedAtMillis INTEGER,
                    evidenceWindowStartedAtMillis INTEGER,
                    evidenceWindowEndedAtMillis INTEGER,
                    evidenceProtectedMomentCount INTEGER NOT NULL,
                    evidenceDistinctDayCount INTEGER NOT NULL,
                    broadWindowStartMinute INTEGER,
                    broadWindowEndMinute INTEGER,
                    suggestedStartMinute INTEGER,
                    suggestedEndMinute INTEGER,
                    acceptedStartMinute INTEGER,
                    acceptedEndMinute INTEGER,
                    onboardingReasonCode TEXT,
                    relatedMomentPlanId TEXT,
                    relatedMomentPlanContentRevisionId TEXT,
                    CHECK(policyVersion > 0),
                    CHECK(expiresAtMillis > createdAtMillis),
                    CHECK(evidenceProtectedMomentCount >= 0),
                    CHECK(evidenceDistinctDayCount >= 0),
                    CHECK(evidenceWindowStartedAtMillis IS NULL OR evidenceWindowEndedAtMillis IS NULL OR evidenceWindowEndedAtMillis >= evidenceWindowStartedAtMillis),
                    CHECK(broadWindowStartMinute IS NULL OR broadWindowStartMinute BETWEEN 0 AND 1439),
                    CHECK(broadWindowEndMinute IS NULL OR broadWindowEndMinute BETWEEN 0 AND 1439),
                    CHECK(suggestedStartMinute IS NULL OR suggestedStartMinute BETWEEN 0 AND 1439),
                    CHECK(suggestedEndMinute IS NULL OR suggestedEndMinute BETWEEN 0 AND 1439),
                    CHECK(acceptedStartMinute IS NULL OR acceptedStartMinute BETWEEN 0 AND 1439),
                    CHECK(acceptedEndMinute IS NULL OR acceptedEndMinute BETWEEN 0 AND 1439),
                    CHECK(status IN ('Prepared', 'Presented', 'Accepted', 'AcceptedWithEdits', 'Dismissed', 'Suppressed', 'Expired')),
                    CHECK(suggestionType IN ('ReviewSocialApps', 'ReviewBrowserProtection', 'CreateMorningWindow', 'CreateEveningWindow', 'StartProtectionEarlier', 'EndProtectionLater', 'CreateWeekdayWindow', 'CreateWeekendWindow', 'PractiseMomentPlan', 'ReviewProtectedApps', 'EnableSupportFamily')),
                    CHECK(
                        (acceptedAtMillis IS NULL OR (dismissedAtMillis IS NULL AND suppressedAtMillis IS NULL))
                        AND (dismissedAtMillis IS NULL OR (acceptedAtMillis IS NULL AND suppressedAtMillis IS NULL))
                        AND (suppressedAtMillis IS NULL OR (acceptedAtMillis IS NULL AND dismissedAtMillis IS NULL))
                    ),
                    CHECK(status != 'AcceptedWithEdits' OR (acceptedAtMillis IS NOT NULL AND acceptedStartMinute IS NOT NULL AND acceptedEndMinute IS NOT NULL)),
                    CHECK(status != 'Accepted' OR acceptedAtMillis IS NOT NULL),
                    CHECK(status != 'Dismissed' OR dismissedAtMillis IS NOT NULL),
                    CHECK(status != 'Suppressed' OR suppressedAtMillis IS NOT NULL)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_protection_coach_suggestions_status_expiresAtMillis
                ON protection_coach_suggestions(status, expiresAtMillis)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_protection_coach_suggestions_type_status_broadWindow
                ON protection_coach_suggestions(
                    suggestionType,
                    status,
                    broadWindowStartMinute,
                    broadWindowEndMinute
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_protection_coach_suggestions_createdAtMillis
                ON protection_coach_suggestions(createdAtMillis)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS
                index_protection_coach_suggestions_relatedMomentPlan
                ON protection_coach_suggestions(
                    relatedMomentPlanId,
                    relatedMomentPlanContentRevisionId
                )
                """.trimIndent(),
            )
        }

        private fun backfillPlanContentRevisions(db: SupportSQLiteDatabase) {
            val updatePlan = db.compileStatement(
                "UPDATE moment_plans SET contentRevisionId = ? WHERE planId = ?",
            )
            db.query("SELECT planId, updatedAtMillis FROM moment_plans").use { cursor ->
                val planIdColumn = cursor.getColumnIndexOrThrow("planId")
                val updatedColumn = cursor.getColumnIndexOrThrow("updatedAtMillis")
                while (cursor.moveToNext()) {
                    val planId = cursor.getString(planIdColumn)
                    updatePlan.clearBindings()
                    updatePlan.bindString(
                        1,
                        LegacyMomentPlanContentRevisionFactory.create(
                            planId,
                            cursor.getLong(updatedColumn),
                        ),
                    )
                    updatePlan.bindString(2, planId)
                    updatePlan.executeUpdateDelete()
                }
            }

            val updateRehearsal = db.compileStatement(
                "UPDATE moment_plan_rehearsals " +
                    "SET planContentRevisionId = ? WHERE rehearsalId = ?",
            )
            db.query(
                "SELECT rehearsalId, planId, planUpdatedAtMillisAtStart " +
                    "FROM moment_plan_rehearsals",
            ).use { cursor ->
                val rehearsalIdColumn = cursor.getColumnIndexOrThrow("rehearsalId")
                val planIdColumn = cursor.getColumnIndexOrThrow("planId")
                val updatedColumn =
                    cursor.getColumnIndexOrThrow("planUpdatedAtMillisAtStart")
                while (cursor.moveToNext()) {
                    updateRehearsal.clearBindings()
                    updateRehearsal.bindString(
                        1,
                        LegacyMomentPlanContentRevisionFactory.create(
                            cursor.getString(planIdColumn),
                            cursor.getLong(updatedColumn),
                        ),
                    )
                    updateRehearsal.bindString(2, cursor.getString(rehearsalIdColumn))
                    updateRehearsal.executeUpdateDelete()
                }
            }

            val updateDecision = db.compileStatement(
                """
                UPDATE adaptive_decisions
                SET
                    assignedPlanContentRevisionId = ?,
                    actualPlanContentRevisionId = ?
                WHERE decisionId = ?
                """.trimIndent(),
            )
            db.query(
                """
                SELECT
                    decisionId,
                    assignedSuggestion,
                    actualIntervention,
                    momentPlanId,
                    momentPlanUpdatedAtMillis
                FROM adaptive_decisions
                WHERE momentPlanId IS NOT NULL
                    AND momentPlanUpdatedAtMillis IS NOT NULL
                """.trimIndent(),
            ).use { cursor ->
                val decisionIdColumn = cursor.getColumnIndexOrThrow("decisionId")
                val assignedColumn = cursor.getColumnIndexOrThrow("assignedSuggestion")
                val actualColumn = cursor.getColumnIndexOrThrow("actualIntervention")
                val planIdColumn = cursor.getColumnIndexOrThrow("momentPlanId")
                val updatedColumn = cursor.getColumnIndexOrThrow("momentPlanUpdatedAtMillis")
                while (cursor.moveToNext()) {
                    val revision = LegacyMomentPlanContentRevisionFactory.create(
                        cursor.getString(planIdColumn),
                        cursor.getLong(updatedColumn),
                    )
                    updateDecision.clearBindings()
                    if (cursor.getString(assignedColumn) == "MomentPlan") {
                        updateDecision.bindString(1, revision)
                    } else {
                        updateDecision.bindNull(1)
                    }
                    if (cursor.getString(actualColumn) == "MomentPlan") {
                        updateDecision.bindString(2, revision)
                    } else {
                        updateDecision.bindNull(2)
                    }
                    updateDecision.bindString(3, cursor.getString(decisionIdColumn))
                    updateDecision.executeUpdateDelete()
                }
            }
        }

        private val DatabaseInvariantCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                installRehearsalInvariantTriggers(db)
                installPathShiftInvariantTriggers(db)
                installProtectionCoachInvariantTriggers(db)
                installFuturePathInvariantTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                installRehearsalInvariantTriggers(db)
                installPathShiftInvariantTriggers(db)
                installProtectionCoachInvariantTriggers(db)
                installFuturePathInvariantTriggers(db)
            }
        }

        private fun installProtectionCoachInvariantTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS protection_coach_reject_unsafe_text_insert
                BEFORE INSERT ON protection_coach_suggestions
                WHEN NEW.relatedMomentPlanId LIKE '%@%'
                    OR NEW.relatedMomentPlanId LIKE '%://%'
                    OR NEW.relatedMomentPlanContentRevisionId LIKE '%@%'
                    OR NEW.relatedMomentPlanContentRevisionId LIKE '%://%'
                BEGIN
                    SELECT RAISE(ABORT, 'Protection Coach identifiers must not contain sensitive free text');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS protection_coach_reject_unsafe_text_update
                BEFORE UPDATE ON protection_coach_suggestions
                WHEN NEW.relatedMomentPlanId LIKE '%@%'
                    OR NEW.relatedMomentPlanId LIKE '%://%'
                    OR NEW.relatedMomentPlanContentRevisionId LIKE '%@%'
                    OR NEW.relatedMomentPlanContentRevisionId LIKE '%://%'
                BEGIN
                    SELECT RAISE(ABORT, 'Protection Coach identifiers must not contain sensitive free text');
                END
                """.trimIndent(),
            )
        }

        private fun installFuturePathInvariantTriggers(
            db: SupportSQLiteDatabase,
        ) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS
                adaptive_preferences_require_future_path_insert
                BEFORE INSERT ON adaptive_preferences
                WHEN NEW.pathShiftEnabled != 1
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Future Path must remain enabled'
                    );
                END
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS
                adaptive_preferences_require_future_path_update
                BEFORE UPDATE OF pathShiftEnabled ON adaptive_preferences
                WHEN NEW.pathShiftEnabled != 1
                BEGIN
                    SELECT RAISE(
                        ABORT,
                        'Future Path must remain enabled'
                    );
                END
                """.trimIndent(),
            )
        }

        private fun installPathShiftInvariantTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS path_shift_cycles_one_active_insert
                BEFORE INSERT ON path_shift_cycles
                WHEN NEW.status = 'Active'
                    AND EXISTS(
                        SELECT 1 FROM path_shift_cycles WHERE status = 'Active'
                    )
                BEGIN
                    SELECT RAISE(ABORT, 'Only one active PathShift cycle is allowed');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS path_shift_cycles_one_active_update
                BEFORE UPDATE ON path_shift_cycles
                WHEN NEW.status = 'Active'
                    AND EXISTS(
                        SELECT 1
                        FROM path_shift_cycles
                        WHERE status = 'Active' AND cycleId != NEW.cycleId
                    )
                BEGIN
                    SELECT RAISE(ABORT, 'Only one active PathShift cycle is allowed');
                END
                """.trimIndent(),
            )
        }

        private fun installRehearsalInvariantTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS
                moment_plan_rehearsals_reject_terminal_insert
                BEFORE INSERT ON moment_plan_rehearsals
                WHEN NEW.completedAtMillis IS NOT NULL
                    AND NEW.dismissedAtMillis IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Conflicting rehearsal terminal state');
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS
                moment_plan_rehearsals_reject_terminal_update
                BEFORE UPDATE ON moment_plan_rehearsals
                WHEN NEW.completedAtMillis IS NOT NULL
                    AND NEW.dismissedAtMillis IS NOT NULL
                BEGIN
                    SELECT RAISE(ABORT, 'Conflicting rehearsal terminal state');
                END
                """.trimIndent(),
            )
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appContext = context.applicationContext
                    val passphrase = DatabasePassphraseStore(appContext).getOrCreatePassphrase()

                    SqlCipherDatabaseMigrator.migratePlaintextDatabaseIfNeeded(
                        context = appContext,
                        databaseName = DatabaseName,
                        passphrase = passphrase,
                    )
                    SqlCipherDatabaseMigrator.ensureSqlCipherLoaded()

                    Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        DatabaseName,
                    )
                        .openHelperFactory(SupportOpenHelperFactory(passphrase))
                        .addMigrations(
                            Migration1To2,
                            Migration2To3,
                            Migration3To4,
                            Migration4To5,
                            Migration5To6,
                            Migration6To7,
                            Migration7To8,
                            Migration8To9,
                            Migration9To10,
                            Migration10To11,
                            Migration11To12,
                            Migration12To13,
                            Migration13To14,
                        )
                        .addCallback(DatabaseInvariantCallback)
                        .build()
                        .also { database ->
                            instance = database
                        }
                }
            }
        }
    }
}
