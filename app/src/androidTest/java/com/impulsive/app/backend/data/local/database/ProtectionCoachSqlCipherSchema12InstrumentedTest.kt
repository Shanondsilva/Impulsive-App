package com.impulsive.app.backend.data.local.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectionCoachSqlCipherSchema12InstrumentedTest {
    @Test
    fun encryptedDatabaseOpensAtSchema12WithProtectionCoachLedger() {
        val database = AppDatabase.getInstance(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        val sqlite = database.openHelper.writableDatabase
        assertEquals(12, sqlite.version)
        sqlite.query("PRAGMA cipher_version").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.getString(0).isNotBlank())
        }
        sqlite.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='protection_coach_suggestions'",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }
}
