package com.impulsive.app.security.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the Room SQLCipher passphrase securely.
 *
 * The database passphrase itself is randomly generated once. Only an AES/GCM
 * encrypted copy is stored in private SharedPreferences. The AES key lives in
 * Android Keystore and is not exportable from normal app storage.
 */
class DatabasePassphraseStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )

    fun getOrCreatePassphrase(): ByteArray {
        synchronized(lock) {
            readStoredPassphrase()?.let { passphrase ->
                return passphrase.toByteArray(Charsets.UTF_8)
            }

            val passphrase = generatePassphrase()
            val encrypted = encrypt(passphrase.toByteArray(Charsets.UTF_8))
            val saved = preferences.edit()
                .putString(CipherTextKey, Base64.encodeToString(encrypted.cipherText, Base64.NO_WRAP))
                .putString(IvKey, Base64.encodeToString(encrypted.iv, Base64.NO_WRAP))
                .commit()

            check(saved) { "Could not persist Room database passphrase." }

            return passphrase.toByteArray(Charsets.UTF_8)
        }
    }

    private fun readStoredPassphrase(): String? {
        val cipherText = preferences.getString(CipherTextKey, null) ?: return null
        val iv = preferences.getString(IvKey, null) ?: return null

        val decrypted = decrypt(
            cipherText = Base64.decode(cipherText, Base64.NO_WRAP),
            iv = Base64.decode(iv, Base64.NO_WRAP),
        )

        return decrypted.toString(Charsets.UTF_8)
    }

    private fun generatePassphrase(): String {
        val randomBytes = ByteArray(PassphraseRandomBytes)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP)
    }

    private fun encrypt(plainText: ByteArray): EncryptedValue {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return EncryptedValue(
            cipherText = cipher.doFinal(plainText),
            iv = cipher.iv,
        )
    }

    private fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GcmTagBits, iv),
        )
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply {
            load(null)
        }

        val existingKey = (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)
            ?.secretKey

        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeyStore,
        )

        val keySpec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesKeySizeBits)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private data class EncryptedValue(
        val cipherText: ByteArray,
        val iv: ByteArray,
    )

    companion object {
        private const val PreferencesName = "impulsive_database_passphrase"
        private const val CipherTextKey = "cipher_text"
        private const val IvKey = "iv"
        private const val KeyAlias = "impulsive_room_database_passphrase"
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val Transformation = "AES/GCM/NoPadding"
        private const val GcmTagBits = 128
        private const val AesKeySizeBits = 256
        private const val PassphraseRandomBytes = 48

        private val lock = Any()
    }
}
