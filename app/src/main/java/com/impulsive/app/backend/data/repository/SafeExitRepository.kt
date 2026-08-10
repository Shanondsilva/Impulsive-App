package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.dao.SafeExitDao
import com.impulsive.app.backend.data.local.database.AppDatabase
import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.data.restore.RestoreSnapshotRefreshScheduler
import com.impulsive.app.backend.domain.model.score.SafeExitAction
import com.impulsive.app.backend.domain.model.score.SafeExitCandidate
import com.impulsive.app.backend.domain.model.score.SafeExitEvaluation
import com.impulsive.app.backend.domain.model.score.SafeExitPolicy
import com.impulsive.app.backend.domain.model.score.SafeExitProgressContributionPolicy
import com.impulsive.app.backend.domain.model.score.SafeExitProgressRangePolicy
import com.impulsive.app.backend.domain.model.score.SafeExitProgressSnapshot
import com.impulsive.app.backend.domain.model.score.SafeExitTimelineItem
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.repository.score.SafeExitRecordRepository
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SafeExitRepository internal constructor(
    private val dao: SafeExitDao,
    private val onBackupRelevantDataChanged:
        () -> Unit = {},
) : SafeExitRecordRepository {
    constructor(
        context: Context,
    ) : this(
        dao =
            AppDatabase
                .getInstance(
                    context.applicationContext,
                )
                .safeExitDao(),
        onBackupRelevantDataChanged =
            safeExitBackupRefreshCallback(
                context,
            ),
    )

    override val records:
        Flow<List<SafeExitRecord>> =
        dao
            .observeAll()
            .map { entities ->
                entities.mapNotNull(
                    SafeExitPersistenceMapper::
                        toDomainOrNull,
                )
            }

    fun observeLedgerChanges(): Flow<Unit> =
        dao
            .observeRecordCount()
            .map { Unit }

    fun observeProgressSnapshot(
        selectedRange:
            ScoreRange,
        now:
            LocalDateTime,
        pivotCandidateSourceKeys:
            Set<String>,
    ): Flow<SafeExitProgressSnapshot> {
        val range =
            SafeExitProgressRangePolicy
                .range(
                    selectedRange =
                        selectedRange,
                    now =
                        now,
                )

        val startInclusive =
            range.startInclusive
                .toString()

        val endExclusive =
            range.endExclusive
                .toString()

        val pivotSource =
            SafeExitSource.PivotGame
                .storageValue

        val canonicalPivotSourceKeys =
            pivotCandidateSourceKeys
                .asSequence()
                .filter {
                    it.startsWith(
                        "$pivotSource:",
                    )
                }
                .distinct()
                .sorted()
                .toList()

        val persistedPivotKeys =
            if (
                canonicalPivotSourceKeys.isEmpty()
            ) {
                flowOf(
                    emptyList(),
                )
            } else {
                dao
                    .observeExistingSourceKeysInRange(
                        startInclusive =
                            startInclusive,
                        endExclusive =
                            endExclusive,
                        source =
                            pivotSource,
                        sourceKeys =
                            canonicalPivotSourceKeys,
                    )
            }

        return combine(
            dao
                .observeSourceCountsInRange(
                    startInclusive =
                        startInclusive,
                    endExclusive =
                        endExclusive,
                ),
            dao
                .observeRecentNonPivotInRange(
                    startInclusive =
                        startInclusive,
                    endExclusive =
                        endExclusive,
                    excludedSource =
                        pivotSource,
                    limit =
                        SafeExitProgressRecentLimit,
                ),
            persistedPivotKeys,
        ) { countRows, recentEntities, pivotKeys ->
            val sourceCounts =
                countRows.mapNotNull { row ->
                    SafeExitSource.entries
                        .firstOrNull {
                            it.storageValue ==
                                row.source
                        }
                        ?.let { source ->
                            source to
                                row.recordCount
                                    .coerceAtLeast(
                                        0L,
                                    )
                        }
                }

            val ledgerSafeExitCount =
                sourceCounts
                    .sumOf {
                        it.second
                    }
                    .coerceAtMost(
                        Int.MAX_VALUE
                            .toLong(),
                    )
                    .toInt()

            val additionalPoints =
                sourceCounts
                    .sumOf { (source, count) ->
                        count *
                            SafeExitProgressContributionPolicy
                                .additionalControlPoints(
                                    source,
                                )
                    }
                    .coerceAtMost(
                        Int.MAX_VALUE
                            .toLong(),
                    )
                    .toInt()

            val recentSafeExits =
                recentEntities
                    .mapNotNull(
                        SafeExitPersistenceMapper::
                            toDomainOrNull,
                    )
                    .filter {
                        it.source !=
                            SafeExitSource.PivotGame
                    }
                    .sortedWith(
                        compareByDescending<
                            SafeExitRecord
                        > {
                            it.completedAt
                        }.thenBy {
                            it.sourceKey
                        },
                    )
                    .take(
                        SafeExitProgressRecentLimit,
                    )
                    .map { record ->
                        SafeExitTimelineItem(
                            sourceKey =
                                record.sourceKey,
                            source =
                                record.source,
                            completedAt =
                                record.completedAt,
                            additionalControlPoints =
                                SafeExitProgressContributionPolicy
                                    .additionalControlPoints(
                                        record.source,
                                    ),
                        )
                    }

            SafeExitProgressSnapshot(
                ledgerSafeExitCount =
                    ledgerSafeExitCount,
                additionalControlPoints =
                    additionalPoints,
                persistedPivotSourceKeys =
                    pivotKeys.toSet(),
                recentSafeExits =
                    recentSafeExits,
            )
        }
    }
    override suspend fun recordIfAbsent(
        record: SafeExitRecord,
    ): Boolean {
        val inserted =
            dao.insertOnce(
                SafeExitPersistenceMapper
                    .toEntity(
                        record,
                    ),
            ) != IgnoredInsertResult

        if (
            inserted
        ) {
            try {
                onBackupRelevantDataChanged()
            } catch (
                _: Exception,
            ) {
                /*
                 * The Safe Exit row is already durable. A restore-snapshot
                 * scheduling failure must not turn that completed mutation
                 * into RetryableFailure or cause the insert to be misreported.
                 *
                 * MainActivity onStop and later successful mutations also
                 * request snapshot refresh.
                 */
            }
        }

        return inserted
    }

    private companion object {
        const val SafeExitProgressRecentLimit =
            10

        const val IgnoredInsertResult =
            -1L
    }
}

private fun safeExitBackupRefreshCallback(
    context: Context,
): () -> Unit {
    val appContext =
        context.applicationContext

    return {
        RestoreSnapshotRefreshScheduler
            .request(
                appContext,
            )
    }
}

internal object SafeExitPersistenceMapper {
    fun toEntity(
        record: SafeExitRecord,
    ): SafeExitEntity {
        val canonical =
            acceptedRecord(
                source = record.source,
                sourceId = record.sourceId,
                completedAt = record.completedAt,
            )

        require(
            canonical == record,
        ) {
            "SafeExitRecord must be canonical before persistence."
        }

        return SafeExitEntity(
            sourceKey =
                record.sourceKey,
            source =
                record.source.storageValue,
            sourceId =
                record.sourceId,
            completedAt =
                record.completedAt.toString(),
        )
    }

    fun toDomainOrNull(
        entity: SafeExitEntity,
    ): SafeExitRecord? {
        val source =
            SafeExitSource.entries
                .firstOrNull { candidate ->
                    candidate.storageValue ==
                        entity.source
                }
                ?: return null

        val completedAt =
            runCatching {
                LocalDateTime.parse(
                    entity.completedAt,
                )
            }.getOrNull()
                ?: return null

        val canonical =
            acceptedRecord(
                source = source,
                sourceId = entity.sourceId,
                completedAt = completedAt,
            )
                ?: return null

        return canonical.takeIf { record ->
            record.sourceKey ==
                entity.sourceKey &&
                record.sourceId ==
                entity.sourceId &&
                record.completedAt.toString() ==
                entity.completedAt
        }
    }

    private fun acceptedRecord(
        source: SafeExitSource,
        sourceId: String,
        completedAt: LocalDateTime,
    ): SafeExitRecord? {
        return when (
            val evaluation =
                SafeExitPolicy.evaluate(
                    SafeExitCandidate(
                        source = source,
                        sourceId = sourceId,
                        action =
                            SafeExitAction.WalkAway,
                        completedAt =
                            completedAt,
                        validCompletion =
                            true,
                    ),
                )
        ) {
            is SafeExitEvaluation.Accepted ->
                evaluation.record

            is SafeExitEvaluation.Rejected ->
                null
        }
    }
}