package com.udara.Utype.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.udara.Utype.data.AndroidKeystoreClipboardCrypto
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.UTypeDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive test suite (38+ test cases) for the offline SymSpell, Bigram, Sinhala Phonetic
 * Fuzzy, Encrypted Personal Dictionary, Blocklist, Offensive Filter, and Passphrase-Encrypted
 * Export/Import engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartSuggestionsEngineTest {

    private lateinit var context: Context
    private lateinit var db: UTypeDatabase
    private lateinit var crypto: AndroidKeystoreClipboardCrypto
    private lateinit var engine: SuggestionEngine

    @Before
    fun setup() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UTypeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        crypto = AndroidKeystoreClipboardCrypto()
        engine = SuggestionEngine(
            context = context,
            learnedWordDao = db.learnedWordDao(),
            shortcutDao = db.textShortcutDao(),
            personalWordDao = db.personalWordDao(),
            blockedSuggestionDao = db.blockedSuggestionDao(),
            crypto = crypto,
        )
        engine.initialize()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- 1. Core Misspellings (SymSpell edit distance 1 & 2) ---

    @Test fun misspelling_recieve_suggests_receive() {
        val items = engine.suggest("recieve", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "receive" })
        assertEquals("receive", engine.bestAutoCorrectCandidate("recieve", LanguageMode.ENGLISH))
    }

    @Test fun misspelling_definately_suggests_definitely() {
        val items = engine.suggest("definately", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "definitely" })
        assertEquals("definitely", engine.bestAutoCorrectCandidate("definately", LanguageMode.ENGLISH))
    }

    @Test fun misspelling_seperate_suggests_separate() {
        val items = engine.suggest("seperate", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "separate" })
        assertEquals("separate", engine.bestAutoCorrectCandidate("seperate", LanguageMode.ENGLISH))
    }

    @Test fun misspelling_occured_suggests_occurred() {
        val items = engine.suggest("occured", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "occurred" })
        assertEquals("occurred", engine.bestAutoCorrectCandidate("occured", LanguageMode.ENGLISH))
    }

    @Test fun misspelling_accomodate_suggests_accommodate() {
        val items = engine.suggest("accomodate", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "accommodate" })
    }

    @Test fun misspelling_neccessary_suggests_necessary() {
        val items = engine.suggest("neccessary", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "necessary" })
    }

    @Test fun misspelling_tomorow_suggests_tomorrow() {
        val items = engine.suggest("tomorow", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "tomorrow" })
    }

    @Test fun misspelling_enviroment_suggests_environment() {
        val items = engine.suggest("enviroment", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "environment" })
    }

    @Test fun misspelling_goverment_suggests_government() {
        val items = engine.suggest("goverment", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "government" })
    }

    @Test fun misspelling_resturant_suggests_restaurant() {
        val items = engine.suggest("resturant", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "restaurant" })
    }

    // --- 2. Transposed (swapped), missing, extra, and wrong letters ---

    @Test fun transposed_teh_suggests_the() {
        val items = engine.suggest("teh", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "the" })
    }

    @Test fun transposed_freind_suggests_friend() {
        val items = engine.suggest("freind", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "friend" })
    }

    @Test fun transposed_wierd_suggests_weird() {
        val items = engine.suggest("wierd", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "weird" })
    }

    @Test fun transposed_taht_suggests_that() {
        val items = engine.suggest("taht", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "that" })
    }

    @Test fun missingLetter_becuse_suggests_because() {
        val items = engine.suggest("becuse", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "because" })
    }

    @Test fun extraLetter_helllo_suggests_hello() {
        val items = engine.suggest("helllo", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "hello" })
    }

    @Test fun twoEdits_acomodate_suggests_accommodate() {
        assertEquals(2, damerauLevenshtein("acomodate", "accommodate", 2))
        val items = engine.suggest("acomodate", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "accommodate" })
    }

    // --- 3. Prefix completions & up-to-5 quoted candidate format ---

    @Test fun prefixCompletion_recomm_suggests_recommend_withQuotedFirstCandidate() {
        val items = engine.suggest("recomm", LanguageMode.ENGLISH)
        assertTrue("Expected up to 5 suggestions", items.size in 2..5)
        assertTrue("First candidate must be typed literal", items.first().isTypedLiteral)
        assertEquals("\"recomm\"", items.first().displayText)
        assertEquals("recomm", items.first().text)
        assertTrue("Expected 'recommend' completion", items.any { it.text == "recommend" })
    }

    @Test fun prefixCompletion_congrat_suggests_congratulations() {
        val items = engine.suggest("congrat", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "congratulations" })
    }

    @Test fun prefixCompletion_keybo_suggests_keyboard() {
        val items = engine.suggest("keybo", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text == "keyboard" })
    }

    @Test fun liveSuggestions_neverExceedFiveCandidates() {
        val items = engine.suggest("th", LanguageMode.ENGLISH)
        assertTrue(items.size <= 5)
    }

    // --- 4. Bigram Context Ranking ---

    @Test fun bigramContext_in_prefers_their_over_there() {
        val items = engine.suggestForExistingWord(word = "thier", previousWord = "in")
        assertTrue(items.isNotEmpty())
        assertEquals("their", items.first().text)
    }

    @Test fun bigramContext_over_prefers_there_over_their() {
        val items = engine.suggestForExistingWord(word = "ther", previousWord = "over")
        assertTrue(items.isNotEmpty())
        assertEquals("there", items.first().text)
    }

    @Test fun bigramContext_want_suggests_to_whenUserTyped_too() {
        val items = engine.suggestForExistingWord(word = "too", previousWord = "want")
        assertTrue(items.any { it.text == "to" })
    }

    @Test fun bigramContext_me_suggests_too_whenUserTyped_to() {
        val items = engine.suggestForExistingWord(word = "to", previousWord = "me")
        assertTrue(items.any { it.text == "too" })
    }

    @Test fun bigramContext_better_suggests_than_whenUserTyped_then() {
        val items = engine.suggestForExistingWord(word = "then", previousWord = "better")
        assertTrue(items.any { it.text == "than" })
    }

    @Test fun bigramContext_side_suggests_effect_whenUserTyped_affect() {
        val items = engine.suggestForExistingWord(word = "affect", previousWord = "side")
        assertTrue(items.any { it.text == "effect" })
    }

    // --- 5. Capitalization Preservation ---

    @Test fun capitalizationPreserved_forTitleCaseAndAllCapsCorrections() {
        val title = engine.suggestForExistingWord("Recieve")
        assertEquals("Receive", title.first().text)

        val caps = engine.suggestForExistingWord("RECIEVE")
        assertEquals("RECEIVE", caps.first().text)

        val compTitle = engine.suggest("Recomm", LanguageMode.ENGLISH)
        assertTrue(compTitle.any { it.text == "Recommend" })

        val compCaps = engine.suggest("RECOMM", LanguageMode.ENGLISH)
        assertTrue(compCaps.any { it.text == "RECOMMEND" })
    }

    // --- 6. Sinhala Dictionary, Phonetic Singlish Fuzzy & Bigrams ---

    @Test fun sinhalaPhoneticFuzzy_amma_suggests_ammaa() {
        val liveSinhala = SinhalaTransliterator.transliterate("amma") // "අම්ම"
        val items = engine.suggest(liveSinhala, LanguageMode.SINHALA, rawLatin = "amma")
        assertTrue("Expected අම්මා for 'amma', got ${items.map { it.text }}", items.any { it.text == "අම්මා" })
    }

    @Test fun sinhalaPhoneticFuzzy_oya_suggests_oyaa() {
        val liveSinhala = SinhalaTransliterator.transliterate("oya") // "ඔය"
        val items = engine.suggest(liveSinhala, LanguageMode.SINHALA, rawLatin = "oya")
        assertTrue("Expected ඔයා for 'oya', got ${items.map { it.text }}", items.any { it.text == "ඔයා" })
    }

    @Test fun sinhalaPhoneticFuzzy_kohomda_suggests_kohomada() {
        val liveSinhala = SinhalaTransliterator.transliterate("kohomda")
        val items = engine.suggest(liveSinhala, LanguageMode.SINHALA, rawLatin = "kohomda")
        assertTrue("Expected කොහොමද for 'kohomda', got ${items.map { it.text }}", items.any { it.text == "කොහොමද" })
    }

    @Test fun sinhalaDirectUnicodeSymSpell_fixesTypoInExistingSinhalaWord() {
        val fixes = engine.suggestForExistingWord("කොහොමඩ", fallbackLanguage = LanguageMode.SINHALA)
        assertTrue("Expected කොහොමද fix, got ${fixes.map { it.text }}", fixes.any { it.text == "කොහොමද" })
    }

    @Test fun sinhalaPrefixCompletion_andBigramBoostWork() {
        val items = engine.suggest("උදෑස", LanguageMode.SINHALA, rawLatin = "udAAs", previousWord = "සුභ")
        assertTrue(items.any { it.text == "උදෑසනක්" })
    }

    // --- 7. Mixed-Language Script Detection ---

    @Test fun mixedScriptDetection_picksMatchingDictionaryAutomatically() {
        assertEquals(LanguageMode.SINHALA, SuggestionEngine.detectScript("අම්මා", LanguageMode.ENGLISH))
        assertEquals(LanguageMode.ENGLISH, SuggestionEngine.detectScript("recieve", LanguageMode.SINHALA))

        // Fixing an English misspelled word while keyboard is in Sinhala mode
        val enFix = engine.suggestForExistingWord("recieve", fallbackLanguage = LanguageMode.SINHALA)
        assertEquals("receive", enFix.first().text)

        // Fixing a Sinhala misspelled word while keyboard is in English mode
        val siFix = engine.suggestForExistingWord("කොහොමඩ", fallbackLanguage = LanguageMode.ENGLISH)
        assertTrue(siFix.any { it.text == "කොහොමද" })
    }

    // --- 8. Encrypted Personal Dictionary, Ranking Boost & Blocklist ---

    @Test fun personalDictionary_isEncryptedAtRestAndBoostsRanking() = runTest {
        assertTrue(engine.addPersonalWord("KandyTech", LanguageMode.ENGLISH))

        // Verify stored Room row is NOT plaintext
        val storedRows = db.personalWordDao().getAll()
        assertEquals(1, storedRows.size)
        assertNotEquals("KandyTech", storedRows.first().encryptedWord)
        assertEquals("KandyTech", crypto.decrypt(storedRows.first().encryptedWord, storedRows.first().iv))

        // Verify personal word appears in prefix suggestions & is recognized as known
        val items = engine.suggest("kandyt", LanguageMode.ENGLISH)
        assertTrue(items.any { it.text.equals("kandytech", ignoreCase = true) })
        assertNull(engine.bestAutoCorrectCandidate("KandyTech", LanguageMode.ENGLISH))

        // Remove word works cleanly
        engine.removePersonalWord("KandyTech", LanguageMode.ENGLISH)
        assertTrue(engine.getPersonalWords().isEmpty())
    }

    @Test fun blockSuggestion_suppressesCandidateUntilExplicitlyReAdded() = runTest {
        assertTrue(engine.suggest("recieve", LanguageMode.ENGLISH).any { it.text == "receive" })

        engine.blockSuggestion("receive", LanguageMode.ENGLISH)
        assertFalse(engine.suggest("recieve", LanguageMode.ENGLISH).any { it.text == "receive" })
        assertNotEquals("receive", engine.bestAutoCorrectCandidate("recieve", LanguageMode.ENGLISH))

        // Explicitly adding it back to personal dictionary unblocks it
        engine.addPersonalWord("receive", LanguageMode.ENGLISH)
        assertTrue(engine.suggest("recieve", LanguageMode.ENGLISH).any { it.text == "receive" })
    }

    @Test fun offensiveWordFilter_blocksOffensiveSuggestionsByDefault() = runTest {
        engine.addPersonalWord("bullshit", LanguageMode.ENGLISH)

        val filtered = engine.suggest(
            "bullsh",
            LanguageMode.ENGLISH,
            options = SuggestionOptions(blockOffensiveSuggestions = true),
        )
        assertFalse(filtered.any { it.text.equals("bullshit", ignoreCase = true) })

        val unfiltered = engine.suggest(
            "bullsh",
            LanguageMode.ENGLISH,
            options = SuggestionOptions(blockOffensiveSuggestions = false),
        )
        assertTrue(unfiltered.any { it.text.equals("bullshit", ignoreCase = true) })
    }

    @Test fun dictionaryAndSpellCheckToggles_areRespectedStrictly() {
        val noSuggestions = engine.suggest(
            "recomm",
            LanguageMode.ENGLISH,
            options = SuggestionOptions(suggestionsEnabled = false),
        )
        assertTrue(noSuggestions.isEmpty())

        val noSpell = engine.suggest(
            "recieve",
            LanguageMode.ENGLISH,
            options = SuggestionOptions(spellingCorrectionEnabled = false),
        )
        assertFalse(noSpell.any { it.text == "receive" })

        val noEnDict = engine.suggest(
            "recieve",
            LanguageMode.ENGLISH,
            options = SuggestionOptions(englishDictionaryEnabled = false),
        )
        assertFalse(noEnDict.any { it.text == "receive" })

        val noSiDict = engine.suggest(
            "අම්ම",
            LanguageMode.SINHALA,
            rawLatin = "amma",
            options = SuggestionOptions(sinhalaDictionaryEnabled = false),
        )
        assertFalse(noSiDict.any { it.text == "අම්මා" })
    }

    // --- 9. Passphrase-Encrypted Export / Import & Validation ---

    @Test fun encryptedDictionaryExportImport_roundTripAndTamperRejection() = runTest {
        engine.addPersonalWord("LankaKeys", LanguageMode.ENGLISH)
        engine.addPersonalWord("කළුතර", LanguageMode.SINHALA)

        val exported = engine.exportEncryptedDictionary("StrongPass#2026")
        assertFalse("Exported file must NOT contain plaintext words", exported.contains("LankaKeys"))
        assertFalse("Exported file must NOT contain plaintext Sinhala words", exported.contains("කළුතර"))
        assertTrue(exported.contains(DictionaryCryptoExporter.FORMAT_ID))

        // Wipe local dictionary and restore
        engine.clearLearnedWords()
        assertTrue(engine.getPersonalWords().isEmpty())

        val importedCount = engine.importEncryptedDictionary(exported, "StrongPass#2026")
        assertEquals(2, importedCount)
        val restored = engine.getPersonalWords().map { it.word }.toSet()
        assertTrue(restored.contains("LankaKeys"))
        assertTrue(restored.contains("කළුතර"))

        // Wrong passphrase must fail cleanly
        var wrongPassFailed = false
        try {
            engine.importEncryptedDictionary(exported, "WrongPass#9999")
        } catch (_: IllegalArgumentException) {
            wrongPassFailed = true
        }
        assertTrue("Wrong passphrase must be rejected", wrongPassFailed)

        // Tampered ciphertext must fail GCM authentication cleanly
        val tampered = exported.replace("\"ciphertext\": \"", "\"ciphertext\": \"AA")
        var tamperFailed = false
        try {
            engine.importEncryptedDictionary(tampered, "StrongPass#2026")
        } catch (_: IllegalArgumentException) {
            tamperFailed = true
        }
        assertTrue("Tampered ciphertext must be rejected", tamperFailed)
    }

    @Test fun encryptedDictionaryImport_rejectsShortPassphraseOversizedAndMalformedInputs() = runTest {
        // Short passphrase rejected on export
        var shortPassRejected = false
        try {
            engine.exportEncryptedDictionary("12345")
        } catch (_: IllegalArgumentException) {
            shortPassRejected = true
        }
        assertTrue(shortPassRejected)

        // Oversized (> 2 MB) import rejected before parsing
        val hugePayload = "x".repeat(DictionaryCryptoExporter.MAX_IMPORT_BYTES + 10)
        var hugeRejected = false
        try {
            DictionaryCryptoExporter.decryptAndValidateDictionary(hugePayload, "StrongPass#2026")
        } catch (_: IllegalArgumentException) {
            hugeRejected = true
        }
        assertTrue(hugeRejected)

        // Malformed non-JSON rejected
        var malformedRejected = false
        try {
            DictionaryCryptoExporter.decryptAndValidateDictionary("not valid json", "StrongPass#2026")
        } catch (_: IllegalArgumentException) {
            malformedRejected = true
        }
        assertTrue(malformedRejected)

        // Multi-word sentence rejected when adding to personal dictionary
        assertFalse(engine.addPersonalWord("this is a full sentence", LanguageMode.ENGLISH))
        assertFalse(engine.addPersonalWord("a".repeat(45), LanguageMode.ENGLISH))
    }

    // --- 10. Hostile & Edge-Case Inputs ---

    @Test fun hostileInputs_hugeStringsEmojiOddUnicodeNeverCrash() {
        val hugeWord = "a".repeat(500)
        assertTrue(engine.suggest(hugeWord, LanguageMode.ENGLISH).isEmpty())
        assertTrue(engine.suggestForExistingWord(hugeWord).isEmpty())
        assertNull(engine.bestAutoCorrectCandidate(hugeWord, LanguageMode.ENGLISH))

        val emojiInput = "🇱🇰👨\u200D👩\u200D👧\u200D👦🔥"
        val emojiSuggestions = engine.suggest(emojiInput, LanguageMode.ENGLISH)
        assertNotNull(emojiSuggestions)
        assertTrue(engine.suggestForExistingWord(emojiInput).isEmpty())

        assertTrue(engine.suggest("", LanguageMode.ENGLISH).isEmpty())
        assertTrue(engine.suggest("   \t\n  ", LanguageMode.ENGLISH).isEmpty())
        assertTrue(engine.suggest("word\u0000null", LanguageMode.ENGLISH).isEmpty())
        assertNull(engine.bestAutoCorrectCandidate("12345", LanguageMode.ENGLISH))
    }
}
