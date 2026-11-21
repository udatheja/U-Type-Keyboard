package com.udara.Utype.engine

import android.util.Base64
import com.udara.Utype.data.LanguageMode
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class DictionaryExportEntry(
    val word: String,
    val language: LanguageMode,
    val frequency: Int,
    val isUserAdded: Boolean,
)

/**
 * Passphrase-encrypted export and strictly validated import for the offline Personal Dictionary.
 *
 * Security controls (OWASP MASVS):
 * - Key derivation: PBKDF2WithHmacSHA256 (120,000 iterations, random 16-byte salt, 256-bit key).
 * - Authenticated encryption: AES/GCM/NoPadding (random 12-byte IV, 128-bit auth tag).
 * - Strict import bounds: 2 MB max payload, 10,000 max entries, 40-char max single-word entries,
 *   schema & iteration-count validation, and clean error reporting on bad passphrase or tampering.
 */
object DictionaryCryptoExporter {

    const val FORMAT_ID = "utype_encrypted_dictionary_v1"
    const val KDF_NAME = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 120_000
    const val MIN_ITERATIONS = 10_000
    const val MAX_ITERATIONS = 500_000
    const val MIN_PASSPHRASE_LENGTH = 6
    const val MAX_IMPORT_BYTES = 2 * 1024 * 1024 // 2 MB hard cap
    const val MAX_ENTRIES = 10_000
    const val MAX_WORD_LENGTH = SymSpellIndex.MAX_WORD_LENGTH

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun encryptDictionary(
        entries: List<DictionaryExportEntry>,
        passphrase: String,
        iterations: Int = DEFAULT_ITERATIONS,
    ): String {
        require(passphrase.length >= MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
        }
        val safeIterations = iterations.coerceIn(MIN_ITERATIONS, MAX_ITERATIONS)

        val payloadJson = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("entries", JSONArray().apply {
                entries.take(MAX_ENTRIES).forEach { e ->
                    val cleaned = e.word.trim()
                    if (isValidSingleWord(cleaned)) {
                        put(JSONObject().apply {
                            put("word", cleaned)
                            put("language", e.language.code)
                            put("frequency", e.frequency.coerceAtLeast(1))
                            put("isUserAdded", e.isUserAdded)
                        })
                    }
                }
            })
        }.toString()

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(passphrase, salt, safeIterations)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(payloadJson.toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("format", FORMAT_ID)
            put("kdf", KDF_NAME)
            put("iterations", safeIterations)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }.toString(2)
    }

    fun decryptAndValidateDictionary(
        encryptedJson: String,
        passphrase: String,
    ): List<DictionaryExportEntry> {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty." }
        val rawBytes = encryptedJson.toByteArray(Charsets.UTF_8)
        require(rawBytes.size <= MAX_IMPORT_BYTES) {
            "Dictionary file exceeds the 2 MB security limit."
        }

        val trimmed = encryptedJson.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Invalid dictionary file format."
        }

        val envelope = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            throw IllegalArgumentException("Malformed dictionary file.")
        }

        require(envelope.optString("format") == FORMAT_ID) {
            "Unsupported dictionary file format."
        }
        require(envelope.optString("kdf") == KDF_NAME) {
            "Unsupported key derivation algorithm."
        }
        val iterations = envelope.optInt("iterations", -1)
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Invalid encryption parameters."
        }

        val salt = decodeStrictBase64(envelope.optString("salt"))
        val iv = decodeStrictBase64(envelope.optString("iv"))
        val cipherBytes = decodeStrictBase64(envelope.optString("ciphertext"))
        require(salt.size == SALT_BYTES && iv.size == IV_BYTES && cipherBytes.size >= 16) {
            "Corrupted encrypted dictionary envelope."
        }

        val plainJson = try {
            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect passphrase or corrupted dictionary file.")
        } catch (_: GeneralSecurityException) {
            throw IllegalArgumentException("Could not decrypt dictionary file.")
        }

        val payload = try {
            JSONObject(plainJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("Decrypted dictionary payload is malformed.")
        }

        require(payload.optInt("schemaVersion", -1) == 1) {
            "Unsupported dictionary schema version."
        }
        val entriesArray = payload.optJSONArray("entries")
            ?: throw IllegalArgumentException("Dictionary payload is missing entries.")
        require(entriesArray.length() <= MAX_ENTRIES) {
            "Dictionary exceeds the $MAX_ENTRIES entry limit."
        }

        val validated = ArrayList<DictionaryExportEntry>(entriesArray.length())
        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.optJSONObject(i)
                ?: throw IllegalArgumentException("Invalid dictionary entry at index $i.")
            val word = obj.optString("word", "").trim()
            val langCode = obj.optString("language", "")
            if (langCode != LanguageMode.ENGLISH.code && langCode != LanguageMode.SINHALA.code) {
                throw IllegalArgumentException("Invalid language code in dictionary entry.")
            }
            if (!isValidSingleWord(word)) {
                throw IllegalArgumentException("Invalid word found in dictionary entry.")
            }
            val freq = obj.optInt("frequency", 1).coerceIn(1, 100_000)
            val userAdded = obj.optBoolean("isUserAdded", true)
            validated.add(
                DictionaryExportEntry(
                    word = word,
                    language = LanguageMode.fromCode(langCode),
                    frequency = freq,
                    isUserAdded = userAdded,
                )
            )
        }
        return validated
    }

    fun isValidSingleWord(word: String): Boolean {
        if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return false
        for (ch in word) {
            if (ch.isWhitespace() || Character.isISOControl(ch)) return false
        }
        return true
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val chars = passphrase.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, salt, iterations, KEY_BITS)
            val factory = SecretKeyFactory.getInstance(KDF_NAME)
            val secret = factory.generateSecret(spec).encoded
            spec.clearPassword()
            SecretKeySpec(secret, "AES")
        } finally {
            chars.fill('\u0000')
        }
    }

    private fun decodeStrictBase64(value: String): ByteArray {
        if (value.isBlank()) throw IllegalArgumentException("Missing encrypted field.")
        return try {
            Base64.decode(value, Base64.NO_WRAP)
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid Base64 encoding in dictionary file.")
        }
    }
}
