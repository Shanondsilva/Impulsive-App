package com.impulsive.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfile::class,
        TriggerLog::class,
        WeeklyTarget::class,
        EvalMetrics::class,
        BypassEvent::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun triggerLogDao(): TriggerLogDao
    abstract fun weeklyTargetDao(): WeeklyTargetDao
    abstract fun evalMetricsDao(): EvalMetricsDao
    abstract fun bypassEventDao(): BypassEventDao
}
