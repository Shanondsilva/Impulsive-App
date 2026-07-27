package com.impulsive.app.backend.data.restore.cloud

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.impulsive.app.backend.data.local.onboarding.OnboardingPreferencesDataSource
import com.impulsive.app.backend.domain.model.onboarding.OnboardingAnswers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudRecoveryOnboardingPreferencesInstrumentedTest {
    private lateinit var dataSource: OnboardingPreferencesDataSource

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dataSource = OnboardingPreferencesDataSource(context)
        dataSource.clear()
    }

    @After
    fun tearDown() = runBlocking {
        dataSource.clear()
    }

    @Test
    fun emailPasswordRestoreAtomicallyWritesEveryAnswerAndCurrentUid() = runBlocking {
        val answers = completeAnswers()

        dataSource.restoreCompletedSnapshotForAccount(
            answers = answers,
            accountUid = " current-firebase-uid ",
            googleSubjectHash = null,
        )

        assertEquals(answers, dataSource.answers.first())
        assertTrue(dataSource.isCompleted.first())
        assertEquals("current-firebase-uid", dataSource.completedAccountUid.first())
        assertNull(dataSource.completedGoogleSubjectHash.first())
    }

    @Test
    fun GoogleRestoreWritesOnlyAValidVerifiedSubjectHash() = runBlocking {
        val verifiedHash = "b".repeat(64)

        dataSource.restoreCompletedSnapshotForAccount(
            answers = completeAnswers(),
            accountUid = "current-firebase-uid",
            googleSubjectHash = verifiedHash,
        )
        assertEquals(verifiedHash, dataSource.completedGoogleSubjectHash.first())

        dataSource.restoreCompletedSnapshotForAccount(
            answers = completeAnswers(),
            accountUid = "current-firebase-uid",
            googleSubjectHash = "invalid",
        )
        assertNull(dataSource.completedGoogleSubjectHash.first())
    }

    private fun completeAnswers(): OnboardingAnswers =
        OnboardingAnswers(
            name = "Alex",
            avatarId = "mountain",
            interrupting = listOf("work", "sleep"),
            timing = listOf("morning", "evening"),
            triggers = listOf("stress", "boredom"),
            weekOneGoal = "notice-patterns",
            dailyRelapseUrgeCount = 4,
            activeDayStartMinute = 390,
            activeDayEndMinute = 1350,
            plannedReleaseWindowMinutes = listOf(480, 720, 960, 1200),
        )
}
