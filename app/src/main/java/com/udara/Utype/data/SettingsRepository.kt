package com.udara.Utype.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uTypeDataStore: DataStore<Preferences> by preferencesDataStore(name = "utype_settings")

class SettingsRepository(private val context: Context) {

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

    val settingsFlow: Flow<KeyboardSettings> = context.uTypeDataStore.data.map { prefs ->
        KeyboardSettings(
            themeId = KeyboardThemeId.fromId(prefs[Keys.THEME_ID]),
            keyHeightDp = (prefs[Keys.KEY_HEIGHT_DP] ?: 50f).coerceIn(42f, 64f),
            keyHorizontalGapDp = (prefs[Keys.KEY_H_GAP_DP] ?: 6f).coerceIn(6f, 12f),
            keyVerticalGapDp = (prefs[Keys.KEY_V_GAP_DP] ?: 8f).coerceIn(8f, 14f),
            keyCornerRadiusDp = (prefs[Keys.KEY_RADIUS_DP] ?: 12f).coerceIn(4f, 20f),
            bottomPaddingDp = (prefs[Keys.BOTTOM_PADDING_DP] ?: 10f).coerceIn(0f, 36f),
            showNumberRow = prefs[Keys.SHOW_NUMBER_ROW] ?: false,
            showHintCharacters = prefs[Keys.SHOW_HINTS] ?: true,
            showSinhalaKeyHints = prefs[Keys.SHOW_SINHALA_HINTS] ?: true,
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
    }

    suspend fun currentSettings(): KeyboardSettings = settingsFlow.first()

    suspend fun updateSettings(transform: (KeyboardSettings) -> KeyboardSettings) {
        val updated = transform(currentSettings())
        saveAll(updated)
    }

    suspend fun saveAll(s: KeyboardSettings) {
        context.uTypeDataStore.edit { prefs ->
            prefs[Keys.THEME_ID] = s.themeId.id
            prefs[Keys.KEY_HEIGHT_DP] = s.keyHeightDp.coerceIn(42f, 64f)
            prefs[Keys.KEY_H_GAP_DP] = s.keyHorizontalGapDp.coerceIn(6f, 12f)
            prefs[Keys.KEY_V_GAP_DP] = s.keyVerticalGapDp.coerceIn(8f, 14f)
            prefs[Keys.KEY_RADIUS_DP] = s.keyCornerRadiusDp.coerceIn(4f, 20f)
            prefs[Keys.BOTTOM_PADDING_DP] = s.bottomPaddingDp.coerceIn(0f, 36f)
            prefs[Keys.SHOW_NUMBER_ROW] = s.showNumberRow
            prefs[Keys.SHOW_HINTS] = s.showHintCharacters
            prefs[Keys.SHOW_SINHALA_HINTS] = s.showSinhalaKeyHints
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
    }

    /** Remember last chosen language per host app package name. */
    suspend fun getLanguageForApp(packageName: String?): LanguageMode? {
        if (packageName.isNullOrBlank()) return null
        val key = stringPreferencesKey("app_lang_$packageName")
        val code = context.uTypeDataStore.data.first()[key] ?: return null
        return LanguageMode.fromCode(code)
    }

    suspend fun setLanguageForApp(packageName: String?, mode: LanguageMode) {
        if (packageName.isNullOrBlank()) return
        val key = stringPreferencesKey("app_lang_$packageName")
        context.uTypeDataStore.edit { prefs ->
            prefs[key] = mode.code
        }
    }
}
