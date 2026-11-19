package com.udara.Utype.layouts

import android.view.inputmethod.EditorInfo
import com.udara.Utype.data.LanguageMode
import com.udara.Utype.engine.SinhalaMap

/**
 * Generates keyboard key rows for QWERTY (English & Sinhala), Symbols 1 & 2, Email, and Number Pad.
 * Closely follows the U Type rounded-key layout with top-right digit/symbol hints and long-press popups.
 */
object KeyboardLayouts {

    private val row1Chars = listOf(
        Triple("q", "1", listOf("1")),
        Triple("w", "2", listOf("2")),
        Triple("e", "3", listOf("3", "é", "è", "ê", "ë", "ē")),
        Triple("r", "4", listOf("4")),
        Triple("t", "5", listOf("5")),
        Triple("y", "6", listOf("6", "ý")),
        Triple("u", "7", listOf("7", "ú", "ù", "û", "ü", "ū")),
        Triple("i", "8", listOf("8", "í", "ì", "î", "ï", "ī")),
        Triple("o", "9", listOf("9", "ó", "ò", "ô", "ö", "ō")),
        Triple("p", "0", listOf("0")),
    )

    private val row2Chars = listOf(
        Triple("a", "@", listOf("@", "á", "à", "â", "ä", "ā", "æ")),
        Triple("s", "#", listOf("#", "ś", "š", "$")),
        Triple("d", "_", listOf("_", "-", "—")),
        Triple("f", "&", listOf("&")),
        Triple("g", "-", listOf("-", "~")),
        Triple("h", "+", listOf("+")),
        Triple("j", "=", listOf("=")),
        Triple("k", "(", listOf("(", "[", "{")),
        Triple("l", ")", listOf(")", "]", "}")),
    )

    private val row3Chars = listOf(
        Triple("z", "*", listOf("*", "ž")),
        Triple("x", "\"", listOf("\"")),
        Triple("c", "'", listOf("'", "ç", "ć")),
        Triple("v", ":", listOf(":")),
        Triple("b", ";", listOf(";")),
        Triple("n", "!", listOf("!", "ñ", "ń")),
        Triple("m", "?", listOf("?")),
    )

    fun buildRows(
        layoutMode: LayoutMode,
        languageMode: LanguageMode,
        shiftState: ShiftState,
        showNumberRow: Boolean,
        showHints: Boolean,
        showSinhalaHints: Boolean,
        imeAction: Int,
    ): List<KeyRow> {
        return when (layoutMode) {
            LayoutMode.ALPHA -> buildAlphaRows(
                languageMode = languageMode,
                shiftState = shiftState,
                showNumberRow = showNumberRow,
                showHints = showHints,
                showSinhalaHints = showSinhalaHints,
                imeAction = imeAction,
                isEmail = false,
            )
            LayoutMode.EMAIL -> buildAlphaRows(
                languageMode = LanguageMode.ENGLISH,
                shiftState = shiftState,
                showNumberRow = showNumberRow,
                showHints = showHints,
                showSinhalaHints = false,
                imeAction = imeAction,
                isEmail = true,
            )
            LayoutMode.SYMBOLS_1 -> buildSymbols1Rows(languageMode, imeAction)
            LayoutMode.SYMBOLS_2 -> buildSymbols2Rows(languageMode, imeAction)
            LayoutMode.NUMBER_PAD -> buildNumberPadRows(imeAction)
        }
    }

    private fun buildAlphaRows(
        languageMode: LanguageMode,
        shiftState: ShiftState,
        showNumberRow: Boolean,
        showHints: Boolean,
        showSinhalaHints: Boolean,
        imeAction: Int,
        isEmail: Boolean,
    ): List<KeyRow> {
        val shifted = shiftState != ShiftState.OFF
        val rows = ArrayList<KeyRow>(5)

        if (showNumberRow) {
            val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            rows.add(
                KeyRow(
                    keys = digits.map { d ->
                        KeySpec(label = d, action = KeyAction.Text(d), weight = 1f)
                    }
                )
            )
        }

        // Row 1: q w e r t y u i o p (with 1-0 hints unless dedicated number row is visible)
        rows.add(
            KeyRow(
                keys = row1Chars.map { (ch, hint, alts) ->
                    val display = if (shifted) ch.uppercase() else ch
                    val sHint = if (languageMode == LanguageMode.SINHALA && showSinhalaHints) {
                        SinhalaMap.keyHints[display] ?: SinhalaMap.keyHints[ch]
                    } else null
                    KeySpec(
                        label = display,
                        action = KeyAction.Text(display),
                        hint = if (showHints && !showNumberRow) hint else null,
                        sinhalaHint = sHint,
                        longPressText = hint,
                        alternates = alts,
                    )
                }
            )
        )

        // Row 2: a s d f g h j k l (slightly indented like screenshot)
        rows.add(
            KeyRow(
                keys = row2Chars.map { (ch, hint, alts) ->
                    val display = if (shifted) ch.uppercase() else ch
                    val sHint = if (languageMode == LanguageMode.SINHALA && showSinhalaHints) {
                        SinhalaMap.keyHints[display] ?: SinhalaMap.keyHints[ch]
                    } else null
                    KeySpec(
                        label = display,
                        action = KeyAction.Text(display),
                        hint = if (showHints) hint else null,
                        sinhalaHint = sHint,
                        longPressText = hint,
                        alternates = alts,
                    )
                },
                sideIndentWeight = 0.45f,
            )
        )

        // Row 3: Shift, z x c v b n m, Backspace
        val shiftLabel = when (shiftState) {
            ShiftState.OFF -> "⇧"
            ShiftState.ONCE -> "⬆"
            ShiftState.CAPS_LOCK -> "⇪"
        }
        val row3Keys = ArrayList<KeySpec>(9)
        row3Keys.add(
            KeySpec(
                label = shiftLabel,
                action = KeyAction.Shift,
                weight = 1.45f,
                style = if (shiftState == ShiftState.OFF) KeyStyle.FLAT_ICON else KeyStyle.ACCENT_ACTION,
                showPreview = false,
            )
        )
        row3Chars.forEach { (ch, hint, alts) ->
            val display = if (shifted) ch.uppercase() else ch
            val sHint = if (languageMode == LanguageMode.SINHALA && showSinhalaHints) {
                SinhalaMap.keyHints[display] ?: SinhalaMap.keyHints[ch]
            } else null
            row3Keys.add(
                KeySpec(
                    label = display,
                    action = KeyAction.Text(display),
                    hint = if (showHints) hint else null,
                    sinhalaHint = sHint,
                    longPressText = hint,
                    alternates = alts,
                )
            )
        }
        row3Keys.add(
            KeySpec(
                label = "⌫",
                action = KeyAction.Backspace,
                weight = 1.45f,
                style = KeyStyle.FLAT_ICON,
                repeatable = true,
                showPreview = false,
            )
        )
        rows.add(KeyRow(keys = row3Keys))

        // Row 4: "#", Emoji, Language (සි), Space ("ENGLISH" / "සිංහල"), Period, Enter
        val enterLabel = enterLabelForAction(imeAction)
        val row4Keys = if (isEmail) {
            listOf(
                KeySpec("#", KeyAction.SymbolsToggle, weight = 1.2f, style = KeyStyle.FLAT_ICON, showPreview = false),
                KeySpec("@", KeyAction.Text("@"), weight = 1.0f, style = KeyStyle.SPECIAL),
                KeySpec(".com", KeyAction.Text(".com"), weight = 1.2f, style = KeyStyle.SPECIAL, alternates = listOf(".com", ".lk", ".org", ".net", ".edu")),
                KeySpec("SPACE", KeyAction.Space, weight = 3.9f, style = KeyStyle.SPECIAL, showPreview = false),
                KeySpec(".", KeyAction.Text("."), weight = 1.1f, hint = "...", longPressText = ",", alternates = listOf(",", "?", "!", "...", "'", "\""), style = KeyStyle.FLAT_ICON),
                KeySpec(enterLabel, KeyAction.Enter, weight = 1.6f, style = KeyStyle.FLAT_ICON, showPreview = false),
            )
        } else {
            listOf(
                KeySpec("#", KeyAction.SymbolsToggle, weight = 1.15f, style = KeyStyle.FLAT_ICON, showPreview = false),
                KeySpec("☺", KeyAction.Emoji, weight = 1.1f, hint = ",", longPressText = ",", style = KeyStyle.FLAT_ICON, showPreview = false),
                KeySpec(
                    label = "සි",
                    action = KeyAction.LanguageSwitch,
                    weight = 1.15f,
                    style = if (languageMode == LanguageMode.SINHALA) KeyStyle.ACCENT_ACTION else KeyStyle.SPECIAL,
                    showPreview = false,
                ),
                KeySpec(
                    label = languageMode.spaceLabel,
                    action = KeyAction.Space,
                    weight = 4.05f,
                    style = KeyStyle.SPECIAL,
                    showPreview = false,
                ),
                KeySpec(
                    label = ".",
                    action = KeyAction.Text("."),
                    weight = 1.15f,
                    hint = "...",
                    longPressText = ",",
                    alternates = listOf(",", "?", "!", "...", "'", "\""),
                    style = KeyStyle.FLAT_ICON,
                ),
                KeySpec(
                    label = enterLabel,
                    action = KeyAction.Enter,
                    weight = 1.4f,
                    style = KeyStyle.FLAT_ICON,
                    showPreview = false,
                ),
            )
        }
        rows.add(KeyRow(keys = row4Keys))
        return rows
    }

    private fun buildSymbols1Rows(languageMode: LanguageMode, imeAction: Int): List<KeyRow> {
        val r1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val r2 = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
        val r3 = listOf("*", "\"", "'", ":", ";", "!", "?")

        return listOf(
            KeyRow(r1.map { KeySpec(it, KeyAction.Text(it)) }),
            KeyRow(r2.map { KeySpec(it, KeyAction.Text(it)) }),
            KeyRow(
                buildList {
                    add(KeySpec("=\\<", KeyAction.SymbolsMore, weight = 1.45f, style = KeyStyle.SPECIAL, showPreview = false))
                    r3.forEach { add(KeySpec(it, KeyAction.Text(it))) }
                    add(KeySpec("⌫", KeyAction.Backspace, weight = 1.45f, style = KeyStyle.FLAT_ICON, repeatable = true, showPreview = false))
                }
            ),
            KeyRow(
                listOf(
                    KeySpec("ABC", KeyAction.SymbolsToggle, weight = 1.4f, style = KeyStyle.SPECIAL, showPreview = false),
                    KeySpec(",", KeyAction.Text(","), weight = 1.0f, style = KeyStyle.SPECIAL),
                    KeySpec(languageMode.spaceLabel, KeyAction.Space, weight = 4.8f, style = KeyStyle.SPECIAL, showPreview = false),
                    KeySpec(".", KeyAction.Text("."), weight = 1.1f, alternates = listOf(",", "?", "!", "...", "'", "\""), style = KeyStyle.SPECIAL),
                    KeySpec(enterLabelForAction(imeAction), KeyAction.Enter, weight = 1.7f, style = KeyStyle.FLAT_ICON, showPreview = false),
                )
            ),
        )
    }

    private fun buildSymbols2Rows(languageMode: LanguageMode, imeAction: Int): List<KeyRow> {
        val r1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "Δ")
        val r2 = listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
        val r3 = listOf("%", "©", "®", "™", "✓", "[", "]")

        return listOf(
            KeyRow(r1.map { KeySpec(it, KeyAction.Text(it)) }),
            KeyRow(r2.map { KeySpec(it, KeyAction.Text(it)) }),
            KeyRow(
                buildList {
                    add(KeySpec("?123", KeyAction.SymbolsMore, weight = 1.45f, style = KeyStyle.SPECIAL, showPreview = false))
                    r3.forEach { add(KeySpec(it, KeyAction.Text(it))) }
                    add(KeySpec("⌫", KeyAction.Backspace, weight = 1.45f, style = KeyStyle.FLAT_ICON, repeatable = true, showPreview = false))
                }
            ),
            KeyRow(
                listOf(
                    KeySpec("ABC", KeyAction.SymbolsToggle, weight = 1.4f, style = KeyStyle.SPECIAL, showPreview = false),
                    KeySpec("<", KeyAction.Text("<"), weight = 1.0f, style = KeyStyle.SPECIAL),
                    KeySpec(languageMode.spaceLabel, KeyAction.Space, weight = 4.8f, style = KeyStyle.SPECIAL, showPreview = false),
                    KeySpec(">", KeyAction.Text(">"), weight = 1.1f, style = KeyStyle.SPECIAL),
                    KeySpec(enterLabelForAction(imeAction), KeyAction.Enter, weight = 1.7f, style = KeyStyle.FLAT_ICON, showPreview = false),
                )
            ),
        )
    }

    private fun buildNumberPadRows(imeAction: Int): List<KeyRow> {
        return listOf(
            KeyRow(
                listOf(
                    KeySpec("1", KeyAction.Text("1")),
                    KeySpec("2", KeyAction.Text("2")),
                    KeySpec("3", KeyAction.Text("3")),
                    KeySpec("-", KeyAction.Text("-"), style = KeyStyle.SPECIAL),
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("4", KeyAction.Text("4")),
                    KeySpec("5", KeyAction.Text("5")),
                    KeySpec("6", KeyAction.Text("6")),
                    KeySpec("SPACE", KeyAction.Space, style = KeyStyle.SPECIAL, showPreview = false),
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("7", KeyAction.Text("7")),
                    KeySpec("8", KeyAction.Text("8")),
                    KeySpec("9", KeyAction.Text("9")),
                    KeySpec("⌫", KeyAction.Backspace, style = KeyStyle.SPECIAL, repeatable = true, showPreview = false),
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("*#", KeyAction.SymbolsToggle, style = KeyStyle.SPECIAL, showPreview = false),
                    KeySpec("0", KeyAction.Text("0"), hint = "+", longPressText = "+", alternates = listOf("+", "*")),
                    KeySpec(".", KeyAction.Text("."), style = KeyStyle.SPECIAL),
                    KeySpec(enterLabelForAction(imeAction), KeyAction.Enter, style = KeyStyle.ACCENT_ACTION, showPreview = false),
                )
            ),
        )
    }

    private fun enterLabelForAction(imeAction: Int): String = when (imeAction) {
        EditorInfo.IME_ACTION_SEARCH -> "🔍"
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEND -> "➤"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "✓"
        else -> "↵"
    }
}
