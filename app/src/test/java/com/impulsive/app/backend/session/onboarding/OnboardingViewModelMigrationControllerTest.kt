package com.impulsive.app.backend.session.onboarding

import com.impulsive.app.backend.data.restore.RestoredAccountMigrationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelMigrationControllerTest {
    @Test
    fun sameGoogleConfirmationInvokesMigrationAndSuccessInvokesReady() = runBlocking {
        var calls = 0
        var ready = false
        val controller = controller {
            calls += 1
            RestoredAccountMigrationResult.Migrated
        }

        controller.confirm(onReady = { ready = true }, onLegacyCloudVerificationRequired = {})
        yield()

        assertEquals(1, calls)
        assertTrue(ready)
        assertEquals(RestoredAccountMigrationUiState.Idle, controller.state.value)
    }

    @Test
    fun duplicateTapsAreBlocked() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val controller = controller {
            calls += 1
            release.await()
            RestoredAccountMigrationResult.Migrated
        }

        controller.confirm({}, {})
        yield()
        controller.confirm({}, {})
        yield()

        assertEquals(1, calls)
        assertEquals(RestoredAccountMigrationUiState.Restoring, controller.state.value)
        release.complete(Unit)
        yield()
    }

    @Test
    fun refreshPendingInvokesReadyAndExposesNonBlockingMessage() = runBlocking {
        var ready = false
        val controller = controller {
            RestoredAccountMigrationResult.MigratedRefreshPending
        }

        controller.confirm(onReady = { ready = true }, onLegacyCloudVerificationRequired = {})
        yield()

        assertTrue(ready)
        assertEquals(
            RestoredAccountMigrationUiState.RefreshPending,
            controller.state.value,
        )
        controller.dismissMessage()
        assertEquals(RestoredAccountMigrationUiState.Idle, controller.state.value)
    }

    @Test
    fun legacyResultOpensCloudVerification() = runBlocking {
        var legacy = false
        val controller = controller {
            RestoredAccountMigrationResult.LegacyCloudVerificationRequired
        }

        controller.confirm({}, { legacy = true })
        yield()

        assertTrue(legacy)
        assertEquals(
            RestoredAccountMigrationUiState.LegacyCloudVerificationRequired,
            controller.state.value,
        )
    }

    @Test
    fun mismatchDoesNotNavigate() = runBlocking {
        var ready = false
        var legacy = false
        val controller = controller {
            RestoredAccountMigrationResult.OwnershipChanged
        }

        controller.confirm({ ready = true }, { legacy = true })
        yield()

        assertFalse(ready)
        assertFalse(legacy)
        assertEquals(
            RestoredAccountMigrationUiState.OwnershipChanged,
            controller.state.value,
        )
    }

    @Test
    fun allCoordinatorResultsMapExplicitly() = runBlocking {
        val expected = listOf(
            RestoredAccountMigrationResult.NotAuthenticated to
                RestoredAccountMigrationUiState.OwnershipChanged,
            RestoredAccountMigrationResult.GuestNotApplicable to
                RestoredAccountMigrationUiState.OwnershipChanged,
            RestoredAccountMigrationResult.RestoreNotPending to
                RestoredAccountMigrationUiState.OwnershipChanged,
            RestoredAccountMigrationResult.ExistingLocalData to
                RestoredAccountMigrationUiState.ExistingLocalData,
            RestoredAccountMigrationResult.InvalidBackup to
                RestoredAccountMigrationUiState.InvalidBackup,
            RestoredAccountMigrationResult.AlreadyRunning to
                RestoredAccountMigrationUiState.Restoring,
        )

        expected.forEach { (result, state) ->
            val controller = controller { result }
            controller.confirm({}, {})
            yield()
            assertEquals("result=$result", state, controller.state.value)
        }
    }

    @Test
    fun failureIsNonDestructiveAndPermitsRetry() = runBlocking {
        val results = ArrayDeque<RestoredAccountMigrationResult>(
            listOf(
                RestoredAccountMigrationResult.Failed(
                    IllegalStateException("temporary"),
                ),
                RestoredAccountMigrationResult.ClaimedWithoutAutomaticBundle,
            ),
        )
        var readyCalls = 0
        val controller = controller { results.removeFirst() }

        controller.confirm({ readyCalls += 1 }, {})
        yield()
        assertTrue(controller.state.value is RestoredAccountMigrationUiState.Failed)
        assertEquals(0, readyCalls)

        controller.dismissMessage()
        controller.confirm({ readyCalls += 1 }, {})
        yield()
        assertEquals(1, readyCalls)
        assertEquals(RestoredAccountMigrationUiState.Idle, controller.state.value)
    }

    private fun CoroutineScope.controller(
        operation: suspend () -> RestoredAccountMigrationResult,
    ) = RestoredAccountMigrationUiController(
        scope = this,
        operation = RestoredAccountMigrationOperation { operation() },
    )
}
