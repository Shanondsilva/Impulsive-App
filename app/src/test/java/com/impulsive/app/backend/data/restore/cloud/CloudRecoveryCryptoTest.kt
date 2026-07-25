package com.impulsive.app.backend.data.restore.cloud

import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRecoveryCryptoTest {
    private val crypto = CloudRecoveryCrypto()

    @Test
    fun `encrypt decrypt round trip`() {
        val recovery = crypto.createNewRecovery(
            ownerUid = OwnerUid,
            payloadJson = PayloadJson,
            recoveryPassword = Password.copyOf(),
        )

        val decrypted = crypto.decrypt(
            envelopeBytes = recovery.envelopeBytes,
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertTrue(decrypted is CloudRecoveryDecryptResult.Success)
        val payload = (decrypted as CloudRecoveryDecryptResult.Success).recovery
        assertEquals(OwnerUid, payload.ownerUid)
        assertEquals(PayloadJson, payload.payloadJson)
    }

    @Test
    fun `wrong password is rejected as crypto failure`() {
        val recovery = newRecovery()

        val decrypted = crypto.decrypt(
            envelopeBytes = recovery.envelopeBytes,
            recoveryPassword = "wrong-password".toCharArray(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.CryptoFailure, decrypted)
    }

    @Test
    fun `altered payload ciphertext is rejected`() {
        val recovery = newRecovery()
        val tampered = replaceBase64Bytes(recovery.envelopeString(), "payloadCipherTextBase64") { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        }

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.CryptoFailure, decrypted)
    }

    @Test
    fun `altered wrapped DEK is rejected`() {
        val recovery = newRecovery()
        val tampered = replaceBase64Bytes(recovery.envelopeString(), "wrappedDekCipherTextBase64") { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        }

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.CryptoFailure, decrypted)
    }

    @Test
    fun `owner UID mismatch is rejected without success payload`() {
        val recovery = newRecovery()

        val decrypted = crypto.decrypt(
            envelopeBytes = recovery.envelopeBytes,
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = "other-user",
        )

        assertEquals(CloudRecoveryDecryptResult.OwnerMismatch, decrypted)
    }

    @Test
    fun `oversized envelope is rejected`() {
        val decrypted = crypto.decrypt(
            envelopeBytes = ByteArray(CloudRecoveryMaxEnvelopeBytes + 1),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `invalid KDF iterations are rejected`() {
        val tampered = newRecovery().envelopeString()
            .replace("\"kdfIterations\":200000", "\"kdfIterations\":199999")

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `invalid salt size is rejected`() {
        val tampered = replaceBase64Value(
            json = newRecovery().envelopeString(),
            name = "kdfSaltBase64",
            value = Base64.getEncoder().encodeToString(ByteArray(CloudRecoverySaltBytes - 1)),
        )

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `invalid IV size is rejected`() {
        val tampered = replaceBase64Value(
            json = newRecovery().envelopeString(),
            name = "payloadIvBase64",
            value = Base64.getEncoder().encodeToString(ByteArray(CloudRecoveryIvBytes - 1)),
        )

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `malformed numeric type is rejected`() {
        val tampered = newRecovery().envelopeString()
            .replace("\"kdfIterations\":200000", "\"kdfIterations\":\"200000\"")

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `canonical Base64 is enforced`() {
        val envelope = newRecovery().envelopeString()
        val salt = stringField(envelope, "kdfSaltBase64")
        val nonCanonical = salt.trimEnd('=')
        val tampered = replaceBase64Value(envelope, "kdfSaltBase64", nonCanonical)

        val decrypted = crypto.decrypt(
            envelopeBytes = tampered.toByteArray(Charsets.UTF_8),
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertEquals(CloudRecoveryDecryptResult.Malformed, decrypted)
    }

    @Test
    fun `same plaintext encrypted twice produces different ciphertext`() {
        val first = newRecovery()
        val second = newRecovery()

        assertNotEquals(first.envelopeString(), second.envelopeString())
        assertNotEquals(
            stringField(first.envelopeString(), "payloadCipherTextBase64"),
            stringField(second.envelopeString(), "payloadCipherTextBase64"),
        )
    }

    @Test
    fun `decrypted payload is exact original payloadJson`() {
        val recovery = crypto.createNewRecovery(
            ownerUid = OwnerUid,
            payloadJson = ComplexPayloadJson,
            recoveryPassword = Password.copyOf(),
        )

        val decrypted = crypto.decrypt(
            envelopeBytes = recovery.envelopeBytes,
            recoveryPassword = Password.copyOf(),
            expectedOwnerUid = OwnerUid,
        )

        assertTrue(decrypted is CloudRecoveryDecryptResult.Success)
        assertEquals(ComplexPayloadJson, (decrypted as CloudRecoveryDecryptResult.Success).recovery.payloadJson)
    }

    @Test
    fun `raw DEK does not appear as plaintext in envelope JSON`() {
        val recovery = newRecovery()
        val envelope = recovery.envelopeString()

        assertFalse(envelope.contains(recovery.rawDek.decodeToString()))
        assertFalse(envelope.contains(Base64.getEncoder().encodeToString(recovery.rawDek)))
        assertArrayEquals(recovery.rawDek, recovery.wrappedKeyMetadata.let { recovery.rawDek })
    }

    @Test
    fun `google subject hash round trips in encrypted payload`() {
        val hash = "a".repeat(64)
        val recovery = crypto.createNewRecovery(
            ownerUid = OwnerUid,
            ownerGoogleSubjectHash = hash,
            payloadJson = PayloadJson,
            recoveryPassword = Password.copyOf(),
        )

        val result = crypto.decryptForRestore(recovery.envelopeBytes, Password.copyOf())

        assertTrue(result is CloudRecoveryRestoreDecryptResult.Success)
        assertEquals(hash, (result as CloudRecoveryRestoreDecryptResult.Success).restoredRecovery.recovery.ownerGoogleSubjectHash)
    }

    @Test
    fun `null google subject hash is omitted and remains legacy compatible`() {
        val payloadJson = buildCloudRecoveryPayloadJson(
            ownerUid = OwnerUid,
            ownerGoogleSubjectHash = null,
            payloadJson = PayloadJson,
            createdAtMillis = 1L,
        )
        assertFalse(payloadJson.contains("ownerGoogleSubjectHash"))

        val parsed = parseCloudRecoveryPlainPayload(payloadJson.toByteArray(Charsets.UTF_8))
        assertEquals(null, parsed?.ownerGoogleSubjectHash)
        assertEquals(
            CloudRecoveryOwnerVerdict.LegacyEnvelope,
            cloudRecoveryOwnerVerdict(
                ownerUid = OwnerUid,
                ownerGoogleSubjectHash = parsed?.ownerGoogleSubjectHash,
                currentFirebaseUid = "new-firebase-uid",
                currentGoogleSubjectHash = "a".repeat(64),
            ),
        )
    }

    private fun newRecovery(): NewCloudRecovery = crypto.createNewRecovery(
        ownerUid = OwnerUid,
        payloadJson = PayloadJson,
        recoveryPassword = Password.copyOf(),
    )

    private fun NewCloudRecovery.envelopeString(): String = envelopeBytes.toString(Charsets.UTF_8)

    private fun replaceBase64Bytes(
        json: String,
        name: String,
        mutate: (ByteArray) -> Unit,
    ): String {
        val bytes = Base64.getDecoder().decode(stringField(json, name))
        mutate(bytes)
        return replaceBase64Value(json, name, Base64.getEncoder().encodeToString(bytes))
    }

    private fun replaceBase64Value(
        json: String,
        name: String,
        value: String,
    ): String = json.replace(
        Regex("\"$name\":\"[^\"]*\""),
        "\"$name\":\"$value\"",
    )

    private fun stringField(json: String, name: String): String =
        Regex("\"$name\":\"([^\"]*)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: error("Missing $name")

    private companion object {
        const val OwnerUid = "firebase-user-123"
        const val PayloadJson = "{\"journalNotes\":[],\"recoverySessions\":[] }"
        const val ComplexPayloadJson = "{\"payload\":\"exact \\\"quoted\\\" value \\\\ slash\",\"n\":1}"
        val Password = "correct horse battery staple".toCharArray()
    }
}