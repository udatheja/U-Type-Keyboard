package com.udara.Utype.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

private val Context.uTypeDataStore: DataStore<Preferences> by preferencesDataStore(name = "utype_settings")

class SettingsRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.uTypeDataStore,
) {

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val KEY_HEIGHT_DP = floatPreferencesKey("key_height_dp")
        val KEY_H_GAP_DP = floatPreferencesKey("key_h_gap_dp")
        val KEY_V_GAP_DP = floatPreferencesKey("key_v_gap_dp")
        val KEY_RADIUS_DP = floatPreferencesKey("key_radius_dp")
        val BOTTOM_PADDING_DP = floatPreferencesKey("bottom_padding_dp")
        val SHOW_NUMBER_ROW = booleanPreferencesKey("show_number_row")
        val SHOW_HINTS = booleanPreferencesKey("show_hints")
        val SHOW_SINHALA_HINTS = booleanPreferencesKey("show_sinhala_hints")
        val KEY_PREVIEW_POPUP = booleanPreferencesKey("key_preview_popup")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val KEY_SOUND = booleanPreferencesKey("key_sound")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val SUGGESTIONS_ENABLED = booleanPreferencesKey("suggestions_enabled")
        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val CLIPBOARD_DURATION_MIN = intPreferencesKey("clipboard_duration_min")
        val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    /**
     * Synchronous in-memory cache for per-app language selection.
     * Prevents any async DataStore race condition when switching languages ("සි" / "En")
     * or when Android immediately restarts the InputConnection (`restarting = true`).
     */
    private val appLanguageCache = ConcurrentHashMap<String, LanguageMode>()

    private fun readSettings(prefs: Preferences): KeyboardSettings = KeyboardSettings(
        themeId = KeyboardThemeId.fromId(prefs[Keys.THEME_ID]),
        keyHeightDp = (prefs[Keys.KEY_HEIGHT_DP] ?: 54f).coerceIn(42f, 66f),
        keyHorizontalGapDp = (prefs[Keys.KEY_H_GAP_DP] ?: 7f).coerceIn(6f, 12f),
        keyVerticalGapDp = (prefs[Keys.KEY_V_GAP_DP] ?: 9f).coerceIn(8f, 14f),
        keyCornerRadiusDp = (prefs[Keys.KEY_RADIUS_DP] ?: 11f).coerceIn(4f, 20f),
        bottomPaddingDp = (prefs[Keys.BOTTOM_PADDING_DP] ?: 12f).coerceIn(0f, 36f),
        showNumberRow = prefs[Keys.SHOW_NUMBER_ROW] ?: false,
        showHintCharacters = prefs[Keys.SHOW_HINTS] ?: true,
        showSinhalaKeyHints = prefs[Keys.SHOW_SINHALA_HINTS] ?: true,
        keyPreviewPopup = prefs[Keys.KEY_PREVIEW_POPUP] ?: false,
        hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
        keySound = prefs[Keys.KEY_SOUND] ?: false,
        defaultLanguage = LanguageMode.fromCode(prefs[Keys.DEFAULT_LANGUAGE]),
        autoCapitalize = prefs[Keys.AUTO_CAPITALIZE] ?: true,
        doubleSpacePeriod = prefs[Keys.DOUBLE_SPACE_PERIOD] ?: true,
        suggestionsEnabled = prefs[Keys.SUGGESTIONS_ENABLED] ?: true,
        clipboardEnabled = prefs[Keys.CLIPBOARD_ENABLED] ?: true,
        clipboardDuration = ClipboardDuration.fromMinutes(prefs[Keys.CLIPBOARD_DURATION_MIN] ?: 120),
        oneHandedMode = OneHandedMode.fromId(prefs[Keys.ONE_HANDED_MODE]),
        onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
    )

    private fun writeSettings(prefs: MutablePreferences, s: KeyboardSettings) {
        prefs[Keys.THEME_ID] = s.themeId.id
        prefs[Keys.KEY_HEIGHT_DP] = s.keyHeightDp.coerceIn(42f, 66f)
        prefs[Keys.KEY_H_GAP_DP] = s.keyHorizontalGapDp.coerceIn(6f, 12f)
        prefs[Keys.KEY_V_GAP_DP] = s.keyVerticalGapDp.coerceIn(8f, 14f)
        prefs[Keys.KEY_RADIUS_DP] = s.keyCornerRadiusDp.coerceIn(4f, 20f)
        prefs[Keys.BOTTOM_PADDING_DP] = s.bottomPaddingDp.coerceIn(0f, 36f)
        prefs[Keys.SHOW_NUMBER_ROW] = s.showNumberRow
        prefs[Keys.SHOW_HINTS] = s.showHintCharacters
        prefs[Keys.SHOW_SINHALA_HINTS] = s.showSinhalaKeyHints
        prefs[Keys.KEY_PREVIEW_POPUP] = s.keyPreviewPopup
        prefs[Keys.HAPTIC_FEEDBACK] = s.hapticFeedback
        prefs[Keys.KEY_SOUND] = s.keySound
        prefs[Keys.DEFAULT_LANGUAGE] = s.defaultLanguage.code
        prefs[Keys.AUTO_CAPITALIZE] = s.autoCapitalize
        prefs[Keys.DOUBLE_SPACE_PERIOD] = s.doubleSpacePeriod
        prefs[Keys.SUGGESTIONS_ENABLED] = s.suggestionsEnabled
        prefs[Keys.CLIPBOARD_ENABLED] = s.clipboardEnabled
        prefs[Keys.CLIPBOARD_DURATION_MIN] = s.clipboardDuration.minutes
        prefs[Keys.ONE_HANDED_MODE] = s.oneHandedMode.id
        prefs[Keys.ONBOARDING_COMPLETED] = s.onboardingCompleted
    }

    /**
     * Emits distinct [KeyboardSettings] updates only, so writing per-app language keys
     * never causes redundant settings redraws in the IME or Settings UI.
     */
    val settingsFlow: Flow<KeyboardSettings> = dataStore.data
        .map { prefs -> readSettings(prefs) }
        .distinctUntilChanged()

    suspend fun currentSettings(): KeyboardSettings = settingsFlow.first()

    /** Atomic read-modify-write inside a single DataStore transaction. */
    suspend fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        dataStore.edit { prefs ->
            val updated = transform(readSettings(prefs))
            writeSettings(prefs, updated)
        }
    }

    suspend fun saveAll(s: KeyboardSettings) {
        dataStore.edit { prefs ->
            writeSettings(prefs, s)
        }
    }

    private fun normalizedAppKey(packageName: String?): String =
        packageName?.trim()?.takeIf { it.isNotEmpty() } ?: "_default_host_"

    /** Synchronous fast-path read for per-app language without waiting for disk I/O. */
    fun getCachedLanguageForApp(packageName: String?): LanguageMode? =
        appLanguageCache[normalizedAppKey(packageName)]

    /** Synchronous in-memory cache write so rapid restarts immediately observe the new language. */
    fun cacheLanguageForApp(packageName: String?, mode: LanguageMode) {
        appLanguageCache[normalizedAppKey(packageName)] = mode
    }

    internal fun clearLanguageCacheForTest() {
        appLanguageCache.clear()
    }

    /** Remember last chosen language per host app package name. */
    suspend fun getLanguageForApp(packageName: String?): LanguageMode? {
        val pkg = normalizedAppKey(packageName)
        appLanguageCache[pkg]?.let { return it }
        val key = stringPreferencesKey("app_lang_$pkg")
        val code = dataStore.data.first()[key] ?: return null
        val mode = LanguageMode.fromCode(code)
        // Only populate cache if a newer user toggle didn't already populate it while reading disk
        return appLanguageCache.putIfAbsent(pkg, mode) ?: mode
    }

    suspend fun setLanguageForApp(packageName: String?, mode: LanguageMode) {
        val pkg = normalizedAppKey(packageName)
        appLanguageCache[pkg] = mode
        val key = stringPreferencesKey("app_lang_$pkg")
        dataStore.edit { prefs ->
            prefs[key] = mode.code
        }
    }
}
