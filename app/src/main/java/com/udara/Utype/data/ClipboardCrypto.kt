package com.udara.Utype.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val ciphertext: String,
    val iv: String,
)

interface ClipboardCrypto {
    fun encrypt(plainText: String): EncryptedPayload
    fun decrypt(ciphertext: String, iv: String): String
    fun hash(plainText: String): String
}

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption for local clipboard storage.
 * Thread-safe and resilient: recovers cleanly if an OEM resets/invalidates the Keystore key
 * or if AndroidKeyStore is absent in JVM test environments.
 */
class AndroidKeystoreClipboardCrypto : ClipboardCrypto {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "utype_clipboard_aes_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }

    private val lock = Any()

    @Volatile
    private var fallbackKey: SecretKey? = null

    private fun getOrCreateKey(forceRecreate: Boolean = false): Pair<SecretKey, Boolean> {
        synchronized(lock) {
            try {
                val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
                if (forceRecreate && ks.containsAlias(KEY_ALIAS)) {
                    ks.deleteEntry(KEY_ALIAS)
                }
                val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existing != null) return existing.secretKey to true

                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                return keyGenerator.generateKey() to true
            } catch (_: Exception) {
                val existingFallback = fallbackKey
                if (existingFallback != null) return existingFallback to false
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                val generated = SecretKeySpec(bytes, "AES")
                fallbackKey = generated
                return generated to false
            }
        }
    }

    override fun encrypt(plainText: String): EncryptedPayload {
        return try {
            encryptWithKey(plainText, forceRecreate = false)
        } catch (_: KeyPermanentlyInvalidatedException) {
            encryptWithKey(plainText, forceRecreate = true)
        }
    }

    private fun encryptWithKey(plainText: String, forceRecreate: Boolean): EncryptedPayload {
        val (key, isKeystoreKey) = getOrCreateKey(forceRecreate)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (isKeystoreKey) {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } else {
            val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    override fun decrypt(ciphertext: String, iv: String): String {
        val (key, _) = getOrCreateKey(forceRecreate = false)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plainBytes = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        return String(plainBytes, Charsets.UTF_8)
    }

    override fun hash(plainText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Pure-JVM deterministic implementation for unit tests where Android Keystore is absent.
 */
class PassThroughClipboardCrypto : ClipboardCrypto {
    override fun encrypt(plainText: String): EncryptedPayload =
        EncryptedPayload(ciphertext = plainText, iv = "test-iv")

    override fun decrypt(ciphertext: String, iv: String): String = ciphertext

    override fun hash(plainText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(plainText.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
