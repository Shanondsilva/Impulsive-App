package com.impulsive.app.backend.data.local.database

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDatabaseSchemaSourceTest {
    private val schemaDirectory =
        File("schemas/com.impulsive.app.backend.data.local.database.AppDatabase")
    private val schema8 = JSONObject(File(schemaDirectory, "8.json").readText())
    private val schema9 = JSONObject(File(schemaDirectory, "9.json").readText())
    private val schema10 = JSONObject(File(schemaDirectory, "10.json").readText())
    private val schema12 = JSONObject(File(schemaDirectory, "12.json").readText())

    @Test
    fun exportedSchemaEightContainsAdaptiveTablesAndRequiredIndexes() {
        val database = schema8.getJSONObject("database")
        assertEquals(8, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        val tables = buildMap {
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                put(entity.getString("tableName"), entity)
            }
        }
        assertTrue("adaptive_decisions" in tables)
        assertTrue("moment_plans" in tables)
        assertTrue("adaptive_preferences" in tables)

        val indexes = tables.values.flatMap { entity ->
            val entityIndexes = entity.optJSONArray("indices")
            if (entityIndexes == null) {
                emptyList()
            } else {
                (0 until entityIndexes.length()).map { index ->
                    entityIndexes.getJSONObject(index).getString("name")
                }
            }
        }.toSet()
        assertTrue(indexes.containsAll(RequiredIndexes))
    }

    @Test
    fun exportedSchemaNineContainsRehearsalTableAndIndexes() {
        val database = schema9.getJSONObject("database")
        assertEquals(9, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        val rehearsal = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .single { it.getString("tableName") == "moment_plan_rehearsals" }
        val fields = rehearsal.getJSONArray("fields")
        val columns = (0 until fields.length())
            .map { fields.getJSONObject(it).getString("columnName") }
            .toSet()
        assertEquals(
            setOf(
                "rehearsalId",
                "planId",
                "planUpdatedAtMillisAtStart",
                "mode",
                "startedAtMillis",
                "completedAtMillis",
                "dismissedAtMillis",
            ),
            columns,
        )
        val indexes = rehearsal.getJSONArray("indices")
        val names = (0 until indexes.length())
            .map { indexes.getJSONObject(it).getString("name") }
            .toSet()
        assertTrue(names.containsAll(RequiredRehearsalIndexes))
    }

    @Test
    fun exportedSchemaTenContainsExactPlanRevisionColumnsAndIndexes() {
        val database = schema10.getJSONObject("database")
        assertEquals(10, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        val tables = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .associateBy { it.getString("tableName") }

        assertTrue("contentRevisionId" in tables.getValue("moment_plans").columnNames())
        assertTrue(
            "planContentRevisionId" in
                tables.getValue("moment_plan_rehearsals").columnNames(),
        )
        assertTrue(
            "actualPlanContentRevisionId" in
                tables.getValue("adaptive_decisions").columnNames(),
        )

        val indexes = tables.values.flatMap { entity ->
            val values = entity.optJSONArray("indices") ?: return@flatMap emptyList()
            (0 until values.length()).map { values.getJSONObject(it).getString("name") }
        }
        assertTrue(
            "index_moment_plan_rehearsals_planId_" +
                "planContentRevisionId_completedAtMillis" in indexes,
        )
        assertTrue(
            "index_adaptive_decisions_momentPlanId_" +
                "actualPlanContentRevisionId_startedAtMillis" in indexes,
        )
    }

    @Test
    fun adaptiveSchemaContainsNoPrivateSourceContentColumns() {
        val entities = schema10.getJSONObject("database").getJSONArray("entities")
        val adaptiveTables = setOf(
            "adaptive_decisions",
            "moment_plans",
            "adaptive_preferences",
            "moment_plan_rehearsals",
        )
        val columns = buildList {
            for (entityIndex in 0 until entities.length()) {
                val entity = entities.getJSONObject(entityIndex)
                if (entity.getString("tableName") !in adaptiveTables) continue
                val fields = entity.getJSONArray("fields")
                for (fieldIndex in 0 until fields.length()) {
                    add(fields.getJSONObject(fieldIndex).getString("columnName").lowercase())
                }
            }
        }

        ForbiddenColumnFragments.forEach { forbidden ->
            assertFalse(
                "Forbidden adaptive schema column fragment: $forbidden",
                columns.any { forbidden in it },
            )
        }
    }

    @Test
    fun migrationIsRegisteredWithoutDestructiveFallbackAndProductionUsesSqlCipher() {
        val source = File(
            "src/main/java/com/impulsive/app/backend/data/local/database/AppDatabase.kt",
        ).readText()

        assertTrue(source.contains("version = 12"))
        assertTrue(source.contains("Migration7To8 = object : Migration(7, 8)"))
        assertTrue(source.contains("Migration8To9 = object : Migration(8, 9)"))
        assertTrue(source.contains("Migration9To10 = object : Migration(9, 10)"))
        assertTrue(source.contains("Migration10To11 = object : Migration(10, 11)"))
        assertTrue(source.contains("Migration11To12 = object : Migration(11, 12)"))
        assertTrue(source.contains("Migration7To8,"))
        assertTrue(source.contains("Migration8To9,"))
        assertTrue(source.contains("Migration9To10,"))
        assertTrue(source.contains("Migration10To11,"))
        assertTrue(source.contains("Migration11To12,"))
        assertTrue(source.contains("SupportOpenHelperFactory(passphrase)"))
        assertFalse(source.contains("fallbackToDestructiveMigration"))
        assertFalse(source.contains("createFromAsset"))
    }

    @Test
    fun exportedSchemaTwelveContainsProtectionCoachLedgerWithoutSourceIdentityColumns() {
        val database = schema12.getJSONObject("database")
        assertEquals(12, database.getInt("version"))
        val entities = database.getJSONArray("entities")
        val tables = (0 until entities.length())
            .map { entities.getJSONObject(it) }
            .associateBy { it.getString("tableName") }
        val coach = tables.getValue("protection_coach_suggestions")

        assertEquals(
            setOf(
                "suggestionId",
                "policyVersion",
                "suggestionType",
                "createdAtMillis",
                "expiresAtMillis",
                "status",
                "presentedAtMillis",
                "acceptedAtMillis",
                "dismissedAtMillis",
                "suppressedAtMillis",
                "evidenceWindowStartedAtMillis",
                "evidenceWindowEndedAtMillis",
                "evidenceProtectedMomentCount",
                "evidenceDistinctDayCount",
                "broadWindowStartMinute",
                "broadWindowEndMinute",
                "suggestedStartMinute",
                "suggestedEndMinute",
                "acceptedStartMinute",
                "acceptedEndMinute",
                "onboardingReasonCode",
                "relatedMomentPlanId",
                "relatedMomentPlanContentRevisionId",
            ),
            coach.columnNames(),
        )

        val indexNames = coach.getJSONArray("indices").let { indexes ->
            (0 until indexes.length()).map { indexes.getJSONObject(it).getString("name") }
        }.toSet()
        assertTrue(indexNames.containsAll(RequiredCoachIndexes))
        val columns = coach.columnNames().map { it.lowercase() }
        ForbiddenColumnFragments.forEach { forbidden ->
            assertFalse(
                "Forbidden Protection Coach column fragment: $forbidden",
                columns.any { forbidden in it },
            )
        }
    }

    @Test
    fun previousExportedSchemasRemainByteForByteUnchanged() {
        PreviousSchemaHashes.forEach { (name, expectedHash) ->
            assertEquals(
                "Old exported schema $name changed",
                expectedHash,
                File(schemaDirectory, name).sha256(),
            )
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02X".format(byte) }
    }

    private fun JSONObject.columnNames(): Set<String> {
        val fields = getJSONArray("fields")
        return (0 until fields.length())
            .mapTo(linkedSetOf()) { fields.getJSONObject(it).getString("columnName") }
    }

    private companion object {
        val RequiredIndexes = setOf(
            "index_adaptive_decisions_protectionIncidentToken",
            "index_adaptive_decisions_createdAtMillis",
            "index_adaptive_decisions_observationFinalisedAtMillis_" +
                "observationDeadlineAtMillis",
            "index_adaptive_decisions_actualIntervention_" +
                "observationFinalisedAtMillis_createdAtMillis",
            "index_adaptive_decisions_momentCue_" +
                "observationFinalisedAtMillis_createdAtMillis",
            "index_adaptive_decisions_momentPlanId",
            "index_moment_plans_enabled_updatedAtMillis",
            "index_moment_plans_momentCue_enabled_preferredForCue",
        )

        val RequiredRehearsalIndexes = setOf(
            "index_moment_plan_rehearsals_planId_startedAtMillis",
            "index_moment_plan_rehearsals_planId_completedAtMillis",
            "index_moment_plan_rehearsals_completedAtMillis_" +
                "dismissedAtMillis_startedAtMillis",
            "index_moment_plan_rehearsals_completedAtMillis",
        )

        val ForbiddenColumnFragments = listOf(
            "url",
            "domain",
            "search",
            "pagetitle",
            "pagecontent",
            "notification",
            "email",
            "firebaseuid",
            "medical",
        )

        val PreviousSchemaHashes = mapOf(
            "3.json" to "6B0323A212E6075345AD0232919827DBBAEB0D9EAEC6836BA9A3952C5954E89B",
            "4.json" to "5500F1DD53F7636C802386338EC0EDAD0531108C2776E80BAAE76F18FD75CC7A",
            "5.json" to "51A5C4D3E8E8C489299C69AD2EBB25F225694A695025B262714D351A4A44D357",
            "6.json" to "D9A1A1AC44CFA5022E9C55408040E2BB9197BC33D3E0FD98115630CE56D919CC",
            "7.json" to "C30524255B82C60FA34093679D8FAE21E9B63550E35B0F7E97B3BB4B4B13F874",
            "8.json" to "8886D5F171F29805D287F2D3B3F3C354ED9A466B2C0EA558ACE7CB6280BB8ECD",
            "9.json" to "1F039785F96DA5BA24711177D0B8467DBBEBE9F785D73CF79A99C4CB13B86E22",
            "10.json" to "8BC1810D8136A975375F6BA5A144F3A310550C8012C853F946DC9C5FD829042E",
            "11.json" to "CF7623F6B5342B187B94CC7D04822A00DA0CAFF76B95DF0E9DBDA5BD55763CA4",
        )

        val RequiredCoachIndexes = setOf(
            "index_protection_coach_suggestions_status_expiresAtMillis",
            "index_protection_coach_suggestions_type_status_broadWindow",
            "index_protection_coach_suggestions_createdAtMillis",
            "index_protection_coach_suggestions_relatedMomentPlan",
        )
    }
}
