package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoverySessionRepositoryTest {
    @Test
    fun insertCompletedSessionInvokesBackupRelevantMutationCallbackAfterInsert() = runBlocking {
        val dao = FakeRecoverySessionDao(insertId = 42L)
        var callbackCount = 0
        val repository = RecoverySessionRepository(
            recoverySessionDao = dao,
            onBackupRelevantDataChanged = { callbackCount += 1 },
        )

        val id = repository.insertCompletedSession(
            startedAt = 100L,
            completedAt = 200L,
            urgeBefore = 8,
            urgeAfter = 3,
            helped = true,
        )

        assertEquals(42L, id)
        assertEquals(1, dao.insertCalls)
        assertEquals(1, callbackCount)
    }
}

private class FakeRecoverySessionDao(
    private val insertId: Long,
) : RecoverySessionDao {
    var insertCalls = 0

    override suspend fun insertSession(session: RecoverySessionEntity): Long {
        insertCalls += 1
        return insertId
    }

    override suspend fun getTodaySessionCount(
        dayStartMillis: Long,
        nextDayStartMillis: Long,
    ): Int = 0

    override suspend fun getLatestSession(): RecoverySessionEntity? = null

    override suspend fun getAllSessions(): List<RecoverySessionEntity> = emptyList()

    override suspend fun deleteByContentKey(
        startedAt: Long,
        completedAt: Long,
    ): Int = 0

    override suspend fun clearAllForRestore(): Int = 0
}
