package com.udara.Utype.data

import android.graphics.Color

enum class LanguageMode(val code: String, val spaceLabel: String, val displayName: String) {
    ENGLISH("en", "ENGLISH", "English"),
    SINHALA("si", "සිංහල", "Sinhala (Singlish)"),
    ;

    fun toggled(): LanguageMode = if (this == ENGLISH) SINHALA else ENGLISH

    companion object {
        fun fromCode(code: String?): LanguageMode =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class OneHandedMode(val id: String, val displayName: String) {
    OFF("off", "Full Width"),
    LEFT("left", "Left-Handed"),
    RIGHT("right", "Right-Handed");

    companion object {
        fun fromId(id: String?): OneHandedMode =
            entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class ClipboardDuration(val minutes: Int, val label: String) {
    MIN_30(30, "30 min"),
    HOUR_1(60, "1 hour"),
    HOUR_2(120, "2 hours"),
    HOUR_6(360, "6 hours"),
    HOUR_24(1440, "24 hours");

    val millis: Long get() = minutes * 60_000L

    companion object {
        fun fromMinutes(minutes: Int): ClipboardDuration =
            entries.firstOrNull { it.minutes == minutes } ?: HOUR_2
    }
}

data class KeyboardPalette(
    val background: Int,
    val toolStrip: Int,
    val keyBg: Int,
    val keyPressedBg: Int,
    val specialKeyBg: Int,
    val keyShadow: Int,
    val keyHighlight: Int,
    val keyText: Int,
    val hintText: Int,
    val accent: Int,
    val onAccent: Int,
    val divider: Int,
    val popupBg: Int,
    val isDark: Boolean,
)

enum class KeyboardThemeId(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val previewSwatchHex: Long,
) {
    CLASSIC_DARK("classic_dark", "Classic Dark", "Clean matte charcoal keys", 0xFF525256),
    LIGHT("light", "Clean Light", "Soft daylight gray & white", 0xFF5F6368),
    SYSTEM("system", "Follow System", "Auto dark / light with Android", 0xFF787C82),
    DARK("dark", "Forest Charcoal", "Deep muted slate-green", 0xFF4D8B78),
    MIDNIGHT("midnight", "Midnight Slate", "Calm dark navy-gray", 0xFF5B8DEF),
    AMBER("amber", "Warm Espresso", "Soft charcoal & warm sand", 0xFFD4A359),
    AMOLED("amoled", "Pitch Black", "True black OLED surface", 0xFF3A3A3E);

    companion object {
        fun fromId(id: String?): KeyboardThemeId =
            entries.firstOrNull { it.id == id } ?: CLASSIC_DARK

        fun resolvePalette(themeId: KeyboardThemeId, isSystemDark: Boolean): KeyboardPalette {
            val effective = if (themeId == SYSTEM) {
                if (isSystemDark) CLASSIC_DARK else LIGHT
            } else {
                themeId
            }
            return when (effective) {
                CLASSIC_DARK -> KeyboardPalette(
                    background = Color.parseColor("#262628"),
                    toolStrip = Color.parseColor("#262628"),
                    keyBg = Color.parseColor("#545458"),
                    keyPressedBg = Color.parseColor("#6B6B70"),
                    specialKeyBg = Color.parseColor("#444448"),
                    keyShadow = Color.parseColor("#141416"),
                    keyHighlight = Color.parseColor("#66666B"),
                    keyText = Color.parseColor("#F5F5F7"),
                    hintText = Color.parseColor("#D0D0D5"),
                    accent = Color.parseColor("#8AB4F8"),
                    onAccent = Color.parseColor("#111318"),
                    divider = Color.parseColor("#38383C"),
                    popupBg = Color.parseColor("#636368"),
                    isDark = true,
                )
                LIGHT -> KeyboardPalette(
                    background = Color.parseColor("#EAECEF"),
                    toolStrip = Color.parseColor("#EAECEF"),
                    keyBg = Color.parseColor("#FFFFFF"),
                    keyPressedBg = Color.parseColor("#DFE3E8"),
                    specialKeyBg = Color.parseColor("#D9DEE4"),
                    keyShadow = Color.parseColor("#B8BFC8"),
                    keyHighlight = Color.parseColor("#FFFFFF"),
                    keyText = Color.parseColor("#1C1E21"),
                    hintText = Color.parseColor("#656B73"),
                    accent = Color.parseColor("#2F6F62"),
                    onAccent = Color.parseColor("#FFFFFF"),
                    divider = Color.parseColor("#CFD5DC"),
                    popupBg = Color.parseColor("#FFFFFF"),
                    isDark = false,
                )
                DARK -> KeyboardPalette(
                    background = Color.parseColor("#18201E"),
                    toolStrip = Color.parseColor("#18201E"),
                    keyBg = Color.parseColor("#2E3B37"),
                    keyPressedBg = Color.parseColor("#3E4F4A"),
                    specialKeyBg = Color.parseColor("#24302C"),
                    keyShadow = Color.parseColor("#0D1210"),
                    keyHighlight = Color.parseColor("#3B4B46"),
                    keyText = Color.parseColor("#EDF3F1"),
                    hintText = Color.parseColor("#B3C2BD"),
                    accent = Color.parseColor("#6AB59E"),
                    onAccent = Color.parseColor("#0D1C17"),
                    divider = Color.parseColor("#2B3834"),
                    popupBg = Color.parseColor("#3B4B46"),
                    isDark = true,
                )
                MIDNIGHT -> KeyboardPalette(
                    background = Color.parseColor("#171C26"),
                    toolStrip = Color.parseColor("#171C26"),
                    keyBg = Color.parseColor("#2D3648"),
                    keyPressedBg = Color.parseColor("#3D4961"),
                    specialKeyBg = Color.parseColor("#232B3B"),
                    keyShadow = Color.parseColor("#0D1017"),
                    keyHighlight = Color.parseColor("#3A455C"),
                    keyText = Color.parseColor("#EDF1F7"),
                    hintText = Color.parseColor("#B0BAC9"),
                    accent = Color.parseColor("#7AA7E8"),
                    onAccent = Color.parseColor("#0F1726"),
                    divider = Color.parseColor("#293242"),
                    popupBg = Color.parseColor("#3A465C"),
                    isDark = true,
                )
                AMBER -> KeyboardPalette(
                    background = Color.parseColor("#1E1B18"),
                    toolStrip = Color.parseColor("#1E1B18"),
                    keyBg = Color.parseColor("#3A342E"),
                    keyPressedBg = Color.parseColor("#4D453D"),
                    specialKeyBg = Color.parseColor("#2E2924"),
                    keyShadow = Color.parseColor("#110F0D"),
                    keyHighlight = Color.parseColor("#4A423B"),
                    keyText = Color.parseColor("#F5F0EB"),
                    hintText = Color.parseColor("#C2B8AD"),
                    accent = Color.parseColor("#D4A359"),
                    onAccent = Color.parseColor("#1C1509"),
                    divider = Color.parseColor("#352F29"),
                    popupBg = Color.parseColor("#4C443C"),
                    isDark = true,
                )
                AMOLED -> KeyboardPalette(
                    background = Color.parseColor("#000000"),
                    toolStrip = Color.parseColor("#000000"),
                    keyBg = Color.parseColor("#242426"),
                    keyPressedBg = Color.parseColor("#38383C"),
                    specialKeyBg = Color.parseColor("#18181A"),
                    keyShadow = Color.parseColor("#000000"),
                    keyHighlight = Color.parseColor("#333336"),
                    keyText = Color.parseColor("#F2F2F7"),
                    hintText = Color.parseColor("#AEAEB2"),
                    accent = Color.parseColor("#8AB4F8"),
                    onAccent = Color.parseColor("#0D1117"),
                    divider = Color.parseColor("#242426"),
                    popupBg = Color.parseColor("#333338"),
                    isDark = true,
                )
                SYSTEM -> resolvePalette(CLASSIC_DARK, isSystemDark)
            }
        }
    }
}

data class KeyboardSettings(
    val themeId: KeyboardThemeId = KeyboardThemeId.CLASSIC_DARK,
    val keyHeightDp: Float = 54f,           // Spacious default matching screenshots (42..66 dp)
    val keyHorizontalGapDp: Float = 7f,     // Generous gap >= 6dp (6..12 dp)
    val keyVerticalGapDp: Float = 9f,       // Generous row gap >= 8dp (8..14 dp)
    val keyCornerRadiusDp: Float = 11f,     // Smooth rounded keycaps (4..20 dp)
    val bottomPaddingDp: Float = 12f,       // Gesture bar clearance (0..36 dp)
    val showNumberRow: Boolean = false,
    val showHintCharacters: Boolean = true,
    val showSinhalaKeyHints: Boolean = true,
    val keyPreviewPopup: Boolean = false,
    val hapticFeedback: Boolean = true,
    val keySound: Boolean = false,
    val defaultLanguage: LanguageMode = LanguageMode.ENGLISH,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val suggestionBarEnabled: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val spellingCorrectionEnabled: Boolean = true,
    val autoCorrectOnSpace: Boolean = false, // Default OFF per user privacy & UX spec
    val learnFromTyping: Boolean = true,
    val blockOffensiveSuggestions: Boolean = true,
    val englishDictionaryEnabled: Boolean = true,
    val sinhalaDictionaryEnabled: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val clipboardDuration: ClipboardDuration = ClipboardDuration.HOUR_2,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val onboardingCompleted: Boolean = false,
)
