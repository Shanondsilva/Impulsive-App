package com.impulsive.app.backend.service.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeBrowsePassSnapshotRevisionTrackerTest {

    @Test
    fun firstSnapshotCreatesARevisionAndPublishes() {
        val tracker =
            SafeBrowsePassSnapshotRevisionTracker<
                String
            >()

        var publishedRevision:
            Long? =
            null

        val accepted =
            tracker.accept(
                key = "purchase-a",
            ) { revision ->
                publishedRevision =
                    revision
            }

        assertTrue(accepted.changed)
        assertEquals(
            accepted.revision,
            publishedRevision,
        )
        assertTrue(
            tracker.isCurrent(
                accepted.revision,
            ),
        )
    }

    @Test
    fun identicalCallbackAndQuerySnapshotsShareTheSameRevision() {
        val tracker =
            SafeBrowsePassSnapshotRevisionTracker<
                String
            >()

        var publicationCount =
            0

        val callback =
            tracker.accept(
                key =
                    "same-token-purchased",
            ) {
                publicationCount += 1
            }

        val query =
            tracker.accept(
                key =
                    "same-token-purchased",
            ) {
                publicationCount += 1
            }

        assertTrue(callback.changed)
        assertFalse(query.changed)
        assertEquals(
            callback.revision,
            query.revision,
        )
        assertEquals(
            1,
            publicationCount,
        )
        assertTrue(
            tracker.isCurrent(
                callback.revision,
            ),
        )
    }

    @Test
    fun completedTopUpInvalidatesOlderPendingVerification() {
        val tracker =
            SafeBrowsePassSnapshotRevisionTracker<
                String
            >()

        val pending =
            tracker.accept(
                key =
                    "old-token-pending-top-up",
            ) {}

        val completed =
            tracker.accept(
                key =
                    "new-token-purchased",
            ) {}

        var oldPublished =
            false

        var newPublished =
            false

        assertFalse(
            tracker.runIfCurrent(
                pending.revision,
            ) {
                oldPublished = true
            },
        )

        assertTrue(
            tracker.runIfCurrent(
                completed.revision,
            ) {
                newPublished = true
            },
        )

        assertFalse(oldPublished)
        assertTrue(newPublished)
    }

    @Test
    fun invalidatingForANewPurchaseLaunchRejectsOldSnapshotWork() {
        val tracker =
            SafeBrowsePassSnapshotRevisionTracker<
                String
            >()

        val oldSnapshot =
            tracker.accept(
                key = "old-snapshot",
            ) {}

        val launchRevision =
            tracker.invalidate()

        assertFalse(
            tracker.isCurrent(
                oldSnapshot.revision,
            ),
        )

        assertTrue(
            tracker.isCurrent(
                launchRevision,
            ),
        )
    }

    @Test
    fun terminalLaunchStatePublishesOnlyForItsCurrentRevision() {
        val tracker =
            SafeBrowsePassSnapshotRevisionTracker<
                String
            >()

        val firstLaunch =
            tracker.invalidate()

        val secondLaunch =
            tracker.invalidate()

        var firstPublished =
            false

        var secondPublished =
            false

        assertFalse(
            tracker.invalidateIfCurrent(
                firstLaunch,
            ) {
                firstPublished = true
            },
        )

        assertTrue(
            tracker.invalidateIfCurrent(
                secondLaunch,
            ) {
                secondPublished = true
            },
        )

        assertFalse(firstPublished)
        assertTrue(secondPublished)
    }
}
