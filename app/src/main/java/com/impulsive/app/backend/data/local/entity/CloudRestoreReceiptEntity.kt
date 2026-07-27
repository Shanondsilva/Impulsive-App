package com.impulsive.app.backend.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.impulsive.app.backend.data.account.isValidGoogleSubjectHash
import java.util.UUID

@Entity(
    tableName = "cloud_restore_receipts",
)
data class CloudRestoreReceiptEntity(
    @PrimaryKey
    val receiptId: String,
    val payloadSha256: String,
    val proofType: String,
    val previousUid: String?,
    val previousGoogleSubjectHash: String?,
    val currentUid: String,
    val currentGoogleSubjectHash: String?,
    val importedAtMillis: Long,
) {
    init {
        requireValidCloudRestoreIdentity(
            receiptId = receiptId,
            payloadSha256 = payloadSha256,
            proofType =
                CloudRestoreProofType.fromPersistedValue(proofType)
                    ?: throw IllegalArgumentException(
                        "Unsupported cloud restore proof type.",
                    ),
            previousUid = previousUid,
            previousGoogleSubjectHash = previousGoogleSubjectHash,
            currentUid = currentUid,
            currentGoogleSubjectHash = currentGoogleSubjectHash,
            timestampMillis = importedAtMillis,
        )
    }
}

internal enum class CloudRestoreProofType(
    val persistedValue: String,
) {
    ExactUid("exact_uid"),
    SameGoogleIdentity("same_google_identity"),
    LegacyEnvelope("legacy_envelope"),
    ;

    companion object {
        fun fromPersistedValue(value: String): CloudRestoreProofType? =
            entries.firstOrNull { it.persistedValue == value }
    }
}

internal fun CloudRestoreReceiptEntity.requireValid():
    CloudRestoreReceiptEntity =
    apply {
        requireValidCloudRestoreIdentity(
            receiptId = receiptId,
            payloadSha256 = payloadSha256,
            proofType =
                CloudRestoreProofType.fromPersistedValue(proofType)
                    ?: throw IllegalArgumentException(
                        "Unsupported cloud restore proof type.",
                    ),
            previousUid = previousUid,
            previousGoogleSubjectHash = previousGoogleSubjectHash,
            currentUid = currentUid,
            currentGoogleSubjectHash = currentGoogleSubjectHash,
            timestampMillis = importedAtMillis,
        )
    }

internal fun requireValidCloudRestoreIdentity(
    receiptId: String,
    payloadSha256: String,
    proofType: CloudRestoreProofType,
    previousUid: String?,
    previousGoogleSubjectHash: String?,
    currentUid: String,
    currentGoogleSubjectHash: String?,
    timestampMillis: Long,
) {
    require(
        runCatching { UUID.fromString(receiptId).toString() }
            .getOrNull() == receiptId,
    ) {
        "Cloud restore receipt ID must be a canonical UUID."
    }
    require(
        payloadSha256.length == Sha256HexLength &&
            payloadSha256.all { character ->
                character in '0'..'9' || character in 'a'..'f'
            },
    ) {
        "Cloud restore payload hash must be lowercase SHA-256."
    }
    requireValidCloudRestoreUid(currentUid)
    previousUid?.let(::requireValidCloudRestoreUid)
    require(
        previousGoogleSubjectHash == null ||
            isValidGoogleSubjectHash(previousGoogleSubjectHash),
    ) {
        "Previous Google subject hash is invalid."
    }
    require(
        currentGoogleSubjectHash == null ||
            isValidGoogleSubjectHash(currentGoogleSubjectHash),
    ) {
        "Current Google subject hash is invalid."
    }
    require(timestampMillis >= 0) {
        "Cloud restore timestamp must not be negative."
    }

    when (proofType) {
        CloudRestoreProofType.ExactUid -> {
            require(previousUid == null) {
                "Exact-UID proof must not have a previous UID."
            }
            require(previousGoogleSubjectHash == null) {
                "Exact-UID proof must not have a previous Google hash."
            }
        }

        CloudRestoreProofType.SameGoogleIdentity -> {
            require(previousUid != null && previousUid != currentUid) {
                "Same-Google proof requires distinct previous and current UIDs."
            }
            require(
                previousGoogleSubjectHash != null &&
                    currentGoogleSubjectHash != null &&
                    previousGoogleSubjectHash == currentGoogleSubjectHash,
            ) {
                "Same-Google proof requires equal valid Google hashes."
            }
        }

        CloudRestoreProofType.LegacyEnvelope -> {
            require(previousGoogleSubjectHash == null) {
                "Legacy proof must not have a previous Google hash."
            }
        }
    }
}

private fun requireValidCloudRestoreUid(value: String) {
    require(
        value == value.trim() &&
            value.isNotBlank() &&
            value.length <= MaxUidChars,
    ) {
        "Cloud restore UID is invalid."
    }
}

private const val MaxUidChars = 128
private const val Sha256HexLength = 64
