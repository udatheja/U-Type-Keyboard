package com.udara.Utype.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class BackupSummary(
    val shortcutsCount: Int,
    val learnedWordsCount: Int,
    val pinnedClipsCount: Int,
)

/**
 * Offline JSON backup & restore for settings, text shortcuts, learned words, and pinned clipboard items.
 * Exports to Downloads using zero-permission MediaStore (API 29+) or Storage Access Framework.
 */
class BackupManager(
    private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val db: UTypeDatabase,
    private val clipboardRepo: ClipboardRepository,
) {
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        val settings = settingsRepo.currentSettings()
        val shortcuts = db.textShortcutDao().getAll()
        val learnedWords = db.learnedWordDao().getAll()
        val pinnedClips = clipboardRepo.getPinnedItems()

        val root = JSONObject().apply {
            put("app", "U Type")
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())

            put("settings", JSONObject().apply {
                put("themeId", settings.themeId.id)
                put("keyHeightDp", settings.keyHeightDp.toDouble())
                put("keyHorizontalGapDp", settings.keyHorizontalGapDp.toDouble())
                put("keyVerticalGapDp", settings.keyVerticalGapDp.toDouble())
                put("keyCornerRadiusDp", settings.keyCornerRadiusDp.toDouble())
                put("bottomPaddingDp", settings.bottomPaddingDp.toDouble())
                put("showNumberRow", settings.showNumberRow)
                put("showHintCharacters", settings.showHintCharacters)
                put("showSinhalaKeyHints", settings.showSinhalaKeyHints)
                put("hapticFeedback", settings.hapticFeedback)
                put("keySound", settings.keySound)
                put("defaultLanguage", settings.defaultLanguage.code)
                put("autoCapitalize", settings.autoCapitalize)
                put("doubleSpacePeriod", settings.doubleSpacePeriod)
                put("suggestionsEnabled", settings.suggestionsEnabled)
                put("clipboardEnabled", settings.clipboardEnabled)
                put("clipboardDurationMinutes", settings.clipboardDuration.minutes)
                put("oneHandedMode", settings.oneHandedMode.id)
            })

            put("shortcuts", JSONArray().apply {
                shortcuts.forEach { s ->
                    put(JSONObject().apply {
                        put("shortCode", s.shortCode)
                        put("expansion", s.expansion)
                        put("createdAt", s.createdAt)
                    })
                }
            })

            put("learnedWords", JSONArray().apply {
                learnedWords.forEach { w ->
                    put(JSONObject().apply {
                        put("word", w.word)
                        put("language", w.language)
                        put("frequency", w.frequency)
                        put("lastUsedAt", w.lastUsedAt)
                    })
                }
            })

            put("pinnedClipboardItems", JSONArray().apply {
                pinnedClips.forEach { c ->
                    put(JSONObject().apply {
                        put("text", c.text)
                        put("createdAt", c.createdAt)
                    })
                }
            })
        }
        root.toString(2)
    }

    /**
     * Exports the JSON backup directly into the public Downloads directory (no storage permission needed on API 29+).
     * Returns the display path or filename on success.
     */
    suspend fun exportToDownloads(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildBackupJson()
            val fileName = "UType_Backup_${System.currentTimeMillis()}.json"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create Downloads entry")
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("Could not open Downloads output stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Downloads/$fileName"
            } else {
                // API 26-28 fallback to app-specific external Downloads (zero permission required)
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                val file = File(dir, fileName)
                file.writeText(json, Charsets.UTF_8)
                file.absolutePath
            }
        }
    }

    suspend fun exportToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            } ?: error("Cannot write to selected location")
        }
    }

    suspend fun importFromUri(uri: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonText = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Cannot read selected backup file")
            importFromJson(jsonText)
        }
    }

    suspend fun importFromJson(jsonText: String): BackupSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonText)

        root.optJSONObject("settings")?.let { s ->
            val current = settingsRepo.currentSettings()
            settingsRepo.saveAll(
                current.copy(
                    themeId = KeyboardThemeId.fromId(s.optString("themeId", current.themeId.id)),
                    keyHeightDp = s.optDouble("keyHeightDp", current.keyHeightDp.toDouble()).toFloat(),
                    keyHorizontalGapDp = s.optDouble("keyHorizontalGapDp", current.keyHorizontalGapDp.toDouble()).toFloat(),
                    keyVerticalGapDp = s.optDouble("keyVerticalGapDp", current.keyVerticalGapDp.toDouble()).toFloat(),
                    keyCornerRadiusDp = s.optDouble("keyCornerRadiusDp", current.keyCornerRadiusDp.toDouble()).toFloat(),
                    bottomPaddingDp = s.optDouble("bottomPaddingDp", current.bottomPaddingDp.toDouble()).toFloat(),
                    showNumberRow = s.optBoolean("showNumberRow", current.showNumberRow),
                    showHintCharacters = s.optBoolean("showHintCharacters", current.showHintCharacters),
                    showSinhalaKeyHints = s.optBoolean("showSinhalaKeyHints", current.showSinhalaKeyHints),
                    hapticFeedback = s.optBoolean("hapticFeedback", current.hapticFeedback),
                    keySound = s.optBoolean("keySound", current.keySound),
                    defaultLanguage = LanguageMode.fromCode(s.optString("defaultLanguage", current.defaultLanguage.code)),
                    autoCapitalize = s.optBoolean("autoCapitalize", current.autoCapitalize),
                    doubleSpacePeriod = s.optBoolean("doubleSpacePeriod", current.doubleSpacePeriod),
                    suggestionsEnabled = s.optBoolean("suggestionsEnabled", current.suggestionsEnabled),
                    clipboardEnabled = s.optBoolean("clipboardEnabled", current.clipboardEnabled),
                    clipboardDuration = ClipboardDuration.fromMinutes(
                        s.optInt("clipboardDurationMinutes", current.clipboardDuration.minutes)
                    ),
                    oneHandedMode = OneHandedMode.fromId(s.optString("oneHandedMode", current.oneHandedMode.id)),
                )
            )
        }

        var shortcutsCount = 0
        root.optJSONArray("shortcuts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val code = obj.optString("shortCode").trim().lowercase()
                val expansion = obj.optString("expansion").trim()
                if (code.isNotEmpty() && expansion.isNotEmpty()) {
                    db.textShortcutDao().upsert(
                        TextShortcutEntity(
                            shortCode = code,
                            expansion = expansion,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        )
                    )
                    shortcutsCount++
                }
            }
        }

        var learnedCount = 0
        root.optJSONArray("learnedWords")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val word = obj.optString("word").trim()
                val lang = obj.optString("language", "en")
                if (word.isNotEmpty()) {
                    db.learnedWordDao().upsert(
                        LearnedWordEntity(
                            word = word,
                            language = lang,
                            frequency = obj.optInt("frequency", 1),
                            lastUsedAt = obj.optLong("lastUsedAt", System.currentTimeMillis()),
                        )
                    )
                    learnedCount++
                }
            }
        }

        var pinnedCount = 0
        root.optJSONArray("pinnedClipboardItems")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val text = obj.optString("text")
                if (text.isNotBlank()) {
                    clipboardRepo.saveClip(text = text, isPinned = true)
                    pinnedCount++
                }
            }
        }

        BackupSummary(
            shortcutsCount = shortcutsCount,
            learnedWordsCount = learnedCount,
            pinnedClipsCount = pinnedCount,
        )
    }
}
