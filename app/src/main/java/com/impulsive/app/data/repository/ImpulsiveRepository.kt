package com.impulsive.app.data.repository

import com.impulsive.app.data.db.BypassEvent
import com.impulsive.app.data.db.BypassEventDao
import com.impulsive.app.data.db.EvalMetrics
import com.impulsive.app.data.db.EvalMetricsDao
import com.impulsive.app.data.db.TriggerLog
import com.impulsive.app.data.db.TriggerLogDao
import com.impulsive.app.data.db.UserProfile
import com.impulsive.app.data.db.UserProfileDao
import com.impulsive.app.data.db.WeeklyTarget
import com.impulsive.app.data.db.WeeklyTargetDao
import kotlinx.coroutines.flow.Flow

class ImpulsiveRepository(
    private val userProfileDao: UserProfileDao,
    private val triggerLogDao: TriggerLogDao,
    private val weeklyTargetDao: WeeklyTargetDao,
    private val evalMetricsDao: EvalMetricsDao,
    private val bypassEventDao: BypassEventDao
) {
    // UserProfile
    fun observeProfile(): Flow<UserProfile?> = userProfileDao.observe()
    suspend fun getProfile(): UserProfile? = userProfileDao.get()
    suspend fun saveProfile(profile: UserProfile) = userProfileDao.upsert(profile)

    // TriggerLog
    suspend fun logTrigger(log: TriggerLog) = triggerLogDao.insert(log)
    fun observeTriggersSince(weekStart: Long): Flow<List<TriggerLog>> =
        triggerLogDao.observeSince(weekStart)
    fun observeUrgeCountSince(weekStart: Long): Flow<Int> =
        triggerLogDao.countSince(weekStart)
    fun observeTriggersForRange(startMs: Long, endMs: Long): Flow<List<TriggerLog>> =
        triggerLogDao.observeForDateRange(startMs, endMs)
    suspend fun getAllTriggerLogs(): List<TriggerLog> = triggerLogDao.getAll()

    // WeeklyTarget
    suspend fun ensureWeeklyTarget(weekStart: Long, allowedSessions: Int) {
        weeklyTargetDao.insert(WeeklyTarget(weekStart, allowedSessions))
    }
    fun observeWeeklyTarget(weekStart: Long): Flow<WeeklyTarget?> =
        weeklyTargetDao.observeForWeek(weekStart)
    suspend fun getWeeklyTarget(weekStart: Long): WeeklyTarget? =
        weeklyTargetDao.getForWeek(weekStart)
    suspend fun updateWeeklyTarget(target: WeeklyTarget) = weeklyTargetDao.update(target)
    suspend fun insertOrReplaceWeeklyTarget(target: WeeklyTarget) = weeklyTargetDao.insertOrReplace(target)
    suspend fun incrementSessionsUsed(weekStart: Long) = weeklyTargetDao.incrementUsed(weekStart)
    fun observeRecentWeeks(): Flow<List<WeeklyTarget>> = weeklyTargetDao.observeRecent()
    fun observeCurrentWeek(): Flow<WeeklyTarget?> = weeklyTargetDao.observeCurrent()
    fun observeLastNWeeks(n: Int): Flow<List<WeeklyTarget>> = weeklyTargetDao.observeLastN(n)
    suspend fun getAllWeeklyTargets(): List<WeeklyTarget> = weeklyTargetDao.getAll()

    // EvalMetrics
    suspend fun logEval(phase: Int, name: String, value: String) {
        evalMetricsDao.insert(
            EvalMetrics(
                phaseNumber = phase,
                metricName = name,
                metricValue = value,
                timestamp = System.currentTimeMillis()
            )
        )
    }
    suspend fun getEvalForPhase(phase: Int): List<EvalMetrics> =
        evalMetricsDao.getAllForPhase(phase)
    suspend fun getAllEvalMetrics(): List<EvalMetrics> = evalMetricsDao.getAll()

    // BypassEvent
    suspend fun logBypass(type: String) {
        bypassEventDao.insert(
            BypassEvent(timestamp = System.currentTimeMillis(), type = type)
        )
    }
    suspend fun markBypassRecovered() {
        bypassEventDao.getLatestUnrecovered()?.let {
            bypassEventDao.update(it.copy(recovered = true))
        }
    }
    suspend fun getLatestUnrecovered(): BypassEvent? = bypassEventDao.getLatestUnrecovered()
    fun observeBypassEvents(): Flow<List<BypassEvent>> = bypassEventDao.observeAll()
    suspend fun getAllBypassEvents(): List<BypassEvent> = bypassEventDao.getAll()
}
