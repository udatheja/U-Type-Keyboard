package com.udara.Utype.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.udara.Utype.engine.SuggestionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * International-standard tests for JSON Backup/Restore round-trip, SuggestionEngine casing &
 * exact-match ranking, and ClipboardCrypto fallback resilience.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndSuggestionTest {

    private lateinit var context: Context
    private lateinit var db: UTypeDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var clipboardRepo: ClipboardRepository
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var backupManager: BackupManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UTypeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(context.cacheDir, "test_settings_${UUID.randomUUID()}.preferences_pb") },
        )
        settingsRepo = SettingsRepository(context, testDataStore)
        clipboardRepo = ClipboardRepository(db.clipboardDao(), PassThroughClipboardCrypto())
        suggestionEngine = SuggestionEngine(context, db.learnedWordDao(), db.textShortcutDao())
        backupManager = BackupManager(context, settingsRepo, db, clipboardRepo)
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        db.close()
    }

    @Test
    fun backupExportAndRestoreRoundTripPreservesAllDataAndRefreshesSuggestions() = runTest {
        settingsRepo.saveAll(
            KeyboardSettings(
                themeId = KeyboardThemeId.AMOLED,
                keyHeightDp = 58f,
                showNumberRow = true,
                keyPreviewPopup = true,
                defaultLanguage = LanguageMode.SINHALA,
            )
        )
        suggestionEngine.saveShortcut("eml", "udara@example.lk")
        suggestionEngine.learnWord("Colombo", LanguageMode.ENGLISH)
        suggestionEngine.learnWord("අයුබෝවන්", LanguageMode.SINHALA)
        clipboardRepo.saveClip("Pinned Wi-Fi Pass", isPinned = true)
        clipboardRepo.saveClip("Temporary OTP", isPinned = false)

        val backupJson = backupManager.buildBackupJson()

        // Wipe everything to simulate a fresh device restore
        settingsRepo.saveAll(KeyboardSettings())
        db.textShortcutDao().delete("eml")
        suggestionEngine.clearLearnedWords()
        clipboardRepo.clearAllUnpinned()
        db.clipboardDao().getAll().forEach { db.clipboardDao().deleteById(it.id) }

        val summary = backupManager.importFromJson(backupJson)
        suggestionEngine.refreshDynamicData()

        assertEquals(1, summary.shortcutsCount)
        assertEquals(2, summary.learnedWordsCount)
        assertEquals(1, summary.pinnedClipsCount)

        val restoredSettings = settingsRepo.currentSettings()
        assertEquals(KeyboardThemeId.AMOLED, restoredSettings.themeId)
        assertEquals(58f, restoredSettings.keyHeightDp)
        assertTrue(restoredSettings.showNumberRow)
        assertTrue(restoredSettings.keyPreviewPopup)
        assertEquals(LanguageMode.SINHALA, restoredSettings.defaultLanguage)

        assertEquals("udara@example.lk", suggestionEngine.getShortcutExpansion("eml"))
        val pinned = clipboardRepo.getPinnedItems()
        assertEquals(1, pinned.size)
        assertEquals("Pinned Wi-Fi Pass", pinned.first().text)
    }

    @Test
    fun invalidBackupJsonIsRejectedGracefully() = runTest {
        var threwForEmptyObject = false
        try {
            backupManager.importFromJson("{}")
        } catch (_: IllegalArgumentException) {
            threwForEmptyObject = true
        }
        assertTrue("Empty JSON object must be rejected", threwForEmptyObject)

        var threwForGarbage = false
        try {
            backupManager.importFromJson("not-json-at-all")
        } catch (_: Exception) {
            threwForGarbage = true
        }
        assertTrue("Malformed non-JSON must be rejected", threwForGarbage)
    }

    @Test
    fun suggestionEnginePreservesAllCapsAndCapitalizedAndExactSinhalaMatches() = runTest {
        suggestionEngine.initialize()
        suggestionEngine.learnWord("hello", LanguageMode.ENGLISH)
        suggestionEngine.learnWord("කෑම", LanguageMode.SINHALA)

        val allCaps = suggestionEngine.suggest("HEL", LanguageMode.ENGLISH)
        assertTrue(allCaps.any { it.text == "HELLO" })

        val capitalized = suggestionEngine.suggest("Hel", LanguageMode.ENGLISH)
        assertTrue(capitalized.any { it.text == "Hello" })

        val sinhala = suggestionEngine.suggest("කෑම", LanguageMode.SINHALA, rawLatin = "kAama")
        assertEquals("කෑම", sinhala.first().text)
    }

    @Test
    fun androidKeystoreCryptoEncryptsDecryptsAndHashesSafelyWithoutCrashing() {
        val crypto = AndroidKeystoreClipboardCrypto()
        val secret = "ආයුබෝවන් Secret 🇱🇰 1234"
        val encrypted = crypto.encrypt(secret)
        assertNotEquals(secret, encrypted.ciphertext)
        assertEquals(secret, crypto.decrypt(encrypted.ciphertext, encrypted.iv))
        assertEquals(64, crypto.hash(secret).length)
    }
}
