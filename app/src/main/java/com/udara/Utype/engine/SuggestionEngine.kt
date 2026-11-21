package com.udara.Utype.engine

import android.content.Context
import com.udara.Utype.data.BlockedSuggestionDao
import com.udara.Utype.data.BlockedSuggestionEntity
import com.udara.Utype.data.ClipboardCrypto
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.LearnedWordDao
import com.udara.Utype.data.LearnedWordEntity
import com.udara.Utype.data.PassThroughClipboardCrypto
import com.udara.Utype.data.PersonalWordDao
import com.udara.Utype.data.PersonalWordEntity
import com.udara.Utype.data.TextShortcutDao
import com.udara.Utype.data.TextShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

data class SuggestionItem(
    val text: String,
    val displayText: String = text,
    val isShortcut: Boolean = false,
    val shortcutCode: String? = null,
    val isTypedLiteral: Boolean = false,
    val isCorrection: Boolean = false,
    val language: LanguageMode = LanguageMode.ENGLISH,
)

data class SuggestionOptions(
    val suggestionsEnabled: Boolean = true,
    val spellingCorrectionEnabled: Boolean = true,
    val blockOffensiveSuggestions: Boolean = true,
    val englishDictionaryEnabled: Boolean = true,
    val sinhalaDictionaryEnabled: Boolean = true,
)

data class PersonalWordItem(
    val word: String,
    val language: LanguageMode,
    val frequency: Int,
    val isUserAdded: Boolean,
    val lastUsedAt: Long,
)

/**
 * Fast, strictly offline smart-suggestion & spell-correction engine.
 *
 * Features:
 * - SymSpell (symmetric delete, edit distance <= 2, Damerau-Levenshtein) for English & Sinhala.
 * - O(log N) prefix completions (e.g., "recomm" -> "recommend").
 * - Offline Bigram context ranking (their/there/they're, to/too/two, your/you're, etc.).
 * - Sinhala Singlish phonetic fuzzy matching ("amma" -> "අම්මා", "kohomda" -> "කොහොමද").
 * - Encrypted personal dictionary at rest (Android Keystore AES-256-GCM) + hashed blocklist.
 * - Passphrase-encrypted export/import (PBKDF2WithHmacSHA256 + AES-256-GCM).
 * - Mixed-language script detection & strict 40-char word cap for DoS protection.
 */
class SuggestionEngine(
    private val context: Context,
    private val learnedWordDao: LearnedWordDao,
    private val shortcutDao: TextShortcutDao,
    private val personalWordDao: PersonalWordDao? = null,
    private val blockedSuggestionDao: BlockedSuggestionDao? = null,
    private val crypto: ClipboardCrypto = PassThroughClipboardCrypto(),
) {
    companion object {
        const val MAX_WORD_LENGTH = SymSpellIndex.MAX_WORD_LENGTH
        const val MAX_PERSONAL_WORDS = 5_000

        private val HOMOPHONE_GROUPS = listOf(
            setOf("their", "there", "they're"),
            setOf("to", "too", "two"),
            setOf("your", "you're"),
            setOf("its", "it's"),
            setOf("then", "than"),
            setOf("affect", "effect"),
            setOf("lose", "loose"),
            setOf("accept", "except"),
            setOf("weather", "whether"),
            setOf("hear", "here"),
        )

        private val HOMOPHONE_LOOKUP: Map<String, Set<String>> = buildMap {
            for (group in HOMOPHONE_GROUPS) {
                for (word in group) {
                    put(word, group)
                }
            }
        }

        /**
         * Detects whether [word] is Sinhala Unicode (`U+0D80..U+0DFF`) or Latin script.
         */
        fun detectScript(word: String, fallback: LanguageMode): LanguageMode {
            var hasSinhala = false
            var hasLatin = false
            for (ch in word) {
                when {
                    ch in '\u0D80'..'\u0DFF' -> hasSinhala = true
                    ch in 'a'..'z' || ch in 'A'..'Z' -> hasLatin = true
                }
            }
            return when {
                hasSinhala -> LanguageMode.SINHALA
                hasLatin -> LanguageMode.ENGLISH
                else -> fallback
            }
        }

        /**
         * Applies the capitalization pattern of [original] onto [candidate] (lowercase, Capitalized, ALL CAPS).
         */
        fun matchCapitalization(candidate: String, original: String, language: LanguageMode): String {
            if (language != LanguageMode.ENGLISH || candidate.isEmpty()) return candidate
            val letters = original.filter { it.isLetter() }
            val isAllCaps = letters.length >= 2 && letters.all { it.isUpperCase() }
            if (isAllCaps) return candidate.uppercase()

            val isCapitalized = original.isNotEmpty() && original[0].isUpperCase()
            if (isCapitalized) return candidate.replaceFirstChar { it.uppercaseChar() }

            if (candidate == "i") return "I"
            if (candidate.startsWith("i'")) return "I" + candidate.substring(1)
            return candidate
        }
    }

    private data class PersonalEntry(
        val displayWord: String,
        val frequency: Int,
        val isUserAdded: Boolean,
        val lastUsedAt: Long,
    )

    private val enIndex = SymSpellIndex(ignoreCase = true, maxEditDistance = 2, prefixLength = 7)
    private val siIndex = SymSpellIndex(ignoreCase = false, maxEditDistance = 2, prefixLength = 7)
    private val enBigrams = BigramModel(ignoreCase = true)
    private val siBigrams = BigramModel(ignoreCase = false)

    @Volatile
    private var offensiveWords: Set<String> = emptySet()

    private val personalEn = ConcurrentHashMap<String, PersonalEntry>()
    private val personalSi = ConcurrentHashMap<String, PersonalEntry>()
    private val blockedHashes = ConcurrentHashMap.newKeySet<String>()
    private val shortcuts = ConcurrentHashMap<String, String>()

    @Volatile
    private var isInitialized = false

    private val initLock = Any()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            val enWords = loadWordlistAsset("wordlists/en_wordlist.txt")
            val siWords = loadWordlistAsset("wordlists/si_wordlist.txt")
            val enBi = loadBigramAsset("wordlists/bigrams_en.txt")
            val siBi = loadBigramAsset("wordlists/bigrams_si.txt")
            val offensive = loadSimpleListAsset("wordlists/offensive_en.txt")

            synchronized(initLock) {
                if (!isInitialized) {
                    enIndex.build(enWords)
                    siIndex.build(siWords)
                    enBigrams.load(enBi)
                    siBigrams.load(siBi)
                    offensiveWords = offensive
                    isInitialized = true
                }
            }
        }
        refreshDynamicData()
    }

    suspend fun refreshDynamicData() = withContext(Dispatchers.IO) {
        personalEn.clear()
        personalSi.clear()

        if (personalWordDao != null) {
            // One-time transparent migration from legacy plaintext learned_words -> encrypted personal_words
            val legacyRows = learnedWordDao.getAll()
            if (legacyRows.isNotEmpty()) {
                for (row in legacyRows) {
                    val lang = LanguageMode.fromCode(row.language)
                    val norm = normalizeWord(row.word, lang)
                    if (isValidWord(norm)) {
                        val hash = wordHash(norm, lang)
                        val enc = crypto.encrypt(row.word.trim())
                        personalWordDao.upsert(
                            PersonalWordEntity(
                                wordHash = hash,
                                encryptedWord = enc.ciphertext,
                                iv = enc.iv,
                                language = lang.code,
                                frequency = row.frequency.coerceAtLeast(1),
                                isUserAdded = false,
                                lastUsedAt = row.lastUsedAt,
                            )
                        )
                    }
                }
                learnedWordDao.clearAll()
            }

            personalWordDao.getAll().forEach { entity ->
                val plain = try {
                    crypto.decrypt(entity.encryptedWord, entity.iv).trim()
                } catch (_: Exception) {
                    null
                }
                if (!plain.isNullOrEmpty() && plain.length <= MAX_WORD_LENGTH) {
                    val lang = LanguageMode.fromCode(entity.language)
                    val key = normalizeWord(plain, lang)
                    val entry = PersonalEntry(plain, entity.frequency, entity.isUserAdded, entity.lastUsedAt)
                    if (lang == LanguageMode.SINHALA) personalSi[key] = entry else personalEn[key] = entry
                }
            }
        } else {
            learnedWordDao.getAll().forEach { row ->
                val lang = LanguageMode.fromCode(row.language)
                val key = normalizeWord(row.word, lang)
                val entry = PersonalEntry(row.word, row.frequency, false, row.lastUsedAt)
                if (lang == LanguageMode.SINHALA) personalSi[key] = entry else personalEn[key] = entry
            }
        }

        blockedHashes.clear()
        blockedSuggestionDao?.getAll()?.forEach { blockedHashes.add(it.wordHash) }

        shortcuts.clear()
        shortcutDao.getAll().forEach {
            shortcuts[it.shortCode.lowercase()] = it.expansion
        }
    }

    fun getShortcutExpansion(rawCode: String): String? {
        if (rawCode.isBlank() || rawCode.length > MAX_WORD_LENGTH) return null
        return shortcuts[rawCode.trim().lowercase()]
    }

    /**
     * Live typing suggestions (Section 3A): returns up to [limit] (default 5) candidates.
     * Candidate 0 is the exact typed word shown in quotes (e.g., `"recomm"`), unless an exact
     * text shortcut matches first, followed by best completions & spelling corrections.
     */
    fun suggest(
        composingWord: String,
        language: LanguageMode,
        rawLatin: String = composingWord,
        previousWord: String? = null,
        limit: Int = 5,
        options: SuggestionOptions = SuggestionOptions(),
    ): List<SuggestionItem> {
        if (!options.suggestionsEnabled || limit <= 0) return emptyList()

        val result = ArrayList<SuggestionItem>(limit)
        val seen = HashSet<String>()

        // 1. Exact text shortcut match
        val shortcutKey = rawLatin.trim().lowercase()
        val expansion = if (shortcutKey.isNotEmpty() && shortcutKey.length <= MAX_WORD_LENGTH) {
            shortcuts[shortcutKey]
        } else null
        if (expansion != null) {
            result.add(
                SuggestionItem(
                    text = expansion,
                    displayText = "⚡ $expansion",
                    isShortcut = true,
                    shortcutCode = shortcutKey,
                    language = language,
                )
            )
            seen.add(expansion.lowercase())
        }

        val query = composingWord.trim()
        if (query.isEmpty() || query.length > MAX_WORD_LENGTH) return result
        if (query.any { it.isWhitespace() || Character.isISOControl(it) }) return result

        val effectiveLang = detectScript(query, language)

        // 2. Typed word shown first in quotes (Section 3A)
        if (result.size < limit) {
            result.add(
                SuggestionItem(
                    text = query,
                    displayText = "\"$query\"",
                    isTypedLiteral = true,
                    language = effectiveLang,
                )
            )
            seen.add(normalizeWord(query, effectiveLang))
        }

        val scored = collectRankedCandidates(
            query = query,
            effectiveLang = effectiveLang,
            rawLatin = rawLatin,
            previousWord = previousWord,
            includeCompletions = true,
            options = options,
        )

        for (cand in scored) {
            if (result.size >= limit) break
            val display = matchCapitalization(cand.word, query, effectiveLang)
            val dedupeKey = normalizeWord(display, effectiveLang)
            if (seen.add(dedupeKey)) {
                result.add(
                    SuggestionItem(
                        text = display,
                        displayText = display,
                        isCorrection = cand.editDistance > 0,
                        language = effectiveLang,
                    )
                )
            }
        }

        return result
    }

    /**
     * Cursor-word spell correction (Section 3B): given an already-typed word under/beside the cursor,
     * returns up to [limit] corrections preserving capitalization. If the word is already valid,
     * returns high-confidence context alternatives (e.g. their/there) or an empty list.
     */
    fun suggestForExistingWord(
        word: String,
        previousWord: String? = null,
        fallbackLanguage: LanguageMode = LanguageMode.ENGLISH,
        limit: Int = 5,
        options: SuggestionOptions = SuggestionOptions(),
    ): List<SuggestionItem> {
        if (!options.suggestionsEnabled || !options.spellingCorrectionEnabled || limit <= 0) {
            return emptyList()
        }
        val cleaned = word.trim()
        if (cleaned.length < 2 || cleaned.length > MAX_WORD_LENGTH) return emptyList()
        if (cleaned.any { it.isDigit() || it.isWhitespace() || Character.isISOControl(it) }) return emptyList()

        val effectiveLang = detectScript(cleaned, fallbackLanguage)
        if (!isDictionaryEnabled(effectiveLang, options)) return emptyList()

        val norm = normalizeWord(cleaned, effectiveLang)
        val alreadyKnown = isKnownWord(norm, effectiveLang, options)

        if (alreadyKnown) {
            // Only offer strong context-confused homophone alternatives when previous word favors another spelling
            if (effectiveLang == LanguageMode.ENGLISH && !previousWord.isNullOrBlank()) {
                val peers = HOMOPHONE_LOOKUP[norm] ?: return emptyList()
                val currentBigram = enBigrams.bonus(previousWord, norm)
                val alternatives = peers
                    .filter { it != norm && !isSuppressed(it, LanguageMode.ENGLISH, options) }
                    .map { it to enBigrams.bonus(previousWord, it) }
                    .filter { (_, score) -> score > currentBigram && score >= 500 }
                    .sortedByDescending { it.second }
                    .take(limit)
                    .map { (alt, _) ->
                        val formatted = matchCapitalization(alt, cleaned, LanguageMode.ENGLISH)
                        SuggestionItem(
                            text = formatted,
                            displayText = formatted,
                            isCorrection = true,
                            language = LanguageMode.ENGLISH,
                        )
                    }
                return alternatives
            }
            return emptyList()
        }

        val scored = collectRankedCandidates(
            query = cleaned,
            effectiveLang = effectiveLang,
            rawLatin = cleaned,
            previousWord = previousWord,
            includeCompletions = false,
            options = options,
        )

        val result = ArrayList<SuggestionItem>(limit)
        val seen = HashSet<String>()
        seen.add(norm)
        for (cand in scored) {
            if (result.size >= limit) break
            val formatted = matchCapitalization(cand.word, cleaned, effectiveLang)
            val key = normalizeWord(formatted, effectiveLang)
            if (seen.add(key)) {
                result.add(
                    SuggestionItem(
                        text = formatted,
                        displayText = formatted,
                        isCorrection = true,
                        language = effectiveLang,
                    )
                )
            }
        }
        return result
    }

    /**
     * Returns the single best auto-correction candidate when Space is pressed (Section 3A).
     * Only corrects misspelled words (never known words, short 1-char tokens, or blocked/offensive words).
     */
    fun bestAutoCorrectCandidate(
        typedWord: String,
        language: LanguageMode,
        rawLatin: String = typedWord,
        previousWord: String? = null,
        options: SuggestionOptions = SuggestionOptions(),
    ): String? {
        if (!options.spellingCorrectionEnabled) return null
        val cleaned = typedWord.trim()
        if (cleaned.length < 2 || cleaned.length > MAX_WORD_LENGTH) return null
        if (cleaned.any { it.isDigit() || it.isWhitespace() || Character.isISOControl(it) }) return null

        val effectiveLang = detectScript(cleaned, language)
        if (!isDictionaryEnabled(effectiveLang, options)) return null

        val norm = normalizeWord(cleaned, effectiveLang)
        if (isKnownWord(norm, effectiveLang, options)) return null

        val ranked = collectRankedCandidates(
            query = cleaned,
            effectiveLang = effectiveLang,
            rawLatin = rawLatin,
            previousWord = previousWord,
            includeCompletions = false,
            options = options,
        )
        val best = ranked.firstOrNull() ?: return null

        // High-confidence gate: allow edit distance 1 always; allow edit distance 2 for words >= 5 chars
        val confident = best.editDistance == 1 || (best.editDistance == 2 && cleaned.length >= 5)
        if (!confident) return null

        val formatted = matchCapitalization(best.word, cleaned, effectiveLang)
        return if (formatted == cleaned) null else formatted
    }

    suspend fun learnWord(word: String, language: LanguageMode) = withContext(Dispatchers.IO) {
        upsertPersonalWordInternal(word = word, language = language, isUserAdded = false)
    }

    suspend fun addPersonalWord(word: String, language: LanguageMode): Boolean = withContext(Dispatchers.IO) {
        val cleaned = word.trim()
        if (!isValidWord(cleaned)) return@withContext false
        val effectiveLang = detectScript(cleaned, language)
        // If user explicitly adds a word, remove it from the blocked list
        val norm = normalizeWord(cleaned, effectiveLang)
        val hash = wordHash(norm, effectiveLang)
        blockedHashes.remove(hash)
        blockedSuggestionDao?.deleteByHash(hash)
        upsertPersonalWordInternal(word = cleaned, language = effectiveLang, isUserAdded = true)
        true
    }

    suspend fun removePersonalWord(word: String, language: LanguageMode) = withContext(Dispatchers.IO) {
        val norm = normalizeWord(word.trim(), language)
        if (norm.isEmpty()) return@withContext
        val hash = wordHash(norm, language)
        if (language == LanguageMode.SINHALA) personalSi.remove(norm) else personalEn.remove(norm)
        personalWordDao?.deleteByHash(hash)
    }

    suspend fun getPersonalWords(): List<PersonalWordItem> = withContext(Dispatchers.IO) {
        val list = ArrayList<PersonalWordItem>(personalEn.size + personalSi.size)
        personalEn.values.forEach {
            list.add(PersonalWordItem(it.displayWord, LanguageMode.ENGLISH, it.frequency, it.isUserAdded, it.lastUsedAt))
        }
        personalSi.values.forEach {
            list.add(PersonalWordItem(it.displayWord, LanguageMode.SINHALA, it.frequency, it.isUserAdded, it.lastUsedAt))
        }
        list.sortWith(compareByDescending<PersonalWordItem> { it.isUserAdded }.thenByDescending { it.frequency })
        list
    }

    suspend fun blockSuggestion(word: String, language: LanguageMode) {
        val cleaned = word.trim()
        if (cleaned.isEmpty() || cleaned.length > MAX_WORD_LENGTH) return
        val effectiveLang = detectScript(cleaned, language)
        val norm = normalizeWord(cleaned, effectiveLang)
        val hash = wordHash(norm, effectiveLang)
        blockedHashes.add(hash)
        val map = if (effectiveLang == LanguageMode.SINHALA) personalSi else personalEn
        val removeAutoLearned = map[norm]?.isUserAdded == false
        if (removeAutoLearned) {
            map.remove(norm)
        }
        withContext(Dispatchers.IO) {
            blockedSuggestionDao?.upsert(BlockedSuggestionEntity(wordHash = hash, language = effectiveLang.code))
            if (removeAutoLearned) {
                personalWordDao?.deleteByHash(hash)
            }
        }
    }

    suspend fun clearLearnedWords() = withContext(Dispatchers.IO) {
        learnedWordDao.clearAll()
        personalWordDao?.clearAll()
        personalEn.clear()
        personalSi.clear()
    }

    suspend fun clearBlockedSuggestions() = withContext(Dispatchers.IO) {
        blockedSuggestionDao?.clearAll()
        blockedHashes.clear()
    }

    suspend fun exportEncryptedDictionary(passphrase: String): String = withContext(Dispatchers.IO) {
        val entries = getPersonalWords().map {
            DictionaryExportEntry(
                word = it.word,
                language = it.language,
                frequency = it.frequency,
                isUserAdded = it.isUserAdded,
            )
        }
        DictionaryCryptoExporter.encryptDictionary(entries, passphrase)
    }

    suspend fun importEncryptedDictionary(encryptedJson: String, passphrase: String): Int = withContext(Dispatchers.IO) {
        val entries = DictionaryCryptoExporter.decryptAndValidateDictionary(encryptedJson, passphrase)
        var imported = 0
        for (entry in entries) {
            upsertPersonalWordInternal(
                word = entry.word,
                language = entry.language,
                isUserAdded = entry.isUserAdded,
                explicitFrequency = entry.frequency,
            )
            imported++
        }
        imported
    }

    suspend fun saveShortcut(shortCode: String, expansion: String) = withContext(Dispatchers.IO) {
        val key = shortCode.trim().lowercase()
        if (key.isEmpty() || key.length > MAX_WORD_LENGTH || expansion.isBlank()) return@withContext
        shortcutDao.upsert(TextShortcutEntity(shortCode = key, expansion = expansion.trim()))
        shortcuts[key] = expansion.trim()
    }

    suspend fun deleteShortcut(shortCode: String) = withContext(Dispatchers.IO) {
        val key = shortCode.trim().lowercase()
        shortcutDao.delete(key)
        shortcuts.remove(key)
    }

    private suspend fun upsertPersonalWordInternal(
        word: String,
        language: LanguageMode,
        isUserAdded: Boolean,
        explicitFrequency: Int? = null,
    ) {
        val cleaned = word.trim()
        if (!isValidWord(cleaned)) return
        if (!isUserAdded && cleaned.length < 2) return
        if (!isUserAdded && cleaned.any { it.isDigit() }) return

        val norm = normalizeWord(cleaned, language)
        val map = if (language == LanguageMode.SINHALA) personalSi else personalEn
        val existing = map[norm]
        val nextFreq = explicitFrequency ?: ((existing?.frequency ?: 0) + 1)
        val now = System.currentTimeMillis()
        val display = if (isUserAdded) cleaned else (existing?.displayWord ?: cleaned)
        val userAddedFlag = isUserAdded || (existing?.isUserAdded == true)

        map[norm] = PersonalEntry(display, nextFreq, userAddedFlag, now)

        if (personalWordDao != null) {
            val hash = wordHash(norm, language)
            val enc = crypto.encrypt(display)
            personalWordDao.upsert(
                PersonalWordEntity(
                    wordHash = hash,
                    encryptedWord = enc.ciphertext,
                    iv = enc.iv,
                    language = language.code,
                    frequency = nextFreq,
                    isUserAdded = userAddedFlag,
                    lastUsedAt = now,
                )
            )
            // Enforce hard size cap (evict oldest auto-learned entries)
            val total = personalWordDao.countAll()
            if (total > MAX_PERSONAL_WORDS) {
                val excess = total - MAX_PERSONAL_WORDS
                val oldest = personalWordDao.oldestAutoLearnedHashes(excess)
                if (oldest.isNotEmpty()) {
                    personalWordDao.deleteByHashes(oldest)
                }
            }
        } else {
            learnedWordDao.upsert(
                LearnedWordEntity(
                    word = norm,
                    language = language.code,
                    frequency = nextFreq,
                    lastUsedAt = now,
                )
            )
        }
    }

    private data class RankedCandidate(
        val word: String,
        val editDistance: Int,
        val isCompletion: Boolean,
        val score: Long,
    )

    private fun collectRankedCandidates(
        query: String,
        effectiveLang: LanguageMode,
        rawLatin: String,
        previousWord: String?,
        includeCompletions: Boolean,
        options: SuggestionOptions,
    ): List<RankedCandidate> {
        val normQuery = normalizeWord(query, effectiveLang)
        val bestByNorm = HashMap<String, RankedCandidate>(32)

        val dictEnabled = isDictionaryEnabled(effectiveLang, options)
        val index = if (effectiveLang == LanguageMode.SINHALA) siIndex else enIndex
        val bigrams = if (effectiveLang == LanguageMode.SINHALA) siBigrams else enBigrams
        val personalMap = if (effectiveLang == LanguageMode.SINHALA) personalSi else personalEn

        fun consider(word: String, editDist: Int, isCompletion: Boolean, baseFreq: Int, personalBoost: Long) {
            val norm = normalizeWord(word, effectiveLang)
            if (norm.isEmpty() || norm == normQuery) return
            if (isSuppressed(norm, effectiveLang, options)) return

            val biBonus = bigrams.bonus(previousWord, norm).toLong()
            // Ranking formula: prefix completions and distance-1 corrections top-tier, then bigram + personal + frequency
            val tierScore = when {
                isCompletion -> 2_400_000L
                editDist == 1 -> 2_200_000L
                editDist == 2 -> 1_100_000L
                else -> 500_000L
            }
            val score = tierScore + (biBonus * 800L) + personalBoost + baseFreq.toLong()
            val candidate = RankedCandidate(word, editDist, isCompletion, score)
            val prev = bestByNorm[norm]
            if (prev == null || candidate.score > prev.score) {
                bestByNorm[norm] = candidate
            }
        }

        // 1. Personal dictionary (completions + edit distance <= 2)
        for ((pKey, entry) in personalMap) {
            val pBoost = (entry.frequency.coerceAtMost(500) * 600L) + if (entry.isUserAdded) 150_000L else 50_000L
            if (includeCompletions && pKey.startsWith(normQuery) && pKey.length > normQuery.length) {
                consider(entry.displayWord, 0, true, entry.frequency * 50, pBoost)
            } else if (options.spellingCorrectionEnabled) {
                val d = damerauLevenshtein(normQuery, pKey, 2)
                if (d in 1..2) {
                    consider(entry.displayWord, d, false, entry.frequency * 50, pBoost)
                }
            }
        }

        // 2. Bundled SymSpell dictionary
        if (dictEnabled) {
            if (includeCompletions) {
                for (c in index.lookupPrefixCompletions(normQuery, maxResults = 12)) {
                    consider(c.word, 0, true, c.frequency, 0L)
                }
            }
            if (options.spellingCorrectionEnabled) {
                val maxDist = if (normQuery.length <= 2) 1 else 2
                for (c in index.lookupCorrections(normQuery, effectiveMaxDistance = maxDist, maxResults = 20)) {
                    if (c.editDistance > 0) {
                        consider(c.word, c.editDistance, false, c.frequency, 0L)
                    }
                }
            }

            // 3. Sinhala phonetic Singlish fuzzy matcher ("amma" -> "අම්මා", "kohomda" -> "කොහොමද")
            if (effectiveLang == LanguageMode.SINHALA && options.spellingCorrectionEnabled && rawLatin.isNotBlank()) {
                val fuzzySinhala = SinhalaFuzzyMatcher.generateSinhalaVariants(rawLatin)
                for (variant in fuzzySinhala) {
                    val personalMatch = personalSi[variant]
                    if (personalMatch != null) {
                        consider(personalMatch.displayWord, 1, false, personalMatch.frequency * 100 + 25_000, 120_000L)
                    }
                    val dictFreq = siIndex.frequencyOf(variant)
                    if (dictFreq > 0) {
                        consider(variant, 1, false, dictFreq + 15_000, 0L)
                    }
                }
            }

            // 4. English context-confused homophones when previousWord gives strong signal
            if (effectiveLang == LanguageMode.ENGLISH && !previousWord.isNullOrBlank()) {
                val peers = HOMOPHONE_LOOKUP[normQuery]
                if (peers != null) {
                    for (peer in peers) {
                        if (peer != normQuery) {
                            val bi = enBigrams.bonus(previousWord, peer)
                            if (bi >= 500) {
                                consider(peer, 1, false, enIndex.frequencyOf(peer), 0L)
                            }
                        }
                    }
                }
            }
        }

        return bestByNorm.values.sortedWith(
            compareByDescending<RankedCandidate> { it.score }
                .thenBy { it.editDistance }
                .thenBy { it.word.length }
        )
    }

    private fun isKnownWord(normWord: String, lang: LanguageMode, options: SuggestionOptions): Boolean {
        val personalMap = if (lang == LanguageMode.SINHALA) personalSi else personalEn
        if (personalMap.containsKey(normWord)) return true
        if (!isDictionaryEnabled(lang, options)) return false
        val index = if (lang == LanguageMode.SINHALA) siIndex else enIndex
        return index.isKnownWord(normWord)
    }

    private fun isDictionaryEnabled(lang: LanguageMode, options: SuggestionOptions): Boolean =
        if (lang == LanguageMode.SINHALA) options.sinhalaDictionaryEnabled else options.englishDictionaryEnabled

    private fun isSuppressed(normWord: String, lang: LanguageMode, options: SuggestionOptions): Boolean {
        if (blockedHashes.contains(wordHash(normWord, lang))) return true
        if (options.blockOffensiveSuggestions && lang == LanguageMode.ENGLISH && offensiveWords.contains(normWord)) {
            return true
        }
        return false
    }

    private fun normalizeWord(word: String, lang: LanguageMode): String =
        if (lang == LanguageMode.ENGLISH) word.lowercase() else word

    private fun wordHash(normWord: String, lang: LanguageMode): String =
        crypto.hash("${lang.code}:$normWord")

    private fun isValidWord(word: String): Boolean =
        DictionaryCryptoExporter.isValidSingleWord(word)

    private fun loadWordlistAsset(assetPath: String): List<Pair<String, Int>> {
        return try {
            val list = ArrayList<Pair<String, Int>>(2048)
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            val sep = line.lastIndexOf(' ')
                            if (sep > 0) {
                                val w = line.substring(0, sep).trim()
                                val f = line.substring(sep + 1).trim().toIntOrNull() ?: 1
                                if (w.isNotEmpty() && w.length <= MAX_WORD_LENGTH) list.add(w to f)
                            } else if (line.length <= MAX_WORD_LENGTH) {
                                list.add(line to 1)
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

    private fun loadBigramAsset(assetPath: String): List<Triple<String, String, Int>> {
        return try {
            val list = ArrayList<Triple<String, String, Int>>(512)
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            val parts = line.split(' ')
                            if (parts.size >= 3) {
                                val score = parts[2].toIntOrNull() ?: 100
                                list.add(Triple(parts[0], parts[1], score))
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

    private fun loadSimpleListAsset(assetPath: String): Set<String> {
        return try {
            val set = HashSet<String>(64)
            context.assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { rawLine ->
                        val line = rawLine.trim().lowercase()
                        if (line.isNotEmpty() && !line.startsWith("#")) {
                            set.add(line)
                        }
                    }
                }
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }
}
