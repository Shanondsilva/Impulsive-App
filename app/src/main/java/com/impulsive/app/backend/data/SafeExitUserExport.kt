package com.impulsive.app.backend.data

import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.data.repository.SafeExitPersistenceMapper
import com.impulsive.app.backend.domain.model.score.SafeExitRecord
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import org.json.JSONArray
import org.json.JSONObject

internal object SafeExitUserExport {
    private val recordOrder =
        compareByDescending<SafeExitRecord> {
            it.completedAt
        }.thenBy {
            it.sourceKey
        }

    fun canonicalRecords(
        entities: List<SafeExitEntity>,
    ): List<SafeExitRecord> {
        return entities
            .mapNotNull(
                SafeExitPersistenceMapper::
                    toDomainOrNull,
            )
            .sortedWith(
                recordOrder,
            )
    }

    fun displayName(
        source: SafeExitSource,
    ): String {
        return when (
            source
        ) {
            SafeExitSource.PivotGame ->
                "Pivot Game"

            SafeExitSource.ResetReading ->
                "Reset Reading"

            SafeExitSource.MomentPlan ->
                "Moment Plan"
        }
    }

    fun toJson(
        records: List<SafeExitRecord>,
    ): JSONArray {
        return JSONArray().also { array ->
            records
                .sortedWith(
                    recordOrder,
                )
                .forEach { record ->
                    array.put(
                        JSONObject()
                            .put(
                                "sourceKey",
                                record.sourceKey,
                            )
                            .put(
                                "source",
                                record.source
                                    .storageValue,
                            )
                            .put(
                                "sourceId",
                                record.sourceId,
                            )
                            .put(
                                "completedAt",
                                record.completedAt
                                    .toString(),
                            ),
                    )
                }
        }
    }
}