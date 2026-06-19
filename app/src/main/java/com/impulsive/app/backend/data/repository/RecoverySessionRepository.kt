package com.impulsive.app.backend.data.repository

import com.impulsive.app.backend.data.local.dao.RecoverySessionDao
import com.impulsive.app.backend.data.local.entity.RecoverySessionEntity
import com.impulsive.app.backend.data.sync.RecoverySessionCloudSync
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.ZoneId

class RecoverySessionRepository(
    private val recoverySessionDao: RecoverySessionDao,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun insertCompletedSession(
        startedAt: Long,
        completedAt: Long,
        urgeBefore: Int?,
        urgeAfter: Int?,
        helped: Boolean?,
        durationSeconds: Int = 90,
        triggerSource: String = "manual_demo",
        recoveryType: String = "psychological_90_second_reset",
    ): Long {
        val newId = recoverySessionDao.insertSession(
            RecoverySessionEntity(
                startedAt = startedAt,
                completedAt = completedAt,
                durationSeconds = durationSeconds,
                urgeBefore = urgeBefore,
                urgeAfter = urgeAfter,
                helped = helped,
                triggerSource = triggerSource,
                recoveryType = recoveryType,
            ),
        )
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            runCatching { RecoverySessionCloudSync().sync(recoverySessionDao, uid) }
        }
        return newId
    }

    suspend fun getTodaySessionCount(today: LocalDate = LocalDate.now(zoneId)): Int {
        val dayStartMillis = today
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        val nextDayStartMillis = today
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        return recoverySessionDao.getTodaySessionCount(
            dayStartMillis = dayStartMillis,
            nextDayStartMillis = nextDayStartMillis,
        )
    }

    suspend fun getLatestSession(): RecoverySessionEntity? {
        return recoverySessionDao.getLatestSession()
    }
}
