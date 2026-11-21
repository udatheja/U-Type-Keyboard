package com.udara.Utype

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.udara.Utype.data.AndroidKeystoreClipboardCrypto
import com.udara.Utype.data.BackupManager
import com.udara.Utype.data.ClipboardCleanupWorker
import com.udara.Utype.data.ClipboardRepository
import com.udara.Utype.data.SettingsRepository
import com.udara.Utype.data.UTypeDatabase
import com.udara.Utype.engine.SuggestionEngine

class UTypeApplication : Application() {

    lateinit var database: UTypeDatabase
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var clipboardRepository: ClipboardRepository
        private set
    lateinit var suggestionEngine: SuggestionEngine
        private set
    lateinit var backupManager: BackupManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize bundled EmojiCompat so modern emojis render reliably offline on all Android versions
        try {
            val emojiConfig = BundledEmojiCompatConfig(this)
                .setReplaceAll(true)
            EmojiCompat.init(emojiConfig)
        } catch (_: Exception) {
            // Safe fallback if already initialized in test environments
        }

        database = UTypeDatabase.getInstance(this)
        settingsRepository = SettingsRepository(this)
        val keystoreCrypto = AndroidKeystoreClipboardCrypto()
        clipboardRepository = ClipboardRepository(
            dao = database.clipboardDao(),
            crypto = keystoreCrypto,
        )
        suggestionEngine = SuggestionEngine(
            context = this,
            learnedWordDao = database.learnedWordDao(),
            shortcutDao = database.textShortcutDao(),
            personalWordDao = database.personalWordDao(),
            blockedSuggestionDao = database.blockedSuggestionDao(),
            crypto = keystoreCrypto,
        )
        backupManager = BackupManager(
            context = this,
            settingsRepo = settingsRepository,
            db = database,
            clipboardRepo = clipboardRepository,
            crypto = keystoreCrypto,
        )

        // Schedule reliable 15-minute background WorkManager cleanup for expired clipboard items
        try {
            ClipboardCleanupWorker.schedule(this)
        } catch (_: Exception) {
            // Safe in unit-test environments without WorkManager initialized
        }
    }
}
