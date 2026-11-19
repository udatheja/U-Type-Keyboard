package com.udara.Utype.engine

/**
 * Unicode Stylish Font engine.
 *
 * Transforms ASCII English letters (A-Z, a-z) and digits (0-9) into styled Unicode equivalents.
 * Sinhala Unicode characters (U+0D80..U+0DFF) and other non-Latin scripts are NEVER altered.
 */
enum class StylishFontStyle(val id: String, val displayName: String) {
    NORMAL("normal", "Normal"),
    BOLD("bold", "Bold"),
    ITALIC("italic", "Italic"),
    BOLD_ITALIC("bold_italic", "Bold Italic"),
    SERIF_BOLD("serif_bold", "Serif Bold"),
    SANS_BOLD("sans_bold", "Sans Bold"),
    SANS_ITALIC("sans_italic", "Sans Italic"),
    SANS_BOLD_ITALIC("sans_bold_italic", "Sans Bold Italic"),
    SCRIPT("script", "Script"),
    BOLD_SCRIPT("bold_script", "Bold Script"),
    FRAKTUR("fraktur", "Fraktur"),
    BOLD_FRAKTUR("bold_fraktur", "Bold Fraktur"),
    DOUBLE_STRUCK("double_struck", "Double-struck"),
    MONOSPACE("monospace", "Monospace"),
    SMALL_CAPS("small_caps", "Small Caps"),
    UPSIDE_DOWN("upside_down", "Upside-down"),
    CIRCLED("circled", "Circled"),
    NEGATIVE_CIRCLED("negative_circled", "Neg Circled"),
    SQUARED("squared", "Squared"),
    NEGATIVE_SQUARED("negative_squared", "Neg Squared"),
    FULLWIDTH("fullwidth", "Fullwidth"),
    WIDE_SPACED("wide_spaced", "Wide-spaced"),
    STRIKETHROUGH("strikethrough", "Strikethrough"),
    UNDERLINE("underline", "Underline");

    companion object {
        fun fromId(id: String?): StylishFontStyle =
            entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

object StylishFontEngine {

    private const val SINHALA_START = '\u0D80'
    private const val SINHALA_END = '\u0DFF'

    private val SMALL_CAPS_LOWER = arrayOf(
        "ᴀ", "ʙ", "ᴄ", "ᴅ", "ᴇ", "ꜰ", "ɢ", "ʜ", "ɪ", "ᴊ", "ᴋ", "ʟ", "ᴍ",
        "ɴ", "ᴏ", "ᴘ", "ǫ", "ʀ", "ꜱ", "ᴛ", "ᴜ", "ᴠ", "ᴡ", "x", "ʏ", "ᴢ"
    )

    private val UPSIDE_DOWN_UPPER = arrayOf(
        "∀", "𐐒", "Ɔ", "ᗡ", "Ǝ", "Ⅎ", "פ", "H", "I", "ſ", "ʞ", "˥", "W",
        "N", "O", "Ԁ", "Ό", "ᴚ", "S", "┴", "∩", "Λ", "M", "X", "⅄", "Z"
    )

    private val UPSIDE_DOWN_LOWER = arrayOf(
        "ɐ", "q", "ɔ", "p", "ǝ", "ɟ", "ƃ", "ɥ", "ᴉ", "ɾ", "ʞ", "l", "ɯ",
        "u", "o", "d", "b", "ɹ", "s", "ʇ", "n", "ʌ", "ʍ", "x", "ʎ", "z"
    )

    private val UPSIDE_DOWN_DIGITS = arrayOf(
        "0", "Ɩ", "ᄅ", "Ɛ", "ㄣ", "ϛ", "9", "ㄥ", "8", "6"
    )

    private val CIRCLED_DIGITS = arrayOf("⓪", "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨")
    private val NEG_CIRCLED_DIGITS = arrayOf("⓿", "❶", "❷", "❸", "❹", "❺", "❻", "❼", "❽", "❾")

    // Exceptions in Unicode Mathematical Alphanumeric Symbols block (holes in contiguous ranges)
    private val ITALIC_LOWER_EXCEPTIONS = mapOf('h' to "\u210E") // ℎ
    private val SCRIPT_UPPER_EXCEPTIONS = mapOf(
        'B' to "\u212C", 'E' to "\u2130", 'F' to "\u2131", 'H' to "\u210B",
        'I' to "\u2110", 'L' to "\u2112", 'M' to "\u2133", 'R' to "\u211B"
    )
    private val SCRIPT_LOWER_EXCEPTIONS = mapOf(
        'e' to "\u212F", 'g' to "\u210A", 'o' to "\u2134"
    )
    private val FRAKTUR_UPPER_EXCEPTIONS = mapOf(
        'C' to "\u212D", 'H' to "\u210C", 'I' to "\u2111", 'R' to "\u211C", 'Z' to "\u2128"
    )
    private val DOUBLE_STRUCK_UPPER_EXCEPTIONS = mapOf(
        'C' to "\u2102", 'H' to "\u210D", 'N' to "\u2115", 'P' to "\u2119",
        'Q' to "\u211A", 'R' to "\u211D", 'Z' to "\u2124"
    )

    fun isSinhalaChar(ch: Char): Boolean = ch in SINHALA_START..SINHALA_END

    fun previewLabel(style: StylishFontStyle): String = when (style) {
        StylishFontStyle.NORMAL -> "Normal"
        StylishFontStyle.UPSIDE_DOWN -> "∀ɐ"
        StylishFontStyle.WIDE_SPACED -> "A a"
        else -> transform("Aa", style)
    }

    /**
     * Converts [text] into the given [style].
     * Sinhala Unicode characters are always preserved completely untouched.
     */
    fun transform(text: String, style: StylishFontStyle): String {
        if (style == StylishFontStyle.NORMAL || text.isEmpty()) return text

        if (style == StylishFontStyle.UPSIDE_DOWN) {
            // Only flip pure Latin/ASCII runs; leave Sinhala untouched
            val containsSinhala = text.any { isSinhalaChar(it) }
            val sb = StringBuilder(text.length * 2)
            for (ch in text) {
                sb.append(transformChar(ch, style))
            }
            return if (containsSinhala) sb.toString() else sb.toString()
        }

        val sb = StringBuilder(text.length * 2)
        for (i in text.indices) {
            val ch = text[i]
            if (isSinhalaChar(ch)) {
                sb.append(ch)
                continue
            }
            when (style) {
                StylishFontStyle.WIDE_SPACED -> {
                    sb.append(ch)
                    if (ch.isLetterOrDigit() && i < text.lastIndex) {
                        sb.append(' ')
                    }
                }
                StylishFontStyle.STRIKETHROUGH -> {
                    sb.append(ch)
                    if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9') {
                        sb.append('\u0336')
                    }
                }
                StylishFontStyle.UNDERLINE -> {
                    sb.append(ch)
                    if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9') {
                        sb.append('\u0332')
                    }
                }
                else -> sb.append(transformChar(ch, style))
            }
        }
        return sb.toString()
    }

    private fun transformChar(ch: Char, style: StylishFontStyle): String {
        if (isSinhalaChar(ch)) return ch.toString()
        val isUpper = ch in 'A'..'Z'
        val isLower = ch in 'a'..'z'
        val isDigit = ch in '0'..'9'
        if (!isUpper && !isLower && !isDigit) return ch.toString()

        val uIdx = ch - 'A'
        val lIdx = ch - 'a'
        val dIdx = ch - '0'

        return when (style) {
            StylishFontStyle.NORMAL -> ch.toString()
            StylishFontStyle.BOLD, StylishFontStyle.SERIF_BOLD -> when {
                isUpper -> cp(0x1D400 + uIdx)
                isLower -> cp(0x1D41A + lIdx)
                else -> cp(0x1D7CE + dIdx)
            }
            StylishFontStyle.ITALIC -> when {
                isUpper -> cp(0x1D434 + uIdx)
                isLower -> ITALIC_LOWER_EXCEPTIONS[ch] ?: cp(0x1D44E + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.BOLD_ITALIC -> when {
                isUpper -> cp(0x1D468 + uIdx)
                isLower -> cp(0x1D482 + lIdx)
                else -> cp(0x1D7CE + dIdx)
            }
            StylishFontStyle.SANS_BOLD -> when {
                isUpper -> cp(0x1D5D4 + uIdx)
                isLower -> cp(0x1D5EE + lIdx)
                else -> cp(0x1D7EC + dIdx)
            }
            StylishFontStyle.SANS_ITALIC -> when {
                isUpper -> cp(0x1D608 + uIdx)
                isLower -> cp(0x1D622 + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.SANS_BOLD_ITALIC -> when {
                isUpper -> cp(0x1D63C + uIdx)
                isLower -> cp(0x1D656 + lIdx)
                else -> cp(0x1D7EC + dIdx)
            }
            StylishFontStyle.SCRIPT -> when {
                isUpper -> SCRIPT_UPPER_EXCEPTIONS[ch] ?: cp(0x1D49C + uIdx)
                isLower -> SCRIPT_LOWER_EXCEPTIONS[ch] ?: cp(0x1D4B6 + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.BOLD_SCRIPT -> when {
                isUpper -> cp(0x1D4D0 + uIdx)
                isLower -> cp(0x1D4EA + lIdx)
                else -> cp(0x1D7CE + dIdx)
            }
            StylishFontStyle.FRAKTUR -> when {
                isUpper -> FRAKTUR_UPPER_EXCEPTIONS[ch] ?: cp(0x1D504 + uIdx)
                isLower -> cp(0x1D51E + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.BOLD_FRAKTUR -> when {
                isUpper -> cp(0x1D56C + uIdx)
                isLower -> cp(0x1D586 + lIdx)
                else -> cp(0x1D7CE + dIdx)
            }
            StylishFontStyle.DOUBLE_STRUCK -> when {
                isUpper -> DOUBLE_STRUCK_UPPER_EXCEPTIONS[ch] ?: cp(0x1D538 + uIdx)
                isLower -> cp(0x1D552 + lIdx)
                else -> cp(0x1D7D8 + dIdx)
            }
            StylishFontStyle.MONOSPACE -> when {
                isUpper -> cp(0x1D670 + uIdx)
                isLower -> cp(0x1D68A + lIdx)
                else -> cp(0x1D7F6 + dIdx)
            }
            StylishFontStyle.SMALL_CAPS -> when {
                isUpper -> ch.toString()
                isLower -> SMALL_CAPS_LOWER[lIdx]
                else -> ch.toString()
            }
            StylishFontStyle.UPSIDE_DOWN -> when {
                isUpper -> UPSIDE_DOWN_UPPER[uIdx]
                isLower -> UPSIDE_DOWN_LOWER[lIdx]
                else -> UPSIDE_DOWN_DIGITS[dIdx]
            }
            StylishFontStyle.CIRCLED -> when {
                isUpper -> cp(0x24B6 + uIdx)
                isLower -> cp(0x24D0 + lIdx)
                else -> CIRCLED_DIGITS[dIdx]
            }
            StylishFontStyle.NEGATIVE_CIRCLED -> when {
                isUpper -> cp(0x1F150 + uIdx)
                isLower -> cp(0x1F150 + lIdx)
                else -> NEG_CIRCLED_DIGITS[dIdx]
            }
            StylishFontStyle.SQUARED -> when {
                isUpper -> cp(0x1F130 + uIdx)
                isLower -> cp(0x1F130 + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.NEGATIVE_SQUARED -> when {
                isUpper -> cp(0x1F170 + uIdx)
                isLower -> cp(0x1F170 + lIdx)
                else -> ch.toString()
            }
            StylishFontStyle.FULLWIDTH -> when {
                isUpper -> cp(0xFF21 + uIdx)
                isLower -> cp(0xFF41 + lIdx)
                else -> cp(0xFF10 + dIdx)
            }
            StylishFontStyle.WIDE_SPACED,
            StylishFontStyle.STRIKETHROUGH,
            StylishFontStyle.UNDERLINE -> ch.toString()
        }
    }

    private fun cp(codePoint: Int): String = String(Character.toChars(codePoint))
}
