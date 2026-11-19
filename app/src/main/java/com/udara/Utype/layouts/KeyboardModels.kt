package com.udara.Utype.layouts

enum class ShiftState {
    OFF,
    ONCE,
    CAPS_LOCK
}

enum class LayoutMode {
    ALPHA,
    SYMBOLS_1,
    SYMBOLS_2,
    NUMBER_PAD,
    EMAIL
}

enum class KeyStyle {
    CHARACTER,      // Standard raised rounded key
    SPECIAL,        // Raised special key (e.g., language switch, space)
    FLAT_ICON,      // Subtle borderless-style action key (Shift, Backspace, #, Emoji, Enter)
    ACCENT_ACTION   // Accent-tinted action key (e.g., Search/Send Enter or active Shift)
}

sealed class KeyAction {
    data class Text(val text: String) : KeyAction()
    data object Shift : KeyAction()
    data object Backspace : KeyAction()
    data object Space : KeyAction()
    data object Enter : KeyAction()
    data object SymbolsToggle : KeyAction()
    data object SymbolsMore : KeyAction()
    data object Emoji : KeyAction()
    data object LanguageSwitch : KeyAction()
}

data class KeySpec(
    val label: String,
    val action: KeyAction,
    val weight: Float = 1f,
    val hint: String? = null,
    val sinhalaHint: String? = null,
    val longPressText: String? = null,
    val alternates: List<String> = emptyList(),
    val style: KeyStyle = KeyStyle.CHARACTER,
    val repeatable: Boolean = false,
    val showPreview: Boolean = true,
)

data class KeyRow(
    val keys: List<KeySpec>,
    val sideIndentWeight: Float = 0f,
)
