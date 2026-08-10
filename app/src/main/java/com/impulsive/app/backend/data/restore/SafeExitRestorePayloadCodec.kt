package com.impulsive.app.backend.data.restore

import com.impulsive.app.backend.data.local.entity.SafeExitEntity
import com.impulsive.app.backend.data.repository.SafeExitPersistenceMapper
import com.impulsive.app.backend.domain.model.score.SafeExitSource
import org.json.JSONArray
import org.json.JSONObject

internal data class ValidatedSafeExitRestorePayload(
    val records: List<SafeExitEntity>,
)

internal object SafeExitRestorePayloadCodec {
    const val JsonKey =
        "safeExitData"

    internal const val CurrentFormatVersion =
        1

    private const val MaximumRecords =
        100_000

    private const val MaximumSourceKeyChars =
        512

    private const val MaximumCompletedAtChars =
        64

    private val recordOrder =
        compareByDescending<SafeExitEntity> {
            it.completedAt
        }.thenBy {
            it.sourceKey
        }

    fun encode(
        records: List<SafeExitEntity>,
    ): JSONObject {
        require(
            records.size <= MaximumRecords,
        ) {
            "Too many Safe Exit records"
        }

        val seenSourceKeys =
            HashSet<String>(
                records.size,
            )

        val canonicalRecords =
            records.map { entity ->
                require(
                    entity.sourceKey.length <=
                        MaximumSourceKeyChars,
                ) {
                    "Safe Exit source key is too long"
                }

                require(
                    entity.completedAt.length <=
                        MaximumCompletedAtChars,
                ) {
                    "Safe Exit completion time is too long"
                }

                val domain =
                    requireNotNull(
                        SafeExitPersistenceMapper
                            .toDomainOrNull(
                                entity,
                            ),
                    ) {
                        "Cannot encode a non-canonical Safe Exit record"
                    }

                val canonical =
                    SafeExitPersistenceMapper
                        .toEntity(
                            domain,
                        )

                require(
                    canonical == entity,
                ) {
                    "Cannot encode a non-canonical Safe Exit record"
                }

                require(
                    seenSourceKeys.add(
                        canonical.sourceKey,
                    ),
                ) {
                    "Duplicate Safe Exit source key"
                }

                canonical
            }.sortedWith(
                recordOrder,
            )

        val encodedRecords =
            JSONArray()

        canonicalRecords.forEach { record ->
            encodedRecords.put(
                JSONArray()
                    .put(
                        record.sourceKey,
                    )
                    .put(
                        record.completedAt,
                    ),
            )
        }

        return JSONObject()
            .put(
                "formatVersion",
                CurrentFormatVersion,
            )
            .put(
                "records",
                encodedRecords,
            )
    }

    fun decodeIfPresent(
        payload: JSONObject,
    ): ValidatedSafeExitRestorePayload? {
        if (
            !payload.has(
                JsonKey,
            )
        ) {
            return null
        }

        val extension =
            requiredObject(
                payload,
                JsonKey,
            )

        val formatVersion =
            requiredInt(
                extension,
                "formatVersion",
            )

        require(
            formatVersion ==
                CurrentFormatVersion,
        ) {
            "Unsupported Safe Exit restore payload version"
        }

        val encodedRecords =
            requiredArray(
                extension,
                "records",
            )

        require(
            encodedRecords.length() <=
                MaximumRecords,
        ) {
            "Too many Safe Exit records"
        }

        val seenSourceKeys =
            HashSet<String>(
                encodedRecords.length(),
            )

        val decodedRecords =
            ArrayList<SafeExitEntity>(
                encodedRecords.length(),
            )

        for (
            index in 0 until
                encodedRecords.length()
        ) {
            val rawRecord =
                encodedRecords.get(
                    index,
                )

            require(
                rawRecord is JSONArray,
            ) {
                "Safe Exit record must be an array"
            }

            require(
                rawRecord.length() == 2,
            ) {
                "Safe Exit record must contain exactly two fields"
            }

            val sourceKey =
                requiredArrayString(
                    array = rawRecord,
                    index = 0,
                    name = "Safe Exit source key",
                    maxLength =
                        MaximumSourceKeyChars,
                )

            val completedAt =
                requiredArrayString(
                    array = rawRecord,
                    index = 1,
                    name =
                        "Safe Exit completion time",
                    maxLength =
                        MaximumCompletedAtChars,
                )

            require(
                seenSourceKeys.add(
                    sourceKey,
                ),
            ) {
                "Duplicate Safe Exit source key"
            }

            val separatorIndex =
                sourceKey.indexOf(
                    ':',
                )

            require(
                separatorIndex > 0 &&
                    separatorIndex <
                    sourceKey.lastIndex,
            ) {
                "Invalid Safe Exit source key"
            }

            val sourceStorageValue =
                sourceKey.substring(
                    0,
                    separatorIndex,
                )

            val sourceId =
                sourceKey.substring(
                    separatorIndex + 1,
                )

            val source =
                SafeExitSource.entries
                    .firstOrNull { candidate ->
                        candidate.storageValue ==
                            sourceStorageValue
                    }
                    ?: throw IllegalArgumentException(
                        "Unknown Safe Exit source",
                    )

            val candidate =
                SafeExitEntity(
                    sourceKey =
                        sourceKey,
                    source =
                        source.storageValue,
                    sourceId =
                        sourceId,
                    completedAt =
                        completedAt,
                )

            val domain =
                requireNotNull(
                    SafeExitPersistenceMapper
                        .toDomainOrNull(
                            candidate,
                        ),
                ) {
                    "Invalid Safe Exit restore record"
                }

            val canonical =
                SafeExitPersistenceMapper
                    .toEntity(
                        domain,
                    )

            require(
                canonical == candidate,
            ) {
                "Invalid Safe Exit restore record"
            }

            decodedRecords.add(
                canonical,
            )
        }

        return ValidatedSafeExitRestorePayload(
            records =
                decodedRecords.sortedWith(
                    recordOrder,
                ),
        )
    }

    private fun requiredObject(
        parent: JSONObject,
        name: String,
    ): JSONObject {
        val value =
            parent.get(
                name,
            )

        require(
            value is JSONObject,
        ) {
            "$name must be an object"
        }

        return value
    }

    private fun requiredArray(
        parent: JSONObject,
        name: String,
    ): JSONArray {
        val value =
            parent.get(
                name,
            )

        require(
            value is JSONArray,
        ) {
            "$name must be an array"
        }

        return value
    }

    private fun requiredInt(
        parent: JSONObject,
        name: String,
    ): Int {
        val value =
            parent.get(
                name,
            )

        return when (
            value
        ) {
            is Int ->
                value

            is Long -> {
                require(
                    value in
                        Int.MIN_VALUE.toLong()..
                        Int.MAX_VALUE.toLong(),
                ) {
                    "$name is outside the supported integer range"
                }

                value.toInt()
            }

            else ->
                throw IllegalArgumentException(
                    "$name must be an integer",
                )
        }
    }

    private fun requiredArrayString(
        array: JSONArray,
        index: Int,
        name: String,
        maxLength: Int,
    ): String {
        val value =
            array.get(
                index,
            )

        require(
            value is String,
        ) {
            "$name must be a string"
        }

        require(
            value.length <=
                maxLength,
        ) {
            "$name exceeds maximum allowed length"
        }

        return value
    }
}
