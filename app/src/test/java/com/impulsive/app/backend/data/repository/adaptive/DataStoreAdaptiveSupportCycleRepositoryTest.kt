package com.impulsive.app.backend.data.repository.adaptive

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.impulsive.app.backend.data.local.preferences.AdaptiveSupportCyclePreferencesDataSource
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycle
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleStatus
import com.impulsive.app.backend.domain.model.adaptive.AdaptiveSupportCycleTransitionReason
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleCreateResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleLoadResult
import com.impulsive.app.backend.domain.repository.adaptive.AdaptiveSupportCycleMutationResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAdaptiveSupportCycleRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() {
        scopes.forEach(CoroutineScope::cancel)
    }

    @Test
    fun validCycleRoundTripsAndSecondCreateReturnsExistingActive() = runBlocking {
        val fixture = fixture("round-trip")
        val first = fixture.repository.create(cycle("cycle-1"), 100L, 1_000L)
        val second = fixture.repository.create(cycle("cycle-2"), 200L, 1_000L)

        assertTrue(first is AdaptiveSupportCycleCreateResult.Created)
        assertEquals(
            "cycle-1",
            (second as AdaptiveSupportCycleCreateResult.ExistingActive).state.cycle.cycleId,
        )
        val loaded = fixture.repository.load(300L) as AdaptiveSupportCycleLoadResult.Active
        assertEquals("cycle-1", loaded.state.cycle.cycleId)
        assertEquals(1L, loaded.state.revision)
    }

    @Test
    fun unknownEnumIsReportedAndAtomicallyCleared() = runBlocking {
        val fixture = fixture("unknown-enum")
        fixture.repository.create(cycle(), 100L, 1_000L)
        fixture.source.edit {
            this[stringPreferencesKey("status")] = "FutureStatus"
        }

        assertEquals(
            AdaptiveSupportCycleLoadResult.InvalidPersistedState,
            fixture.repository.load(200L),
        )
        assertEquals(AdaptiveSupportCycleLoadResult.NotFound, fixture.repository.load(200L))
    }

    @Test
    fun missingMandatoryFieldAndImpossibleDurationAreCleared() = runBlocking {
        val missing = fixture("missing-field")
        missing.repository.create(cycle(), 100L, 1_000L)
        missing.source.edit { remove(stringPreferencesKey("decision_id")) }
        assertEquals(
            AdaptiveSupportCycleLoadResult.InvalidPersistedState,
            missing.repository.load(200L),
        )

        val impossible = fixture("impossible-duration")
        impossible.repository.create(cycle(), 100L, 1_000L)
        impossible.source.edit {
            this[longPreferencesKey("consumed_duration_millis")] = 90_001L
        }
        assertEquals(
            AdaptiveSupportCycleLoadResult.InvalidPersistedState,
            impossible.repository.load(200L),
        )
    }

    @Test
    fun expiredCycleIsReportedAndCleared() = runBlocking {
        val fixture = fixture("expiry")
        fixture.repository.create(cycle(), 100L, 1_000L)

        assertEquals(AdaptiveSupportCycleLoadResult.Expired, fixture.repository.load(1_000L))
        assertEquals(AdaptiveSupportCycleLoadResult.NotFound, fixture.repository.load(1_000L))
    }

    @Test
    fun create_replacesInvalidStateAndCreatesRequestedCycleAtomically() = runBlocking {
        val fixture = fixture("replace-invalid")
        fixture.repository.create(cycle("old-cycle"), 100L, 1_000L)
        fixture.source.edit {
            this[stringPreferencesKey("status")] = "FutureStatus"
        }

        val created = fixture.repository.create(cycle("requested-cycle"), 200L, 2_000L)
            as AdaptiveSupportCycleCreateResult.Created
        val loaded = fixture.repository.load(200L) as AdaptiveSupportCycleLoadResult.Active

        assertEquals("requested-cycle", created.state.cycle.cycleId)
        assertEquals("requested-cycle", loaded.state.cycle.cycleId)
    }

    @Test
    fun create_replacesExpiredStateAndCreatesRequestedCycleAtomically() = runBlocking {
        val fixture = fixture("replace-expired")
        fixture.repository.create(cycle("old-cycle"), 100L, 1_000L)

        val created = fixture.repository.create(cycle("requested-cycle"), 1_000L, 2_000L)
            as AdaptiveSupportCycleCreateResult.Created
        val loaded = fixture.repository.load(1_000L) as AdaptiveSupportCycleLoadResult.Active

        assertEquals("requested-cycle", created.state.cycle.cycleId)
        assertEquals("requested-cycle", loaded.state.cycle.cycleId)
    }

    @Test
    fun staleRevisionCannotOverwriteNewerState() = runBlocking {
        val fixture = fixture("stale-revision")
        val created = fixture.repository.create(cycle(), 100L, 1_000L)
            as AdaptiveSupportCycleCreateResult.Created
        val updatedCycle = created.state.cycle.copy(consecutiveGameAssignments = 1)
        val updated = fixture.repository.update(
            cycleId = updatedCycle.cycleId,
            expectedRevision = created.state.revision,
            cycle = updatedCycle,
            updatedAtEpochMillis = 200L,
        ) as AdaptiveSupportCycleMutationResult.Updated

        val stale = fixture.repository.update(
            cycleId = updatedCycle.cycleId,
            expectedRevision = created.state.revision,
            cycle = updatedCycle.copy(consecutiveGameAssignments = 2),
            updatedAtEpochMillis = 300L,
        )

        assertEquals(2L, updated.state.revision)
        assertEquals(
            AdaptiveSupportCycleMutationResult.RevisionConflict(2L),
            stale,
        )
    }

    @Test
    fun concurrentUpdatesProduceOneUpdateAndOneRevisionConflict() = runBlocking {
        val fixture = fixture("concurrent")
        val created = fixture.repository.create(cycle(), 100L, 1_000L)
            as AdaptiveSupportCycleCreateResult.Created
        val results = listOf(1, 2).map { assignmentCount ->
            async(Dispatchers.Default) {
                fixture.repository.update(
                    cycleId = created.state.cycle.cycleId,
                    expectedRevision = created.state.revision,
                    cycle = created.state.cycle.copy(
                        consecutiveGameAssignments = assignmentCount,
                    ),
                    updatedAtEpochMillis = 200L,
                )
            }
        }.awaitAll()

        assertEquals(1, results.count { it is AdaptiveSupportCycleMutationResult.Updated })
        assertEquals(
            1,
            results.count { it is AdaptiveSupportCycleMutationResult.RevisionConflict },
        )
    }

    @Test
    fun terminalUpdateClearsTheActiveStore() = runBlocking {
        val fixture = fixture("terminal")
        val created = fixture.repository.create(cycle(), 100L, 1_000L)
            as AdaptiveSupportCycleCreateResult.Created
        val result = fixture.repository.update(
            cycleId = created.state.cycle.cycleId,
            expectedRevision = created.state.revision,
            cycle = created.state.cycle.copy(status = AdaptiveSupportCycleStatus.Completed),
            updatedAtEpochMillis = 200L,
        )

        assertEquals(AdaptiveSupportCycleMutationResult.Cleared, result)
        assertEquals(AdaptiveSupportCycleLoadResult.NotFound, fixture.repository.load(200L))
    }

    @Test
    fun alternativeRequestCountRoundTripsAndIsEncodedUnderItsOwnKey() = runBlocking {
        val zero = fixture("alternative-count-zero")
        zero.repository.create(cycle(), 100L, 1_000L)
        val loadedZero = zero.repository.load(200L) as AdaptiveSupportCycleLoadResult.Active
        assertEquals(0, loadedZero.state.cycle.alternativeRequestCount)

        val one = fixture("alternative-count-one")
        one.repository.create(cycle(alternativeRequestCount = 1), 100L, 1_000L)
        val loadedOne = one.repository.load(200L) as AdaptiveSupportCycleLoadResult.Active
        assertEquals(1, loadedOne.state.cycle.alternativeRequestCount)

        val encoded = one.source.edit { this[AlternativeRequestCountKey] }
        assertEquals(1, encoded)
    }

    @Test
    fun legacyPayloadWithoutTheNewKeyDecodesAsNoAlternativeRequest() = runBlocking {
        val fixture = fixture("legacy-without-key")
        fixture.repository.create(cycle(alternativeRequestCount = 1), 100L, 1_000L)
        // Reproduce a format-version-1 payload written before the key existed.
        fixture.source.edit { remove(AlternativeRequestCountKey) }

        val loaded = fixture.repository.load(200L) as AdaptiveSupportCycleLoadResult.Active

        assertEquals(1, loaded.state.revision)
        assertEquals(0, loaded.state.cycle.alternativeRequestCount)
        assertEquals("cycle-1", loaded.state.cycle.cycleId)
    }

    @Test
    fun corruptedAlternativeRequestCountIsNotSilentlyClamped() = runBlocking {
        listOf(-1, 3).forEach { corrupted ->
            val fixture = fixture("corrupt-alternative-count-$corrupted")
            fixture.repository.create(cycle(), 100L, 1_000L)
            fixture.source.edit { this[AlternativeRequestCountKey] = corrupted }

            assertEquals(
                AdaptiveSupportCycleLoadResult.InvalidPersistedState,
                fixture.repository.load(200L),
            )
        }
    }

    @Test
    fun firstAlternativeRequestUpdatePreservesTimestampsAndIncrementsRevisionOnce() =
        runBlocking {
            val fixture = fixture("alternative-count-update")
            val created = fixture.repository.create(cycle(), 100L, 1_000L)
                as AdaptiveSupportCycleCreateResult.Created

            val updated = fixture.repository.update(
                cycleId = created.state.cycle.cycleId,
                expectedRevision = created.state.revision,
                cycle = created.state.cycle.copy(alternativeRequestCount = 1),
                updatedAtEpochMillis = 200L,
            ) as AdaptiveSupportCycleMutationResult.Updated

            assertEquals(1, updated.state.cycle.alternativeRequestCount)
            assertEquals(created.state.createdAtEpochMillis, updated.state.createdAtEpochMillis)
            assertEquals(created.state.expiresAtEpochMillis, updated.state.expiresAtEpochMillis)
            assertEquals(created.state.revision + 1L, updated.state.revision)

            val reloaded = fixture.repository.load(300L) as AdaptiveSupportCycleLoadResult.Active
            assertEquals(1, reloaded.state.cycle.alternativeRequestCount)
            assertEquals(2L, reloaded.state.revision)
        }

    @Test
    fun secondAlternativeRequestTerminalMutationClearsTheStoredCycle() = runBlocking {
        val fixture = fixture("alternative-count-terminal")
        val created = fixture.repository.create(cycle(alternativeRequestCount = 1), 100L, 1_000L)
            as AdaptiveSupportCycleCreateResult.Created

        val terminal = fixture.repository.update(
            cycleId = created.state.cycle.cycleId,
            expectedRevision = created.state.revision,
            cycle = created.state.cycle.copy(
                alternativeRequestCount = 2,
                status = AdaptiveSupportCycleStatus.Abandoned,
                transitionReason =
                    AdaptiveSupportCycleTransitionReason.InterventionAbandoned,
            ),
            updatedAtEpochMillis = 200L,
        )

        assertEquals(AdaptiveSupportCycleMutationResult.Cleared, terminal)
        assertEquals(AdaptiveSupportCycleLoadResult.NotFound, fixture.repository.load(200L))
    }

    /**
     * A cycle written by the superseded attempt-ladder build carries a shorter
     * total than the protected contract allows. It must fail closed and be
     * cleared -- never silently rewritten to the full duration, which would
     * manufacture support time for an already-running cycle.
     */
    @Test
    fun obsoleteShorterActiveCyclesFailClosedAndAreCleared() = runBlocking {
        listOf(60_000L, 45_000L).forEach { obsoleteDuration ->
            val fixture = fixture("obsolete-$obsoleteDuration")
            fixture.repository.create(cycle(), 100L, 1_000L)
            // Same FormatVersion 1 payload; only the total is now invalid.
            fixture.source.edit {
                this[longPreferencesKey("initial_duration_millis")] = obsoleteDuration
            }

            assertEquals(
                AdaptiveSupportCycleLoadResult.InvalidPersistedState,
                fixture.repository.load(200L),
            )
            // The invalid-state path cleared it.
            assertEquals(
                AdaptiveSupportCycleLoadResult.NotFound,
                fixture.repository.load(200L),
            )

            // A fresh protected cycle can then be created normally.
            val recreated = fixture.repository.create(cycle(), 300L, 2_000L)
                as AdaptiveSupportCycleCreateResult.Created
            assertEquals(90_000L, recreated.state.cycle.initialDurationMillis)
        }
    }

    @Test
    fun aValidFixedDurationCycleStillRoundTrips() = runBlocking {
        val fixture = fixture("fixed-duration-round-trip")
        fixture.repository.create(cycle(), 100L, 1_000L)

        val loaded = fixture.repository.load(200L) as AdaptiveSupportCycleLoadResult.Active

        assertEquals(90_000L, loaded.state.cycle.initialDurationMillis)
        assertEquals("cycle-1", loaded.state.cycle.cycleId)
        assertEquals(1L, loaded.state.revision)
    }

    private fun fixture(name: String): Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also(scopes::add)
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(temporaryFolder.root, "$name.preferences_pb") },
        )
        val source = AdaptiveSupportCyclePreferencesDataSource(store)
        return Fixture(source, DataStoreAdaptiveSupportCycleRepository(source))
    }

    private fun cycle(
        cycleId: String = "cycle-1",
        alternativeRequestCount: Int = 0,
    ) = AdaptiveSupportCycle(
        cycleId = cycleId,
        decisionId = "decision-1",
        protectionIncidentToken = "incident-1",
        initialDurationMillis = 90_000L,
        alternativeRequestCount = alternativeRequestCount,
        transitionReason = AdaptiveSupportCycleTransitionReason.Created,
    )

    private data class Fixture(
        val source: AdaptiveSupportCyclePreferencesDataSource,
        val repository: DataStoreAdaptiveSupportCycleRepository,
    )

    private companion object {
        /** Mirrors the production key name; no production visibility is widened. */
        val AlternativeRequestCountKey = intPreferencesKey("alternative_request_count")
    }
}
