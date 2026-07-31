package com.impulsive.app.backend.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.impulsive.app.backend.domain.engine.adaptive.LegacyMomentPlanContentRevisionFactory
import java.io.IOException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentPlanContentRevisionMigration9To10Test {
    private val databaseName = "moment-plan-content-revision-migration"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrationPreservesRowsAndBackfillsExactHistoricalRevisions() {
        val currentPlanId = UUID.randomUUID().toString()
        val deletedPlanId = UUID.randomUUID().toString()
        val currentRehearsalId = UUID.randomUUID().toString()
        val deletedRehearsalId = UUID.randomUUID().toString()
        val currentDecisionId = UUID.randomUUID().toString()
        val deletedDecisionId = UUID.randomUUID().toString()

        helper.createDatabase(databaseName, 9).use { db ->
            insertPlan(db, currentPlanId, 1_000L)
            insertPlan(db, deletedPlanId, 2_000L)
            insertRehearsal(db, currentRehearsalId, currentPlanId, 1_000L)
            insertRehearsal(db, deletedRehearsalId, deletedPlanId, 2_000L)
            insertDecision(db, currentDecisionId, currentPlanId, 1_000L)
            insertDecision(db, deletedDecisionId, deletedPlanId, 2_001L)
            db.execSQL("DELETE FROM moment_plans WHERE planId = '$deletedPlanId'")
        }

        helper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            AppDatabase.Migration9To10,
        ).use { db ->
            val expectedCurrent =
                LegacyMomentPlanContentRevisionFactory.create(currentPlanId, 1_000L)
            assertEquals(
                expectedCurrent,
                db.singleString(
                    "SELECT contentRevisionId FROM moment_plans " +
                        "WHERE planId = '$currentPlanId'",
                ),
            )
            assertEquals(
                expectedCurrent,
                db.singleString(
                    "SELECT planContentRevisionId FROM moment_plan_rehearsals " +
                        "WHERE rehearsalId = '$currentRehearsalId'",
                ),
            )
            assertEquals(
                expectedCurrent,
                db.singleString(
                    "SELECT actualPlanContentRevisionId FROM adaptive_decisions " +
                        "WHERE decisionId = '$currentDecisionId'",
                ),
            )
            assertEquals(
                expectedCurrent,
                db.singleString(
                    "SELECT assignedPlanContentRevisionId FROM adaptive_decisions " +
                        "WHERE decisionId = '$currentDecisionId'",
                ),
            )

            val deletedRehearsalRevision = db.singleString(
                "SELECT planContentRevisionId FROM moment_plan_rehearsals " +
                    "WHERE rehearsalId = '$deletedRehearsalId'",
            )
            val deletedDecisionRevision = db.singleString(
                "SELECT actualPlanContentRevisionId FROM adaptive_decisions " +
                    "WHERE decisionId = '$deletedDecisionId'",
            )
            assertEquals(
                LegacyMomentPlanContentRevisionFactory.create(deletedPlanId, 2_000L),
                deletedRehearsalRevision,
            )
            assertEquals(
                LegacyMomentPlanContentRevisionFactory.create(deletedPlanId, 2_001L),
                deletedDecisionRevision,
            )
            assertNotEquals(deletedRehearsalRevision, deletedDecisionRevision)
            assertEquals(2, db.singleInt("SELECT COUNT(*) FROM moment_plan_rehearsals"))
            assertEquals(2, db.singleInt("SELECT COUNT(*) FROM adaptive_decisions"))
            assertEquals(1, db.singleInt("SELECT COUNT(*) FROM moment_plans"))
            assertFalse(expectedCurrent.contains("Plan survives"))
            assertEquals(10, db.singleInt("PRAGMA user_version"))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrationAddsRequiredNonRedundantRevisionIndexes() {
        helper.createDatabase(databaseName, 9).close()

        helper.runMigrationsAndValidate(
            databaseName,
            10,
            true,
            AppDatabase.Migration9To10,
        ).use { db ->
            val indexes = db.firstColumnSet(
                "SELECT name FROM sqlite_master WHERE type = 'index'",
            )
            assertTrue(
                "index_moment_plan_rehearsals_planId_" +
                    "planContentRevisionId_completedAtMillis" in indexes,
            )
            assertTrue(
                "index_adaptive_decisions_momentPlanId_" +
                    "actualPlanContentRevisionId_startedAtMillis" in indexes,
            )
        }
    }

    private fun insertPlan(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        planId: String,
        updatedAtMillis: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO moment_plans (
                planId, title, momentCue, actionText, futureCueText, actionType,
                actionTarget, enabled, preferredForCue, createdAtMillis,
                updatedAtMillis, rehearsedAtMillis
            ) VALUES (
                '$planId', 'Plan survives', 'Boredom', 'Take a short walk',
                'Feel clear tomorrow', 'TextOnly', NULL, 1, 0, 100,
                $updatedAtMillis, NULL
            )
            """.trimIndent(),
        )
    }

    private fun insertRehearsal(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        rehearsalId: String,
        planId: String,
        planUpdatedAtMillis: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO moment_plan_rehearsals (
                rehearsalId, planId, planUpdatedAtMillisAtStart, mode,
                startedAtMillis, completedAtMillis, dismissedAtMillis
            ) VALUES (
                '$rehearsalId', '$planId', $planUpdatedAtMillis, 'Guided',
                3000, 3100, NULL
            )
            """.trimIndent(),
        )
    }

    private fun insertDecision(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        decisionId: String,
        planId: String,
        planUpdatedAtMillis: Long,
    ) {
        db.execSQL(
            """
            INSERT INTO adaptive_decisions (
                decisionId, protectionIncidentToken, sourceKind, createdAtMillis,
                momentWindowStartedAtMillis, momentIntensity, momentCue,
                baselineUrgeRating, assignmentMode, eligibleInterventionsMask,
                assignedSuggestion, actualIntervention, selectionProbability,
                reasonCode, momentPlanId, momentPlanUpdatedAtMillis,
                userOverrodeSuggestion, presentedAtMillis, startedAtMillis,
                completedAtMillis, dismissedAtMillis, feedbackCode,
                feedbackUpdatedAtMillis, repeatDetectedWithin20Minutes,
                firstRepeatAtMillis, observationDeadlineAtMillis,
                observationFinalisedAtMillis
            ) VALUES (
                '$decisionId', 'incident-$decisionId', 'App', 3000, 3000,
                'RepeatedAttempt', 'Boredom', 6, 'AdaptiveSuggestion', 8,
                'MomentPlan', 'MomentPlan', NULL, 'CueMatchedMomentPlan',
                '$planId', $planUpdatedAtMillis, 0, 3001, 3002, 3003, NULL,
                'NotProvided', NULL, 0, NULL, 4000, 4000
            )
            """.trimIndent(),
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleString(query: String): String =
        this.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.singleInt(query: String): Int =
        this.query(query).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.firstColumnSet(
        query: String,
    ): Set<String> = buildSet {
        this@firstColumnSet.query(query).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }
}
