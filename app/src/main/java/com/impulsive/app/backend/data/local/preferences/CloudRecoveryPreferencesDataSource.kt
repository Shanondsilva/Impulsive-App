package com.impulsive.app.backend.data.local.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cloudRecoveryPreferencesDataStore by
    preferencesDataStore(name = "cloud_recovery_preferences")

data class CloudRecoveryBackupMetadata(
    val lastAttemptEpochMillis: Long?,
    val lastSuccessfulBackupEpochMillis: Long?,
    val latestOutcome: CloudRecoveryStoredUploadOutcome,
)

enum class CloudRecoveryStoredUploadOutcome {
    NeverAttempted,
    Uploaded,
    NoAuthenticatedAccount,
    GuestNotApplicable,
    NoOwnedCompletedData,
    AccountMismatch,
    SetupRequired,
    AuthorizationRequired,
    Cancelled,
    RetryableFailure,
    PermanentFailure,
}

class CloudRecoveryPreferencesDataSource(
    private val context: Context,
) {
    val enabled: Flow<Boolean> =
        context.cloudRecoveryPreferencesDataStore.data.map { preferences ->
            preferences[EnabledKey] ?: false
        }

    val backupMetadata: Flow<CloudRecoveryBackupMetadata> =
        context.cloudRecoveryPreferencesDataStore.data.map { preferences ->
            val outcome =
                preferences[LatestOutcomeKey]
                    ?.let { stored ->
                        CloudRecoveryStoredUploadOutcome
                            .entries
                            .firstOrNull { it.name == stored }
                    }
                    ?: CloudRecoveryStoredUploadOutcome.NeverAttempted

            CloudRecoveryBackupMetadata(
                lastAttemptEpochMillis = preferences[LastAttemptEpochMillisKey],
                lastSuccessfulBackupEpochMillis =
                    preferences[LastSuccessEpochMillisKey],
                latestOutcome = outcome,
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        context.cloudRecoveryPreferencesDataStore.edit { preferences ->
            preferences[EnabledKey] = enabled
        }
    }

    suspend fun recordUploadAttempt(epochMillis: Long) {
        context.cloudRecoveryPreferencesDataStore.edit { preferences ->
            preferences[LastAttemptEpochMillisKey] = epochMillis
        }
    }

    suspend fun recordUploadOutcome(
        outcome: CloudRecoveryStoredUploadOutcome,
        epochMillis: Long,
    ) {
        context.cloudRecoveryPreferencesDataStore.edit { preferences ->
            preferences[LastAttemptEpochMillisKey] = epochMillis
            preferences[LatestOutcomeKey] = outcome.name
            if (outcome == CloudRecoveryStoredUploadOutcome.Uploaded) {
                preferences[LastSuccessEpochMillisKey] = epochMillis
            }
        }
    }

    suspend fun clearBackupStatus() {
        context.cloudRecoveryPreferencesDataStore.edit { preferences ->
            preferences.remove(LastAttemptEpochMillisKey)
            preferences.remove(LastSuccessEpochMillisKey)
            preferences.remove(LatestOutcomeKey)
        }
    }

    private companion object {
        val EnabledKey = booleanPreferencesKey("cloud_recovery_enabled")
        val LastAttemptEpochMillisKey =
            longPreferencesKey("cloud_recovery_last_attempt_epoch_millis")
        val LastSuccessEpochMillisKey =
            longPreferencesKey("cloud_recovery_last_success_epoch_millis")
        val LatestOutcomeKey =
            stringPreferencesKey("cloud_recovery_latest_outcome")
    }
}