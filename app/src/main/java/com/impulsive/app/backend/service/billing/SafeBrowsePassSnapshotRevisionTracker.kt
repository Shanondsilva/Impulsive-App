package com.impulsive.app.backend.service.billing

internal data class SafeBrowsePassSnapshotAcceptance(
    val revision: Long,
    val changed: Boolean,
)

/**
 * Orders asynchronous work derived from Google Play purchase snapshots.
 *
 * Equal snapshot keys share one revision. A materially different snapshot
 * advances the revision and makes work from the previous snapshot obsolete.
 *
 * The callbacks passed to this class must remain synchronous and must not
 * call back into this tracker.
 */
internal class SafeBrowsePassSnapshotRevisionTracker<K : Any> {

    private val lock =
        Any()

    private var currentKey: K? =
        null

    private var revision: Long =
        0L

    fun accept(
        key: K,
        publishWhenChanged:
            (revision: Long) -> Unit,
    ): SafeBrowsePassSnapshotAcceptance =
        synchronized(lock) {
            val changed =
                currentKey != key

            if (changed) {
                currentKey = key
                revision += 1L
                publishWhenChanged(revision)
            }

            SafeBrowsePassSnapshotAcceptance(
                revision = revision,
                changed = changed,
            )
        }

    fun invalidate(
        publish:
            (revision: Long) -> Unit =
            {},
    ): Long =
        synchronized(lock) {
            currentKey = null
            revision += 1L
            publish(revision)
            revision
        }

    fun invalidateIfCurrent(
        expectedRevision: Long,
        publish:
            (revision: Long) -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (revision != expectedRevision) {
                return@synchronized false
            }

            currentKey = null
            revision += 1L
            publish(revision)
            true
        }

    fun runIfCurrent(
        expectedRevision: Long,
        publish: () -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (revision != expectedRevision) {
                return@synchronized false
            }

            publish()
            true
        }

    fun isCurrent(
        expectedRevision: Long,
    ): Boolean =
        synchronized(lock) {
            revision == expectedRevision
        }
}
