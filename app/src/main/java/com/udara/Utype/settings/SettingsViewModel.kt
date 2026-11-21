package com.udara.Utype.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.udara.Utype.UTypeApplication
import com.udara.Utype.data.ClipboardDuration
import com.udara.Utype.data.ClipboardItem
import com.udara.Utype.data.KeyboardSettings
import com.udara.Utype.data.KeyboardThemeId
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.data.OneHandedMode
import com.udara.Utype.data.TextShortcutEntity
import com.udara.Utype.engine.DictionaryCryptoExporter
import com.udara.Utype.engine.PersonalWordItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UTypeApplication

    val settings: StateFlow<KeyboardSettings> = app.settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = KeyboardSettings(),
    )

    val shortcuts: StateFlow<List<TextShortcutEntity>> = app.database.textShortcutDao().observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val learnedWordCount: StateFlow<Int> = app.database.personalWordDao().observeCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0,
    )

    private val _personalWords = MutableStateFlow<List<PersonalWordItem>>(emptyList())
    val personalWords: StateFlow<List<PersonalWordItem>> = _personalWords.asStateFlow()

    val clipboardItems: StateFlow<List<ClipboardItem>> = app.clipboardRepository.observeItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        viewModelScope.launch {
            val current = app.settingsRepository.currentSettings()
            app.clipboardRepository.cleanupExpired(current.clipboardDuration.millis)
            app.suggestionEngine.initialize()
            _personalWords.value = app.suggestionEngine.getPersonalWords()
        }
    }

    fun refreshPersonalWords() {
        viewModelScope.launch {
            app.suggestionEngine.refreshDynamicData()
            _personalWords.value = app.suggestionEngine.getPersonalWords()
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        viewModelScope.launch {
            app.settingsRepository.updateSettings(transform)
        }
    }

    fun setTheme(themeId: KeyboardThemeId) = updateSettings { it.copy(themeId = themeId) }
    fun setKeyHeight(dp: Float) = updateSettings { it.copy(keyHeightDp = dp) }
    fun setHorizontalGap(dp: Float) = updateSettings { it.copy(keyHorizontalGapDp = dp) }
    fun setVerticalGap(dp: Float) = updateSettings { it.copy(keyVerticalGapDp = dp) }
    fun setCornerRadius(dp: Float) = updateSettings { it.copy(keyCornerRadiusDp = dp) }
    fun setBottomPadding(dp: Float) = updateSettings { it.copy(bottomPaddingDp = dp) }
    fun setOneHandedMode(mode: OneHandedMode) = updateSettings { it.copy(oneHandedMode = mode) }
    fun setDefaultLanguage(lang: LanguageMode) = updateSettings { it.copy(defaultLanguage = lang) }
    fun setClipboardDuration(duration: ClipboardDuration) = updateSettings { it.copy(clipboardDuration = duration) }
    fun markOnboardingDone() = updateSettings { it.copy(onboardingCompleted = true) }

    fun addShortcut(shortCode: String, expansion: String) {
        viewModelScope.launch {
            app.suggestionEngine.saveShortcut(shortCode, expansion)
            _statusMessage.value = "Saved shortcut \"${shortCode.trim().lowercase()}\""
        }
    }

    fun deleteShortcut(shortCode: String) {
        viewModelScope.launch {
            app.suggestionEngine.deleteShortcut(shortCode)
            _statusMessage.value = "Removed shortcut \"$shortCode\""
        }
    }

    fun addPersonalWord(word: String, language: LanguageMode) {
        viewModelScope.launch {
            val added = app.suggestionEngine.addPersonalWord(word, language)
            if (added) {
                _personalWords.value = app.suggestionEngine.getPersonalWords()
                _statusMessage.value = "Added to personal dictionary."
            } else {
                _statusMessage.value = "Enter a single valid word (max 40 characters)."
            }
        }
    }

    fun removePersonalWord(word: String, language: LanguageMode) {
        viewModelScope.launch {
            app.suggestionEngine.removePersonalWord(word, language)
            _personalWords.value = app.suggestionEngine.getPersonalWords()
            _statusMessage.value = "Removed word from personal dictionary."
        }
    }

    fun clearLearnedWords() {
        viewModelScope.launch {
            app.suggestionEngine.clearLearnedWords()
            _personalWords.value = emptyList()
            _statusMessage.value = "All personal & learned words cleared."
        }
    }

    fun exportEncryptedDictionaryToUri(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val encrypted = app.suggestionEngine.exportEncryptedDictionary(passphrase)
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(encrypted.toByteArray(Charsets.UTF_8))
                    } ?: error("Cannot write to selected file.")
                }
            }
            _statusMessage.value = result.fold(
                onSuccess = { "Encrypted dictionary exported successfully." },
                onFailure = { err -> "Dictionary export failed: ${err.message}" },
            )
        }
    }

    fun importEncryptedDictionaryFromUri(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { input ->
                        val data = input.readBytes()
                        require(data.size <= DictionaryCryptoExporter.MAX_IMPORT_BYTES) {
                            "Dictionary file exceeds 2 MB limit."
                        }
                        data
                    } ?: error("Cannot read selected file.")
                    val count = app.suggestionEngine.importEncryptedDictionary(
                        encryptedJson = String(bytes, Charsets.UTF_8),
                        passphrase = passphrase,
                    )
                    count
                }
            }
            if (result.isSuccess) {
                _personalWords.value = app.suggestionEngine.getPersonalWords()
            }
            _statusMessage.value = result.fold(
                onSuccess = { count -> "Imported $count words into personal dictionary." },
                onFailure = { err -> "Dictionary import failed: ${err.message}" },
            )
        }
    }

    fun clearUnpinnedClipboard() {
        viewModelScope.launch {
            app.clipboardRepository.clearAllUnpinned()
            _statusMessage.value = "Cleared unpinned clipboard items (pinned items kept)."
        }
    }

    fun exportBackupToDownloads() {
        viewModelScope.launch {
            val result = app.backupManager.exportToDownloads()
            _statusMessage.value = result.fold(
                onSuccess = { path -> "Backup exported to $path" },
                onFailure = { err -> "Export failed: ${err.message}" },
            )
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val result = app.backupManager.exportToUri(uri)
            _statusMessage.value = result.fold(
                onSuccess = { "Backup saved successfully." },
                onFailure = { err -> "Save failed: ${err.message}" },
            )
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val result = app.backupManager.importFromUri(uri)
            if (result.isSuccess) {
                app.suggestionEngine.refreshDynamicData()
                _personalWords.value = app.suggestionEngine.getPersonalWords()
            }
            _statusMessage.value = result.fold(
                onSuccess = { summary ->
                    "Restored settings, ${summary.shortcutsCount} shortcuts, ${summary.learnedWordsCount} words, and ${summary.pinnedClipsCount} pinned clips."
                },
                onFailure = { err -> "Restore failed: ${err.message}" },
            )
        }
    }
}
