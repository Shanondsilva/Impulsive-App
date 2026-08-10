package com.impulsive.app.backend.data.repository.adaptive

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.impulsive.app.backend.data.local.preferences.AdaptiveSupportCyclePreferencesDataSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStep
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportStepOutcome
import com.impulsive.app.backend.domain.model.adaptive.InterventionFamily
import com.impulsive.app.backend.domain.model.score.ScoreGameType
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleClearAllResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleRepository
import com.impulsive.app.backend.domain.repository.adaptive.PersistedAdaptiveSupportCycle
import kotlinx.coroutines.CancellationException

class DataStoreAdaptiveSupportCycleRepository(
    private val source: AdaptiveSupportCyclePreferencesDataSource,
) : AdaptiveSupportCycleRepository {
    override suspend fun create(
        cycle: AdaptiveSupportCycle,
        createdAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): AdaptiveSupportCycleCreateResult = safely(
        onFailure = AdaptiveSupportCycleCreateResult.PersistenceFailure,
    ) {
        source.edit {
            when (val stored = decode(this)) {
                DecodeResult.Empty -> createAndEncode(
                    cycle,
                    createdAtEpochMillis,
                    expiresAtEpochMillis,
                    this,
                )

                DecodeResult.Invalid -> createAndEncode(
                    cycle,
                    createdAtEpochMillis,
                    expiresAtEpochMillis,
                    this,
                )

                is DecodeResult.Valid -> {
                    if (stored.state.expiresAtEpochMillis <= createdAtEpochMillis) {
                        createAndEncode(
                            cycle,
                            createdAtEpochMillis,
                            expiresAtEpochMillis,
                            this,
                        )
                    } else {
                        AdaptiveSupportCycleCreateResult.ExistingActive(stored.state)
                    }
                }
            }
        }
    }

    private fun createAndEncode(
        cycle: AdaptiveSupportCycle,
        createdAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
        preferences: androidx.datastore.preferences.core.MutablePreferences,
    ): AdaptiveSupportCycleCreateResult {
        val state = newState(cycle, createdAtEpochMillis, expiresAtEpochMillis)
            ?: return AdaptiveSupportCycleCreateResult.Expired
        encode(state, preferences)
        return AdaptiveSupportCycleCreateResult.Created(state)
    }

    override suspend fun load(nowEpochMillis: Long): AdaptiveSupportCycleLoadResult = safely(
        onFailure = AdaptiveSupportCycleLoadResult.PersistenceFailure,
    ) {
        source.edit {
            when (val stored = decode(this)) {
                DecodeResult.Empty -> AdaptiveSupportCycleLoadResult.NotFound
                DecodeResult.Invalid -> {
                    clear()
                    AdaptiveSupportCycleLoadResult.InvalidPersistedState
                }

                is DecodeResult.Valid -> {
                    if (stored.state.expiresAtEpochMillis <= nowEpochMillis) {
                        clear()
                        AdaptiveSupportCycleLoadResult.Expired
                    } else {
                        AdaptiveSupportCycleLoadResult.Active(stored.state)
                    }
                }
            }
        }
    }

    override suspend fun update(
        cycleId: String,
        expectedRevision: Long,
        cycle: AdaptiveSupportCycle,
        updatedAtEpochMillis: Long,
    ): AdaptiveSupportCycleMutationResult = safely(
        onFailure = AdaptiveSupportCycleMutationResult.PersistenceFailure,
    ) {
        source.edit {
            when (val stored = decode(this)) {
                DecodeResult.Empty -> AdaptiveSupportCycleMutationResult.NotFound
                DecodeResult.Invalid -> {
                    clear()
                    AdaptiveSupportCycleMutationResult.InvalidPersistedState
                }

                is DecodeResult.Valid -> when {
                    stored.state.expiresAtEpochMillis <= updatedAtEpochMillis -> {
                        clear()
                        AdaptiveSupportCycleMutationResult.Expired
                    }

                    stored.state.cycle.cycleId != cycleId || cycle.cycleId != cycleId ->
                        AdaptiveSupportCycleMutationResult.CycleMismatch

                    stored.state.revision != expectedRevision ->
                        AdaptiveSupportCycleMutationResult.RevisionConflict(
                            stored.state.revision,
                        )

                    cycle.isTerminal -> {
                        clear()
                        AdaptiveSupportCycleMutationResult.Cleared
                    }

                    updatedAtEpochMillis < stored.state.updatedAtEpochMillis ||
                        stored.state.revision == Long.MAX_VALUE ->
                        AdaptiveSupportCycleMutationResult.InvalidPersistedState

                    else -> {
                        val updated = stored.state.copy(
                            cycle = cycle,
                            updatedAtEpochMillis = updatedAtEpochMillis,
                            revision = stored.state.revision + 1L,
                        )
                        encode(updated, this)
                        AdaptiveSupportCycleMutationResult.Updated(updated)
                    }
                }
            }
        }
    }

    override suspend fun clear(cycleId: String): AdaptiveSupportCycleMutationResult = safely(
        onFailure = AdaptiveSupportCycleMutationResult.PersistenceFailure,
    ) {
        source.edit {
            when (val stored = decode(this)) {
                DecodeResult.Empty -> AdaptiveSupportCycleMutationResult.NotFound
                DecodeResult.Invalid -> {
                    clear()
                    AdaptiveSupportCycleMutationResult.InvalidPersistedState
                }

                is DecodeResult.Valid -> if (stored.state.cycle.cycleId != cycleId) {
                    AdaptiveSupportCycleMutationResult.CycleMismatch
                } else {
                    clear()
                    AdaptiveSupportCycleMutationResult.Cleared
                }
            }
        }
    }

    override suspend fun clearAll(): AdaptiveSupportCycleClearAllResult = safely(
        onFailure = AdaptiveSupportCycleClearAllResult.PersistenceFailure,
    ) {
        source.clearAll()
        AdaptiveSupportCycleClearAllResult.Cleared
    }

    private fun newState(
        cycle: AdaptiveSupportCycle,
        createdAtEpochMillis: Long,
        expiresAtEpochMillis: Long,
    ): PersistedAdaptiveSupportCycle? {
        if (cycle.isTerminal || createdAtEpochMillis < 0L || expiresAtEpochMillis <= createdAtEpochMillis) {
            return null
        }
        return PersistedAdaptiveSupportCycle(
            cycle = cycle,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
            expiresAtEpochMillis = expiresAtEpochMillis,
            revision = InitialRevision,
        )
    }

    private fun decode(preferences: Preferences): DecodeResult {
        if (preferences.asMap().isEmpty()) return DecodeResult.Empty
        if (preferences[Keys.FormatVersion] != FormatVersion) return DecodeResult.Invalid

        val cycleId = preferences[Keys.CycleId]?.takeIf(String::isNotBlank)
            ?: return DecodeResult.Invalid
        val decisionId = preferences[Keys.DecisionId]?.takeIf(String::isNotBlank)
            ?: return DecodeResult.Invalid
        val incidentToken = preferences[Keys.IncidentToken]?.takeIf(String::isNotBlank)
            ?: return DecodeResult.Invalid
        val initialDuration = preferences[Keys.InitialDuration] ?: return DecodeResult.Invalid
        val consumedDuration = preferences[Keys.ConsumedDuration] ?: return DecodeResult.Invalid
        val consecutiveGames = preferences[Keys.ConsecutiveGames] ?: return DecodeResult.Invalid
        /*
         * Additive backward-compatible field. A format-version-1 payload written
         * before this key existed simply recorded no alternative request, so an
         * absent key decodes as zero rather than invalidating a live cycle.
         * Present-but-corrupted values still fail through model construction.
         */
        val alternativeRequests = preferences[Keys.AlternativeRequestCount] ?: 0
        val transitionReason = enumValue<AdaptiveSupportCycleTransitionReason>(
            preferences[Keys.TransitionReason],
        ) ?: return DecodeResult.Invalid
        val status = enumValue<AdaptiveSupportCycleStatus>(preferences[Keys.Status])
            ?: return DecodeResult.Invalid
        val hasStep = preferences[Keys.HasStep] ?: return DecodeResult.Invalid
        val currentStep = if (hasStep) decodeStep(preferences) ?: return DecodeResult.Invalid else null
        val createdAt = preferences[Keys.CreatedAt] ?: return DecodeResult.Invalid
        val updatedAt = preferences[Keys.UpdatedAt] ?: return DecodeResult.Invalid
        val expiresAt = preferences[Keys.ExpiresAt] ?: return DecodeResult.Invalid
        val revision = preferences[Keys.Revision] ?: return DecodeResult.Invalid

        if (createdAt < 0L || updatedAt < createdAt || expiresAt <= createdAt || revision < InitialRevision) {
            return DecodeResult.Invalid
        }

        val cycle = runCatching {
            AdaptiveSupportCycle(
                cycleId = cycleId,
                decisionId = decisionId,
                protectionIncidentToken = incidentToken,
                initialDurationMillis = initialDuration,
                consumedDurationMillis = consumedDuration,
                currentStep = currentStep,
                consecutiveGameAssignments = consecutiveGames,
                alternativeRequestCount = alternativeRequests,
                transitionReason = transitionReason,
                status = status,
            )
        }.getOrNull() ?: return DecodeResult.Invalid

        if (cycle.isTerminal) return DecodeResult.Invalid
        return DecodeResult.Valid(
            PersistedAdaptiveSupportCycle(
                cycle = cycle,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = updatedAt,
                expiresAtEpochMillis = expiresAt,
                revision = revision,
            ),
        )
    }

    private fun decodeStep(preferences: Preferences): AdaptiveSupportCycleStep? {
        val intervention = enumValue<InterventionFamily>(preferences[Keys.StepIntervention])
            ?: return null
        val gameName = preferences[Keys.StepGameType]
        val gameType = if (gameName == null) null else enumValue<ScoreGameType>(gameName) ?: return null
        val outcome = enumValue<AdaptiveSupportStepOutcome>(preferences[Keys.StepOutcome])
            ?: return null
        return runCatching {
            AdaptiveSupportCycleStep(
                sequence = preferences[Keys.StepSequence] ?: return null,
                intervention = intervention,
                gameType = gameType,
                startedAtCycleConsumedDurationMillis = preferences[Keys.StepStartedAt]
                    ?: return null,
                allottedDurationMillis = preferences[Keys.StepAllotted] ?: return null,
                consumedDurationMillis = preferences[Keys.StepConsumed] ?: return null,
                outcome = outcome,
            )
        }.getOrNull()
    }

    private fun encode(state: PersistedAdaptiveSupportCycle, preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences.clear()
        val cycle = state.cycle
        preferences[Keys.FormatVersion] = FormatVersion
        preferences[Keys.CycleId] = cycle.cycleId
        preferences[Keys.DecisionId] = cycle.decisionId
        preferences[Keys.IncidentToken] = cycle.protectionIncidentToken
        preferences[Keys.InitialDuration] = cycle.initialDurationMillis
        preferences[Keys.ConsumedDuration] = cycle.consumedDurationMillis
        preferences[Keys.ConsecutiveGames] = cycle.consecutiveGameAssignments
        preferences[Keys.AlternativeRequestCount] = cycle.alternativeRequestCount
        preferences[Keys.TransitionReason] = cycle.transitionReason.name
        preferences[Keys.Status] = cycle.status.name
        preferences[Keys.HasStep] = cycle.currentStep != null
        cycle.currentStep?.let { step ->
            preferences[Keys.StepSequence] = step.sequence
            preferences[Keys.StepIntervention] = step.intervention.name
            step.gameType?.let { preferences[Keys.StepGameType] = it.name }
            preferences[Keys.StepStartedAt] = step.startedAtCycleConsumedDurationMillis
            preferences[Keys.StepAllotted] = step.allottedDurationMillis
            preferences[Keys.StepConsumed] = step.consumedDurationMillis
            preferences[Keys.StepOutcome] = step.outcome.name
        }
        preferences[Keys.CreatedAt] = state.createdAtEpochMillis
        preferences[Keys.UpdatedAt] = state.updatedAtEpochMillis
        preferences[Keys.ExpiresAt] = state.expiresAtEpochMillis
        preferences[Keys.Revision] = state.revision
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?): T? =
        name?.let { stored -> enumValues<T>().singleOrNull { it.name == stored } }

    private inline suspend fun <T> safely(onFailure: T, block: suspend () -> T): T = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        onFailure
    }

    private sealed interface DecodeResult {
        data object Empty : DecodeResult
        data object Invalid : DecodeResult
        data class Valid(val state: PersistedAdaptiveSupportCycle) : DecodeResult
    }

    private object Keys {
        val FormatVersion = intPreferencesKey("format_version")
        val CycleId = stringPreferencesKey("cycle_id")
        val DecisionId = stringPreferencesKey("decision_id")
        val IncidentToken = stringPreferencesKey("incident_token")
        val InitialDuration = longPreferencesKey("initial_duration_millis")
        val ConsumedDuration = longPreferencesKey("consumed_duration_millis")
        val ConsecutiveGames = intPreferencesKey("consecutive_game_assignments")
        val AlternativeRequestCount = intPreferencesKey("alternative_request_count")
        val TransitionReason = stringPreferencesKey("transition_reason")
        val Status = stringPreferencesKey("status")
        val HasStep = booleanPreferencesKey("has_current_step")
        val StepSequence = intPreferencesKey("step_sequence")
        val StepIntervention = stringPreferencesKey("step_intervention")
        val StepGameType = stringPreferencesKey("step_game_type")
        val StepStartedAt = longPreferencesKey("step_started_at_cycle_consumed_millis")
        val StepAllotted = longPreferencesKey("step_allotted_duration_millis")
        val StepConsumed = longPreferencesKey("step_consumed_duration_millis")
        val StepOutcome = stringPreferencesKey("step_outcome")
        val CreatedAt = longPreferencesKey("created_at_epoch_millis")
        val UpdatedAt = longPreferencesKey("updated_at_epoch_millis")
        val ExpiresAt = longPreferencesKey("expires_at_epoch_millis")
        val Revision = longPreferencesKey("revision")
    }

    private companion object {
        const val FormatVersion = 1
        const val InitialRevision = 1L
    }
}
