package com.udara.Utype.engine

import android.content.Context
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.LearnedWordDao
import com.udara.Utype.data.LearnedWordEntity
import com.udara.Utype.data.TextShortcutDao
import com.udara.Utype.data.TextShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

data class SuggestionItem(
    val text: String,
    val isShortcut: Boolean = false,
    val shortcutCode: String? = null,
)

/**
 * Fast offline suggestion & text-shortcut engine.
 * - Loads bundled frequency-ranked EN & SI wordlists off the main thread.
 * - Learns user words locally in Room (disabled in incognito & password fields).
 * - Resolves user-defined text shortcuts (e.g., "addr" -> full address).
 */
class SuggestionEngine(
    private val context: Context,
    private val learnedWordDao: LearnedWordDao,
    private val shortcutDao: TextShortcutDao,
) {
    private data class WordEntry(val word: String, val freq: Int)

    @Volatile
    private var enBaseWords: List<WordEntry> = emptyList()

    @Volatile
    private var siBaseWords: List<WordEntry> = emptyList()

    private val learnedEn = ConcurrentHashMap<String, Int>()
    private val learnedSi = ConcurrentHashMap<String, Int>()
    private val shortcuts = ConcurrentHashMap<String, String>()

    @Volatile
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            enBaseWords = loadAssetWordlist("wordlists/en_wordlist.txt")
            siBaseWords = loadAssetWordlist("wordlists/si_wordlist.txt")
            isInitialized = true
        }
        refreshDynamicData()
    }

    suspend fun refreshDynamicData() = withContext(Dispatchers.IO) {
        learnedEn.clear()
        learnedWordDao.getTopForLanguage(LanguageMode.ENGLISH.code).forEach {
            learnedEn[it.word] = it.frequency
        }
        learnedSi.clear()
        learnedWordDao.getTopForLanguage(LanguageMode.SINHALA.code).forEach {
            learnedSi[it.word] = it.frequency
        }
        shortcuts.clear()
        shortcutDao.getAll().forEach {
            shortcuts[it.shortCode.lowercase()] = it.expansion
        }
    }

    fun getShortcutExpansion(rawCode: String): String? {
        if (rawCode.isBlank()) return null
        return shortcuts[rawCode.trim().lowercase()]
    }

    /**
     * Returns up to 3 suggestions for the current composing word.
     * In Sinhala mode, [composingWord] is the live Sinhala transliteration and [rawLatin] is the typed Singlish.
     */
    fun suggest(
        composingWord: String,
        language: LanguageMode,
        rawLatin: String = composingWord,
        limit: Int = 3,
    ): List<SuggestionItem> {
        val result = ArrayList<SuggestionItem>(limit)
        val seen = HashSet<String>()

        // 1. Check exact text shortcut match first
        val shortcutKey = rawLatin.trim().lowercase()
        val expansion = if (shortcutKey.isNotEmpty()) shortcuts[shortcutKey] else null
        if (expansion != null) {
            result.add(SuggestionItem(text = expansion, isShortcut = true, shortcutCode = shortcutKey))
            seen.add(expansion.lowercase())
        }

        val query = composingWord.trim()
        if (query.isEmpty()) return result

        val lowerQuery = query.lowercase()
        val isCapitalized = language == LanguageMode.ENGLISH &&
            query.isNotEmpty() && query[0].isUpperCase()

        val candidates = ArrayList<Pair<String, Int>>()

        // 2. Learned words (prioritized boost)
        val learnedMap = if (language == LanguageMode.SINHALA) learnedSi else learnedEn
        for ((word, freq) in learnedMap) {
            val matches = if (language == LanguageMode.ENGLISH) {
                word.lowercase().startsWith(lowerQuery)
            } else {
                word.startsWith(query)
            }
            if (matches) {
                candidates.add(word to (freq * 100 + 20_000))
            }
        }

        // 3. Bundled frequency wordlist
        val baseList = if (language == LanguageMode.SINHALA) siBaseWords else enBaseWords
        for (entry in baseList) {
            val matches = if (language == LanguageMode.ENGLISH) {
                entry.word.lowercase().startsWith(lowerQuery)
            } else {
                entry.word.startsWith(query)
            }
            if (matches) {
                candidates.add(entry.word to entry.freq)
            }
        }

        candidates.sortByDescending { it.second }

        for ((word, _) in candidates) {
            if (result.size >= limit) break
            val displayWord = if (isCapitalized && word.isNotEmpty()) {
                word.replaceFirstChar { it.uppercaseChar() }
            } else {
                word
            }
            val key = displayWord.lowercase()
            if (seen.add(key)) {
                result.add(SuggestionItem(text = displayWord))
            }
        }

        // In Sinhala mode, if fewer than limit suggestions matched, ensure the live transliteration itself is offered
        if (language == LanguageMode.SINHALA && result.isEmpty() && query.isNotEmpty()) {
            result.add(SuggestionItem(text = query))
        }

        return result
    }

    suspend fun learnWord(word: String, language: LanguageMode) = withContext(Dispatchers.IO) {
        val cleaned = word.trim()
        if (cleaned.length < 2 || cleaned.length > 40) return@withContext
        if (cleaned.any { it.isDigit() || it.isWhitespace() }) return@withContext

        val normalized = if (language == LanguageMode.ENGLISH) cleaned.lowercase() else cleaned
        val existing = learnedWordDao.find(normalized, language.code)
        val nextFreq = (existing?.frequency ?: 0) + 1
        learnedWordDao.upsert(
            LearnedWordEntity(
                word = normalized,
                language = language.code,
                frequency = nextFreq,
                lastUsedAt = System.currentTimeMillis(),
            )
        )
        if (language == LanguageMode.SINHALA) {
            learnedSi[normalized] = nextFreq
        } else {
            learnedEn[normalized] = nextFreq
        }
    }

    suspend fun saveShortcut(shortCode: String, expansion: String) = withContext(Dispatchers.IO) {
        val key = shortCode.trim().lowercase()
        if (key.isEmpty() || expansion.isBlank()) return@withContext
        shortcutDao.upsert(TextShortcutEntity(shortCode = key, expansion = expansion.trim()))
        shortcuts[key] = expansion.trim()
    }

    suspend fun deleteShortcut(shortCode: String) = withContext(Dispatchers.IO) {
        val key = shortCode.trim().lowercase()
        shortcutDao.delete(key)
        shortcuts.remove(key)
    }

    suspend fun clearLearnedWords() = withContext(Dispatchers.IO) {
        learnedWordDao.clearAll()
        learnedEn.clear()
        learnedSi.clear()
    }

    private fun loadAssetWordlist(assetPath: String): List<WordEntry> {
        return try {
            val list = ArrayList<WordEntry>(512)
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            val sep = line.lastIndexOf(' ')
                            if (sep > 0) {
                                val word = line.substring(0, sep).trim()
                                val freq = line.substring(sep + 1).trim().toIntOrNull() ?: 1
                                if (word.isNotEmpty()) list.add(WordEntry(word, freq))
                            } else {
                                list.add(WordEntry(line, 1))
                            }
                        }
                    }
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
