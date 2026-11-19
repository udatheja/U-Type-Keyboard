package com.udara.Utype.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * Falls back gracefully only if KeyStore is unavailable in test environments.
 */
class AndroidKeystoreClipboardCrypto : ClipboardCrypto {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "utype_clipboard_aes_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

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
        return keyGenerator.generateKey()
    }

    override fun encrypt(plainText: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    override fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, Base64.decode(iv, Base64.NO_WRAP))
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
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

    override fun hash(plainText: String): String = plainText.hashCode().toString()
}
