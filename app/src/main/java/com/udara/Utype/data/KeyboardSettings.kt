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
    DARK("dark", "Emerald Dark", "Default dark rounded palette", 0xFF3DD6A5),
    LIGHT("light", "Mint Light", "Crisp daylight palette", 0xFF0B6B57),
    SYSTEM("system", "Follow System", "Auto dark / light with OS", 0xFF2FA885),
    MIDNIGHT("midnight", "Midnight Ocean", "Deep slate & cyan glow", 0xFF38BDF8),
    AMBER("amber", "Amber Dusk", "Warm charcoal & gold", 0xFFF59E0B),
    AMOLED("amoled", "Pitch AMOLED", "Pure black battery saver", 0xFF3DD6A5),
    ROSE("rose", "Sakura Night", "Velvet plum & rose accent", 0xFFFB7185);

    companion object {
        fun fromId(id: String?): KeyboardThemeId =
            entries.firstOrNull { it.id == id } ?: DARK

        fun resolvePalette(themeId: KeyboardThemeId, isSystemDark: Boolean): KeyboardPalette {
            val effective = if (themeId == SYSTEM) {
                if (isSystemDark) DARK else LIGHT
            } else {
                themeId
            }
            return when (effective) {
                DARK -> KeyboardPalette(
                    background = Color.parseColor("#0A110F"),
                    toolStrip = Color.parseColor("#131D1A"),
                    keyBg = Color.parseColor("#1E2B27"),
                    keyPressedBg = Color.parseColor("#2B3D38"),
                    specialKeyBg = Color.parseColor("#15201D"),
                    keyShadow = Color.parseColor("#050908"),
                    keyText = Color.parseColor("#EAF2EF"),
                    hintText = Color.parseColor("#9DB0AA"),
                    accent = Color.parseColor("#3DD6A5"),
                    onAccent = Color.parseColor("#062118"),
                    divider = Color.parseColor("#23332E"),
                    popupBg = Color.parseColor("#253631"),
                    isDark = true,
                )
                LIGHT -> KeyboardPalette(
                    background = Color.parseColor("#F6F8F7"),
                    toolStrip = Color.parseColor("#EBF0EE"),
                    keyBg = Color.parseColor("#FFFFFF"),
                    keyPressedBg = Color.parseColor("#DCEAE5"),
                    specialKeyBg = Color.parseColor("#E3EAE7"),
                    keyShadow = Color.parseColor("#C7D4CF"),
                    keyText = Color.parseColor("#0E1A16"),
                    hintText = Color.parseColor("#56665F"),
                    accent = Color.parseColor("#0B6B57"),
                    onAccent = Color.parseColor("#FFFFFF"),
                    divider = Color.parseColor("#D5E0DC"),
                    popupBg = Color.parseColor("#FFFFFF"),
                    isDark = false,
                )
                MIDNIGHT -> KeyboardPalette(
                    background = Color.parseColor("#090F1A"),
                    toolStrip = Color.parseColor("#111B2E"),
                    keyBg = Color.parseColor("#1B2942"),
                    keyPressedBg = Color.parseColor("#283C5E"),
                    specialKeyBg = Color.parseColor("#131E33"),
                    keyShadow = Color.parseColor("#04070D"),
                    keyText = Color.parseColor("#E8F1FC"),
                    hintText = Color.parseColor("#8FA4C2"),
                    accent = Color.parseColor("#38BDF8"),
                    onAccent = Color.parseColor("#041E2B"),
                    divider = Color.parseColor("#21324F"),
                    popupBg = Color.parseColor("#223452"),
                    isDark = true,
                )
                AMBER -> KeyboardPalette(
                    background = Color.parseColor("#14110E"),
                    toolStrip = Color.parseColor("#1E1914"),
                    keyBg = Color.parseColor("#2B241D"),
                    keyPressedBg = Color.parseColor("#3D3329"),
                    specialKeyBg = Color.parseColor("#211C17"),
                    keyShadow = Color.parseColor("#0A0806"),
                    keyText = Color.parseColor("#F5EFE6"),
                    hintText = Color.parseColor("#B5A796"),
                    accent = Color.parseColor("#F59E0B"),
                    onAccent = Color.parseColor("#241502"),
                    divider = Color.parseColor("#352C23"),
                    popupBg = Color.parseColor("#362D24"),
                    isDark = true,
                )
                AMOLED -> KeyboardPalette(
                    background = Color.parseColor("#000000"),
                    toolStrip = Color.parseColor("#090C0B"),
                    keyBg = Color.parseColor("#151C19"),
                    keyPressedBg = Color.parseColor("#24302C"),
                    specialKeyBg = Color.parseColor("#0D1210"),
                    keyShadow = Color.parseColor("#000000"),
                    keyText = Color.parseColor("#F0F7F4"),
                    hintText = Color.parseColor("#899E97"),
                    accent = Color.parseColor("#3DD6A5"),
                    onAccent = Color.parseColor("#041F16"),
                    divider = Color.parseColor("#1C2622"),
                    popupBg = Color.parseColor("#1D2925"),
                    isDark = true,
                )
                ROSE -> KeyboardPalette(
                    background = Color.parseColor("#140D12"),
                    toolStrip = Color.parseColor("#1F141B"),
                    keyBg = Color.parseColor("#2D1E28"),
                    keyPressedBg = Color.parseColor("#422C3B"),
                    specialKeyBg = Color.parseColor("#23171F"),
                    keyShadow = Color.parseColor("#0A0609"),
                    keyText = Color.parseColor("#F7EBF2"),
                    hintText = Color.parseColor("#BA9DB0"),
                    accent = Color.parseColor("#FB7185"),
                    onAccent = Color.parseColor("#2B070E"),
                    divider = Color.parseColor("#3A2633"),
                    popupBg = Color.parseColor("#3A2634"),
                    isDark = true,
                )
                SYSTEM -> resolvePalette(DARK, isSystemDark)
            }
        }
    }
}

data class KeyboardSettings(
    val themeId: KeyboardThemeId = KeyboardThemeId.DARK,
    val keyHeightDp: Float = 50f,           // 42..64 dp
    val keyHorizontalGapDp: Float = 6f,     // 6..12 dp (never < 6dp)
    val keyVerticalGapDp: Float = 8f,       // 8..14 dp (never < 8dp)
    val keyCornerRadiusDp: Float = 12f,     // 4..20 dp
    val bottomPaddingDp: Float = 10f,       // 0..36 dp for gesture bars
    val showNumberRow: Boolean = false,
    val showHintCharacters: Boolean = true,
    val showSinhalaKeyHints: Boolean = true,
    val hapticFeedback: Boolean = true,
    val keySound: Boolean = false,
    val defaultLanguage: LanguageMode = LanguageMode.ENGLISH,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val suggestionsEnabled: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val clipboardDuration: ClipboardDuration = ClipboardDuration.HOUR_2,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF,
    val onboardingCompleted: Boolean = false,
)
